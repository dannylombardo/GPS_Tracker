package com.dannylombardo.gpstracker.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.dannylombardo.gpstracker.GpsTrackerApp
import com.dannylombardo.gpstracker.R
import com.dannylombardo.gpstracker.data.RoutePoint
import com.dannylombardo.gpstracker.data.CarRepository
import com.dannylombardo.gpstracker.data.TripRepository
import com.dannylombardo.gpstracker.garage.GarageIntents
import com.dannylombardo.gpstracker.garage.GarageReminders
import com.dannylombardo.gpstracker.ui.MainActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Records one drive at a time. It only runs (and GPS is only on) between a
 * "might be in a vehicle" event and the end of that drive.
 *
 * A possible drive (motion sensors or car Bluetooth) first has to be confirmed by GPS
 * showing car-like movement, see [DriveConfirmation]. Until then nothing is saved; if
 * it isn't confirmed within the trigger's time limit, or we're seen leaving the vehicle,
 * GPS goes straight back off. Drives started by hand skip the check.
 *
 * A drive ends when:
 * - Android reports we left the vehicle, and we haven't got back in within [EXIT_GRACE_MS]
 *   (so a long red light or a drive-through doesn't split a trip), or
 * - the car hasn't moved for [STATIONARY_TIMEOUT_MS], in case the exit event never comes, or
 * - the drive is ended by hand.
 *
 * Once a drive ends, its stops are checked against OpenStreetMap's gas stations so
 * a fill-up can be logged.
 */
class DriveTrackingService : LifecycleService() {

    private lateinit var repository: TripRepository
    private lateinit var locationClient: FusedLocationProviderClient

    private var tripId: Long? = null
    private var tracker: TripDistanceTracker? = null
    private var startJob: Job? = null
    private var probe: DriveConfirmation? = null
    private var probeStartedAt = 0L
    private var probeDeadline = 0L
    private var probeJob: Job? = null
    private var restartTrigger = DriveTrigger.ACTIVITY
    private var exitJob: Job? = null
    private var watchdogJob: Job? = null
    private var ending = false
    private var restartAfterEnding = false
    private var notifiedDistanceTenths = -1L
    private var notifiedSpeedKmh = -1
    private var notifiedAt = 0L
    private var tripStartTime = 0L
    private var recentSpeeds: List<Pair<Long, Float>> = emptyList()

    // Serialises database writes so a trip is never finished or deleted while a point is being added.
    private val writeLock = Mutex()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = TripRepository.get(this)
        locationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        DriveState.isServiceRunning = true

        // A null intent means Android restarted us after killing the process mid-drive.
        when (intent?.action) {
            // Resumes the interrupted drive if there was one, otherwise checks with GPS as usual.
            null -> onVehicleEntered(DriveTrigger.ACTIVITY)
            ACTION_START -> onVehicleEntered(DriveTrigger.fromName(intent.getStringExtra(EXTRA_TRIGGER)))
            ACTION_VEHICLE_EXIT -> {
                if (probe != null && tripId == null && startJob == null) {
                    // Left the "vehicle" before GPS saw any driving: a false start.
                    falseStart()
                } else if (exitJob == null) {
                    exitJob = lifecycleScope.launch {
                        delay(EXIT_GRACE_MS)
                        requestEnd()
                    }
                }
            }
            ACTION_STOP -> if (probe != null && tripId == null && startJob == null) falseStart() else requestEnd()
        }
        return START_STICKY
    }

    private fun onVehicleEntered(trigger: DriveTrigger) {
        exitJob?.cancel()
        exitJob = null
        when {
            ending -> {
                restartAfterEnding = true
                restartTrigger = trigger
            }
            tripId != null || startJob != null -> Unit
            probe != null -> {
                if (trigger == DriveTrigger.MANUAL) {
                    confirmDrive()
                } else {
                    probeDeadline = maxOf(probeDeadline, System.currentTimeMillis() + trigger.confirmWithinMillis)
                }
            }
            else -> startJob = lifecycleScope.launch { startDetection(trigger) }
        }
    }

    /**
     * Turns GPS on. A drive started by hand, or one Android interrupted mid-way, is
     * recorded straight away; anything else waits for GPS to confirm we're driving.
     */
    private suspend fun startDetection(trigger: DriveTrigger) {
        val now = System.currentTimeMillis()
        val resuming = trigger == DriveTrigger.MANUAL ||
            writeLock.withLock { repository.findResumableTrip(now, RESUME_WINDOW_MS) } != null
        if (resuming) {
            beginTrip(now)
            startJob = null
            if (!startLocationUpdates()) {
                requestEnd()
                return
            }
            startWatchdog()
            updateNotification(force = true)
            return
        }

        probe = DriveConfirmation()
        probeStartedAt = now
        probeDeadline = now + trigger.confirmWithinMillis
        startJob = null
        updateNotification(force = true)
        if (!startLocationUpdates()) {
            requestEnd()
            return
        }
        probeJob = lifecycleScope.launch {
            while (isActive) {
                delay(PROBE_CHECK_INTERVAL_MS)
                if (probe != null && tripId == null && startJob == null &&
                    System.currentTimeMillis() >= probeDeadline
                ) {
                    falseStart()
                    break
                }
            }
        }
    }

    /** GPS never saw us driving: forget it, without saving anything, and turn GPS off. */
    private fun falseStart() {
        TrackingPrefs.setFalseStartAt(this, System.currentTimeMillis())
        requestEnd()
    }

    /** GPS confirmed the drive: save it, including the stretch driven while we were checking. */
    private fun confirmDrive() {
        val confirmation = probe ?: return
        if (startJob != null || tripId != null) return
        probeJob?.cancel()
        probeJob = null
        startJob = lifecycleScope.launch {
            beginTrip(confirmation.startTimeMillis(probeStartedAt))
            // Fixes that arrive while the trip is being created are still collected by
            // the confirmation, so take them all in one go before switching over.
            val fixes = confirmation.routeFixes()
            probe = null
            startJob = null
            fixes.forEach(::recordFix)
            startWatchdog()
            updateNotification(force = true)
        }
    }

    private suspend fun beginTrip(startTime: Long) {
        val now = System.currentTimeMillis()
        val newTracker = TripDistanceTracker(startTimeMillis = startTime)
        val id = writeLock.withLock {
            val resumable = repository.findResumableTrip(now, RESUME_WINDOW_MS)
            repository.closeAbandonedTrips(exceptTripId = resumable?.id, minDistanceMeters = MIN_TRIP_METERS)
            if (resumable != null) {
                newTracker.restore(
                    resumable.distanceMeters,
                    repository.lastPoint(resumable.id)?.toFix(),
                    resumable.topSpeedMetersPerSecond ?: 0.0,
                )
                resumable.id
            } else {
                repository.startTrip(startTime, CarRepository.get(this@DriveTrackingService).activeCarId())
            }
        }
        tracker = newTracker
        tripId = id
        tripStartTime = repository.trip(id)?.startTime ?: startTime
        recentSpeeds = emptyList()
        DriveState.setActiveTrip(id)
        publishLive(null, null)
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = lifecycleScope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                if (tracker?.isStationary(System.currentTimeMillis(), STATIONARY_TIMEOUT_MS) == true) {
                    requestEnd()
                    break
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(): Boolean {
        if (!Permissions.hasLocation(this)) return false
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_MIN_INTERVAL_MS)
            .setMinUpdateDistanceMeters(LOCATION_MIN_DISTANCE_M)
            .build()
        return try {
            locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission missing", e)
            false
        }
    }

    private fun onLocation(location: Location) {
        if (ending) return
        val fix = Fix(
            latitude = location.latitude,
            longitude = location.longitude,
            timeMillis = location.time,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
        )
        val confirmation = probe
        if (confirmation != null) {
            if (confirmation.add(fix)) confirmDrive()
            return
        }
        recordFix(fix)
    }

    private fun recordFix(fix: Fix) {
        val id = tripId ?: return
        val currentTracker = tracker ?: return
        if (!currentTracker.add(fix)) return

        val distance = currentTracker.distanceMeters
        val topSpeed = currentTracker.topSpeedMetersPerSecond
        val point = RoutePoint(
            tripId = id,
            time = fix.timeMillis,
            latitude = fix.latitude,
            longitude = fix.longitude,
            accuracyMeters = fix.accuracyMeters,
            speedMetersPerSecond = fix.speedMetersPerSecond,
        )
        lifecycleScope.launch {
            writeLock.withLock {
                try {
                    repository.addPoint(point, distance, topSpeed)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not save route point", e)
                }
            }
        }
        recentSpeeds = LiveDrive.trimmed(
            recentSpeeds + listOfNotNull(fix.speedMetersPerSecond?.let { fix.timeMillis to it }),
            fix.timeMillis,
        )
        publishLive(fix.speedMetersPerSecond?.toDouble(), fix.timeMillis)
        updateNotification(force = false)
    }

    /** Shares the drive's numbers with the live screen. */
    private fun publishLive(speed: Double?, fixTime: Long?) {
        val id = tripId ?: return
        val t = tracker ?: return
        DriveState.setLive(
            LiveDrive(
                tripId = id,
                startTime = tripStartTime,
                distanceMeters = t.distanceMeters,
                speedMetersPerSecond = speed,
                topSpeedMetersPerSecond = t.topSpeedMetersPerSecond,
                lastFixTime = fixTime,
                recentSpeeds = recentSpeeds,
            ),
        )
    }

    private fun requestEnd() {
        lifecycleScope.launch { endTrip() }
    }

    private suspend fun endTrip() {
        if (ending) return
        ending = true
        startJob?.join()
        locationClient.removeLocationUpdates(locationCallback)
        probeJob?.cancel()
        probeJob = null
        probe = null
        exitJob?.cancel()
        exitJob = null
        watchdogJob?.cancel()
        watchdogJob = null

        val id = tripId
        val distance = tracker?.distanceMeters ?: 0.0
        val topSpeed = tracker?.topSpeedMetersPerSecond?.takeIf { it > 0 }
        if (id != null) {
            val (kept, route) = writeLock.withLock {
                val route = repository.routePoints(id).map { it.toFix() }
                val endTime = route.lastOrNull()?.timeMillis ?: System.currentTimeMillis()
                repository.finishTrip(id, endTime, distance, topSpeed, MIN_TRIP_METERS) to route
            }
            if (kept) DriverCheck.ask(this, id, distance)
            DriveState.setLive(null)
            // Even a drive too short to keep may have been a hop to the gas station.
            checkForGasStation(if (kept) id else null, route)
            if (kept) {
                // Score the drive, update the widget, and check for maintenance, fuel and budget nudges.
                val carId = writeLock.withLock { repository.trip(id)?.carId }
                withTimeoutOrNull(GARAGE_TIMEOUT_MS) {
                    runCatching { GarageReminders.afterDrive(this@DriveTrackingService, carId) }
                        .onFailure { Log.w(TAG, "Garage check failed", it) }
                }
            }
        }
        tripId = null
        tracker = null
        notifiedDistanceTenths = -1L
        notifiedSpeedKmh = -1
        recentSpeeds = emptyList()
        DriveState.setActiveTrip(null)
        DriveState.setLive(null)
        ending = false

        if (restartAfterEnding) {
            restartAfterEnding = false
            val trigger = restartTrigger
            startJob = lifecycleScope.launch { startDetection(trigger) }
            return
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        DriveState.isServiceRunning = false
        stopSelf()
    }

    /**
     * Asks OpenStreetMap whether any stop on the drive was at a gas station, and if so
     * offers to log the fill-up. One small request per drive, made while we're still
     * in the foreground; a failed or slow lookup just means no prompt.
     */
    private suspend fun checkForGasStation(tripId: Long?, route: List<Fix>) {
        if (!TrackingPrefs.isStationSpottingEnabled(this)) return
        val stops = StopFinder.find(route)
        if (stops.isEmpty()) return
        val stations = withTimeoutOrNull(STATION_LOOKUP_TIMEOUT_MS) {
            withContext(Dispatchers.IO) { GasStations.fetchNear(stops) }
        } ?: return
        val visit = GasStations.match(stops, stations) ?: return
        FuelPrompt.show(this, tripId ?: -1, visit)
    }

    override fun onDestroy() {
        locationClient.removeLocationUpdates(locationCallback)
        DriveState.isServiceRunning = false
        DriveState.setActiveTrip(null)
        DriveState.setLive(null)
        super.onDestroy()
    }

    private fun enterForeground(): Boolean =
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(tracker?.distanceMeters),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            true
        } catch (e: Exception) {
            // Typically missing "Allow all the time" location access, which Android
            // requires before it lets a background event start location tracking.
            Log.w(TAG, "Could not start foreground tracking", e)
            false
        }

    @SuppressLint("MissingPermission")
    private fun updateNotification(force: Boolean) {
        val distance = tracker?.distanceMeters
        val tenths = distance?.let { (it / 100).toLong() } ?: -1L
        val speedKmh = DriveState.live.value?.speedMetersPerSecond?.let { (it * 3.6).toInt() } ?: -1
        val now = System.currentTimeMillis()
        val speedChanged = kotlin.math.abs(speedKmh - notifiedSpeedKmh) >= 3 && now - notifiedAt >= 3_000
        if (!force && tenths == notifiedDistanceTenths && !speedChanged) return
        notifiedDistanceTenths = tenths
        notifiedSpeedKmh = speedKmh
        notifiedAt = now
        if (Permissions.hasNotifications(this)) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(distance))
        }
    }

    /** [distanceMeters] is null while GPS is still checking whether we're driving. */
    private fun buildNotification(distanceMeters: Double?): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            if (distanceMeters == null) Intent(this, MainActivity::class.java) else GarageIntents.openLive(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val endDrive = PendingIntent.getForegroundService(
            this,
            1,
            Intent(this, DriveTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, GpsTrackerApp.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_car)
            .apply {
                if (distanceMeters == null) {
                    setContentTitle("Checking for a drive")
                    setContentText("GPS turns off again if you're not driving")
                    addAction(0, "Not driving", endDrive)
                } else {
                    val live = DriveState.live.value
                    val speed = live?.speedMetersPerSecond?.let { (it * 3.6).toInt() }
                    val top = live?.topSpeedMetersPerSecond?.takeIf { it > 0 }?.let { (it * 3.6).toInt() }
                    val avg = live?.averageSpeedMetersPerSecond?.let { (it * 3.6).toInt() }
                    setContentTitle(if (speed != null) "Recording drive · $speed km/h" else "Recording drive")
                    val km = String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1000)
                    setContentText(listOfNotNull(km, avg?.let { "avg $it km/h" }, top?.let { "top $it km/h" }).joinToString(" · "))
                    setSubText("Tap for the live dash")
                    if (tripStartTime > 0) {
                        setWhen(tripStartTime)
                        setShowWhen(true)
                        setUsesChronometer(true)
                    }
                    addAction(0, "End drive", endDrive)
                }
            }
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "DriveTrackingService"
        private const val NOTIFICATION_ID = 1

        private const val ACTION_START = "com.dannylombardo.gpstracker.action.START_DRIVE"
        private const val ACTION_VEHICLE_EXIT = "com.dannylombardo.gpstracker.action.VEHICLE_EXIT"
        private const val ACTION_STOP = "com.dannylombardo.gpstracker.action.STOP_DRIVE"
        private const val EXTRA_TRIGGER = "trigger"

        private const val LOCATION_INTERVAL_MS = 5_000L
        private const val LOCATION_MIN_INTERVAL_MS = 2_000L
        private const val LOCATION_MIN_DISTANCE_M = 10f

        const val MIN_TRIP_METERS = 500.0
        private const val EXIT_GRACE_MS = 3 * 60_000L
        private const val STATIONARY_TIMEOUT_MS = 10 * 60_000L
        private const val RESUME_WINDOW_MS = 10 * 60_000L
        private const val WATCHDOG_INTERVAL_MS = 30_000L
        private const val PROBE_CHECK_INTERVAL_MS = 15_000L
        private const val STATION_LOOKUP_TIMEOUT_MS = 40_000L
        private const val GARAGE_TIMEOUT_MS = 30_000L

        fun vehicleEntered(context: Context, trigger: DriveTrigger) = send(context, ACTION_START, trigger)

        fun vehicleExited(context: Context) = send(context, ACTION_VEHICLE_EXIT)

        fun startManually(context: Context) = send(context, ACTION_START, DriveTrigger.MANUAL)

        fun stopManually(context: Context) = send(context, ACTION_STOP)

        private fun send(context: Context, action: String, trigger: DriveTrigger? = null) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DriveTrackingService::class.java)
                        .setAction(action)
                        .apply { trigger?.let { putExtra(EXTRA_TRIGGER, it.name) } },
                )
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Android refused to start drive tracking", e)
            }
        }
    }
}

private fun RoutePoint.toFix() = Fix(latitude, longitude, time, accuracyMeters, speedMetersPerSecond)
