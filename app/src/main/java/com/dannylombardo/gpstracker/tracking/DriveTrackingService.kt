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
import com.dannylombardo.gpstracker.data.TripRepository
import com.dannylombardo.gpstracker.ui.MainActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * Records one drive at a time. It only runs (and GPS is only on) between a
 * "got into a vehicle" event and the end of that drive.
 *
 * A drive ends when:
 * - Android reports we left the vehicle, and we haven't got back in within [EXIT_GRACE_MS]
 *   (so a long red light or a drive-through doesn't split a trip), or
 * - the car hasn't moved for [STATIONARY_TIMEOUT_MS], in case the exit event never comes, or
 * - the drive is ended by hand.
 */
class DriveTrackingService : LifecycleService() {

    private lateinit var repository: TripRepository
    private lateinit var locationClient: FusedLocationProviderClient

    private var tripId: Long? = null
    private var tracker: TripDistanceTracker? = null
    private var startJob: Job? = null
    private var exitJob: Job? = null
    private var watchdogJob: Job? = null
    private var ending = false
    private var restartAfterEnding = false
    private var notifiedDistanceTenths = -1L

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
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> onVehicleEntered()
            ACTION_VEHICLE_EXIT -> {
                if (exitJob == null) {
                    exitJob = lifecycleScope.launch {
                        delay(EXIT_GRACE_MS)
                        requestEnd()
                    }
                }
            }
            ACTION_STOP -> requestEnd()
        }
        return START_STICKY
    }

    private fun onVehicleEntered() {
        exitJob?.cancel()
        exitJob = null
        when {
            ending -> restartAfterEnding = true
            tripId == null && startJob == null -> startJob = lifecycleScope.launch { beginTrip() }
        }
    }

    private suspend fun beginTrip() {
        val now = System.currentTimeMillis()
        val newTracker = TripDistanceTracker(startTimeMillis = now)
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
                repository.startTrip(now)
            }
        }
        tracker = newTracker
        tripId = id
        DriveState.setActiveTrip(id)
        startJob = null

        if (!startLocationUpdates()) {
            requestEnd()
            return
        }
        watchdogJob = lifecycleScope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                if (tracker?.isStationary(System.currentTimeMillis(), STATIONARY_TIMEOUT_MS) == true) {
                    requestEnd()
                    break
                }
            }
        }
        updateNotification(force = true)
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
        val id = tripId ?: return
        val currentTracker = tracker ?: return
        val fix = Fix(
            latitude = location.latitude,
            longitude = location.longitude,
            timeMillis = location.time,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
        )
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
        updateNotification(force = false)
    }

    private fun requestEnd() {
        lifecycleScope.launch { endTrip() }
    }

    private suspend fun endTrip() {
        if (ending) return
        ending = true
        startJob?.join()
        locationClient.removeLocationUpdates(locationCallback)
        exitJob?.cancel()
        exitJob = null
        watchdogJob?.cancel()
        watchdogJob = null

        val id = tripId
        val distance = tracker?.distanceMeters ?: 0.0
        val topSpeed = tracker?.topSpeedMetersPerSecond?.takeIf { it > 0 }
        if (id != null) {
            val kept = writeLock.withLock {
                val endTime = repository.lastPoint(id)?.time ?: System.currentTimeMillis()
                repository.finishTrip(id, endTime, distance, topSpeed, MIN_TRIP_METERS)
            }
            if (kept) DriverCheck.ask(this, id, distance)
        }
        tripId = null
        tracker = null
        notifiedDistanceTenths = -1L
        DriveState.setActiveTrip(null)
        ending = false

        if (restartAfterEnding) {
            restartAfterEnding = false
            startJob = lifecycleScope.launch { beginTrip() }
            return
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        DriveState.isServiceRunning = false
        stopSelf()
    }

    override fun onDestroy() {
        locationClient.removeLocationUpdates(locationCallback)
        DriveState.isServiceRunning = false
        DriveState.setActiveTrip(null)
        super.onDestroy()
    }

    private fun enterForeground(): Boolean =
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(tracker?.distanceMeters ?: 0.0),
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
        val distance = tracker?.distanceMeters ?: return
        val tenths = (distance / 100).toLong()
        if (!force && tenths == notifiedDistanceTenths) return
        notifiedDistanceTenths = tenths
        if (Permissions.hasNotifications(this)) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(distance))
        }
    }

    private fun buildNotification(distanceMeters: Double): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val endDrive = PendingIntent.getForegroundService(
            this,
            1,
            Intent(this, DriveTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, GpsTrackerApp.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle("Recording drive")
            .setContentText(String.format(Locale.getDefault(), "%.1f km so far", distanceMeters / 1000))
            .setContentIntent(openApp)
            .addAction(0, "End drive", endDrive)
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

        private const val LOCATION_INTERVAL_MS = 5_000L
        private const val LOCATION_MIN_INTERVAL_MS = 2_000L
        private const val LOCATION_MIN_DISTANCE_M = 10f

        const val MIN_TRIP_METERS = 500.0
        private const val EXIT_GRACE_MS = 3 * 60_000L
        private const val STATIONARY_TIMEOUT_MS = 10 * 60_000L
        private const val RESUME_WINDOW_MS = 10 * 60_000L
        private const val WATCHDOG_INTERVAL_MS = 30_000L

        fun vehicleEntered(context: Context) = send(context, ACTION_START)

        fun vehicleExited(context: Context) = send(context, ACTION_VEHICLE_EXIT)

        fun startManually(context: Context) = send(context, ACTION_START)

        fun stopManually(context: Context) = send(context, ACTION_STOP)

        private fun send(context: Context, action: String) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DriveTrackingService::class.java).setAction(action),
                )
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Android refused to start drive tracking", e)
            }
        }
    }
}

private fun RoutePoint.toFix() = Fix(latitude, longitude, time, accuracyMeters, speedMetersPerSecond)
