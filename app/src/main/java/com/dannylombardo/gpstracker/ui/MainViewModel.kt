package com.dannylombardo.gpstracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dannylombardo.gpstracker.data.Car
import com.dannylombardo.gpstracker.data.CarRepository
import com.dannylombardo.gpstracker.data.CarStats
import com.dannylombardo.gpstracker.data.FuelEconomy
import com.dannylombardo.gpstracker.data.FuelUp
import com.dannylombardo.gpstracker.data.FuelUpRepository
import com.dannylombardo.gpstracker.data.RoutePoint
import com.dannylombardo.gpstracker.data.RouteProfile
import com.dannylombardo.gpstracker.data.Trip
import com.dannylombardo.gpstracker.data.TripRepository
import com.dannylombardo.gpstracker.data.WeeklySummary
import com.dannylombardo.gpstracker.tracking.DriveDetection
import com.dannylombardo.gpstracker.tracking.DriveState
import com.dannylombardo.gpstracker.tracking.DriveTrackingService
import com.dannylombardo.gpstracker.tracking.DriverCheck
import com.dannylombardo.gpstracker.tracking.Permissions
import com.dannylombardo.gpstracker.tracking.TrackingPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

data class PermissionState(
    val location: Boolean = false,
    val backgroundLocation: Boolean = false,
    val activityRecognition: Boolean = false,
    val notifications: Boolean = false,
) {
    val canAutoTrack get() = location && backgroundLocation && activityRecognition
}

/** One car's all-time numbers for the cars page. */
data class CarOverview(
    val car: Car,
    val totals: CarStats.Totals,
    val litresPer100Km: Double?,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val repository = TripRepository.get(application)

    private val fuelRepository = FuelUpRepository.get(application)

    private val carRepository = CarRepository.get(application)

    private val _weekStart = MutableStateFlow(currentWeekStart())

    /** Every car, oldest first. */
    val cars: StateFlow<List<Car>> = carRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeCarPick = MutableStateFlow(TrackingPrefs.activeCarId(application))

    /** The car new drives go to: the one picked, or the first car if that one is gone. */
    val activeCar: StateFlow<Car?> = combine(cars, _activeCarPick) { cars, picked ->
        cars.firstOrNull { it.id == picked } ?: cars.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _viewedCarPick = MutableStateFlow(TrackingPrefs.viewedCarId(application))

    /**
     * The car the screens show, or null for all cars together. With only one car,
     * or if the picked car was deleted, that's all cars.
     */
    val viewedCarId: StateFlow<Long?> = combine(cars, _viewedCarPick) { cars, picked ->
        picked?.takeIf { id -> cars.size > 1 && cars.any { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val finishedTrips = repository.observeFinishedTrips()

    private val everyFuelUp = fuelRepository.observeAll()

    val week: StateFlow<WeeklySummary> = combine(
        finishedTrips,
        everyFuelUp,
        _weekStart,
        viewedCarId,
    ) { trips, fuelUps, start, carId ->
        WeeklySummary.of(
            CarStats.tripsFor(trips, carId),
            start,
            ZoneId.systemDefault(),
            CarStats.fuelUpsFor(fuelUps, carId),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WeeklySummary.of(emptyList(), _weekStart.value, ZoneId.systemDefault()),
    )

    /** How the shown week's kilometres split between cars, for the all-cars view. */
    val weekByCar: StateFlow<List<Pair<Car, Double>>> = combine(finishedTrips, cars, _weekStart) { trips, cars, start ->
        CarStats.distanceByCar(cars, WeeklySummary.of(trips, start, ZoneId.systemDefault()).trips)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Each car's fuel economy, from only its own fill-ups and drives. */
    val economyByCar: StateFlow<Map<Long, FuelEconomy.Summary>> =
        combine(cars, everyFuelUp, finishedTrips) { cars, fuelUps, trips ->
            CarStats.economyByCar(cars, fuelUps, trips)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The L/100km of the stretch ending at each fill-up, by fill-up id, across all cars. */
    val consumptionByFuelUp: StateFlow<Map<Long, Double>> = economyByCar
        .map { byCar -> byCar.values.flatMap { it.byFuelUp.entries }.associate { it.key to it.value } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The shown car's fill-ups, or everyone's for all cars, newest first. */
    val fuelUps: StateFlow<List<FuelUp>> = combine(everyFuelUp, viewedCarId) { fuelUps, carId ->
        CarStats.fuelUpsFor(fuelUps, carId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All-time numbers per car, plus every car together (first, with a null car). */
    val carOverviews: StateFlow<Pair<CarStats.Totals, List<CarOverview>>?> =
        combine(cars, everyFuelUp, finishedTrips, economyByCar) { cars, fuelUps, trips, economy ->
            CarStats.totals(trips, fuelUps) to cars.map { car ->
                CarOverview(
                    car = car,
                    totals = CarStats.totals(CarStats.tripsFor(trips, car.id), CarStats.fuelUpsFor(fuelUps, car.id)),
                    litresPer100Km = economy[car.id]?.averageLitresPer100Km,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether the cars page is open. */
    private val _carsOpen = MutableStateFlow(false)
    val carsOpen: StateFlow<Boolean> = _carsOpen.asStateFlow()

    /** The drive whose page is open, if any. */
    private val _openTripId = MutableStateFlow<Long?>(null)
    val openTripId: StateFlow<Long?> = _openTripId.asStateFlow()

    /** Route outlines for the drive list, kept so scrolling back doesn't reload them. */
    private val routePreviews = ConcurrentHashMap<Long, List<LatLon>>()

    /** The fill-up being added or edited in the fill-up form, if it's open. */
    private val _fuelDraft = MutableStateFlow<FuelUp?>(null)
    val fuelDraft: StateFlow<FuelUp?> = _fuelDraft.asStateFlow()

    private val _stationSpotting = MutableStateFlow(TrackingPrefs.isStationSpottingEnabled(application))
    val stationSpotting: StateFlow<Boolean> = _stationSpotting.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeTrip: StateFlow<Trip?> = DriveState.activeTripId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeTrip(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _autoTrack = MutableStateFlow(TrackingPrefs.isAutoTrackEnabled(application))
    val autoTrack: StateFlow<Boolean> = _autoTrack.asStateFlow()

    init {
        // Tidy up trips left open if the app was killed mid-drive while not tracking now.
        if (!DriveState.isServiceRunning) {
            viewModelScope.launch {
                repository.closeAbandonedTrips(
                    exceptTripId = null,
                    minDistanceMeters = DriveTrackingService.MIN_TRIP_METERS,
                )
            }
        }
    }

    /** Called whenever the screen resumes, since permissions can change in system settings. */
    fun refresh() {
        val state = PermissionState(
            location = Permissions.hasLocation(app),
            backgroundLocation = Permissions.hasBackgroundLocation(app),
            activityRecognition = Permissions.hasActivityRecognition(app),
            notifications = Permissions.hasNotifications(app),
        )
        _permissions.value = state
        if (_autoTrack.value && state.canAutoTrack) {
            // Cheap and idempotent; makes sure detection is registered after reinstalls etc.
            viewModelScope.launch { DriveDetection.start(app) }
        }
    }

    fun setAutoTrack(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                if (!DriveDetection.start(app)) return@launch
            } else {
                DriveDetection.stop(app)
            }
            TrackingPrefs.setAutoTrackEnabled(app, enabled)
            _autoTrack.value = enabled
        }
    }

    fun previousWeek() {
        _weekStart.value = _weekStart.value.minusWeeks(1)
    }

    fun nextWeek() {
        if (_weekStart.value < currentWeekStart()) _weekStart.value = _weekStart.value.plusWeeks(1)
    }

    fun isCurrentWeek(start: LocalDate) = start >= currentWeekStart()

    fun setDriver(tripId: Long, isMine: Boolean) {
        DriverCheck.dismiss(app, tripId)
        viewModelScope.launch { repository.setDriver(tripId, isMine) }
    }

    fun openTrip(tripId: Long) {
        _openTripId.value = tripId
    }

    fun closeTrip() {
        _openTripId.value = null
    }

    fun observeTrip(tripId: Long): Flow<Trip?> = repository.observeTrip(tripId)

    suspend fun routePoints(tripId: Long): List<RoutePoint> = repository.routePoints(tripId)

    suspend fun routePreview(tripId: Long): List<LatLon> =
        routePreviews[tripId] ?: RouteProfile.thin(repository.routePoints(tripId), maxPoints = 120)
            .map { LatLon(it.latitude, it.longitude) }
            .also { routePreviews[tripId] = it }

    /** Fill-ups the app spotted on this drive's stops. */
    fun fuelUpsForTrip(tripId: Long): Flow<List<FuelUp>> =
        fuelRepository.observeAll().map { all -> all.filter { it.tripId == tripId } }

    /** Removes a drive and its route for good, e.g. one recorded on a bus. */
    fun deleteTrip(tripId: Long) {
        DriverCheck.dismiss(app, tripId)
        if (_openTripId.value == tripId) _openTripId.value = null
        routePreviews.remove(tripId)
        viewModelScope.launch { repository.deleteTrip(tripId) }
    }

    /**
     * Opens the fill-up form: blank for now, or [draft] from a gas station notification or an existing fill-up.
     * A new fill-up goes to the drive's car when it came from a drive, else the shown car, else the active one.
     */
    fun openFuelUp(draft: FuelUp? = null) {
        val base = draft ?: FuelUp(time = System.currentTimeMillis(), litres = 0.0, pricePerLitre = 0.0)
        if (base.carId != null) {
            _fuelDraft.value = base
            return
        }
        viewModelScope.launch {
            val carId = base.tripId?.let { repository.trip(it)?.carId }
                ?: viewedCarId.value
                ?: carRepository.activeCarId()
            _fuelDraft.value = base.copy(carId = carId)
        }
    }

    fun closeFuelUp() {
        _fuelDraft.value = null
    }

    fun saveFuelUp(fuelUp: FuelUp) {
        _fuelDraft.value = null
        viewModelScope.launch { fuelRepository.save(fuelUp) }
    }

    fun deleteFuelUp(id: Long) {
        _fuelDraft.value = null
        viewModelScope.launch { fuelRepository.delete(id) }
    }

    /** Shows one car on the drives and fuel screens, or all cars together when [carId] is null. */
    fun viewCar(carId: Long?) {
        TrackingPrefs.setViewedCarId(app, carId)
        _viewedCarPick.value = carId
    }

    /** Picks the car new drives go to. A drive being recorded right now moves to it too. */
    fun setActiveCar(carId: Long) {
        TrackingPrefs.setActiveCarId(app, carId)
        _activeCarPick.value = carId
        DriveState.activeTripId.value?.let { tripId -> viewModelScope.launch { repository.setCar(tripId, carId) } }
    }

    fun setTripCar(tripId: Long, carId: Long) {
        viewModelScope.launch { repository.setCar(tripId, carId) }
    }

    fun addCar(name: String) {
        viewModelScope.launch { carRepository.add(name.trim()) }
    }

    fun renameCar(carId: Long, name: String) {
        viewModelScope.launch { carRepository.rename(carId, name.trim()) }
    }

    /** Deletes a car, moving its drives and fill-ups to [moveToCarId], or deleting them too when null. */
    fun deleteCar(carId: Long, moveToCarId: Long?) {
        viewModelScope.launch {
            carRepository.delete(carId, moveToCarId)
            _activeCarPick.value = TrackingPrefs.activeCarId(app)
        }
    }

    fun openCars() {
        _carsOpen.value = true
    }

    fun closeCars() {
        _carsOpen.value = false
    }

    fun setStationSpotting(enabled: Boolean) {
        TrackingPrefs.setStationSpottingEnabled(app, enabled)
        _stationSpotting.value = enabled
    }

    private fun currentWeekStart() = WeeklySummary.weekStartOf(LocalDate.now())

    fun startDrive() = DriveTrackingService.startManually(app)

    fun endDrive() = DriveTrackingService.stopManually(app)
}
