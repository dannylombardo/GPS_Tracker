package com.dannylombardo.gpstracker.ui.garage

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dannylombardo.gpstracker.data.Car
import com.dannylombardo.gpstracker.data.CarRepository
import com.dannylombardo.gpstracker.data.FuelEconomy
import com.dannylombardo.gpstracker.data.FuelUp
import com.dannylombardo.gpstracker.data.FuelUpRepository
import com.dannylombardo.gpstracker.data.Trip
import com.dannylombardo.gpstracker.data.TripRepository
import com.dannylombardo.gpstracker.garage.Backup
import com.dannylombardo.gpstracker.garage.CarSpec
import com.dannylombardo.gpstracker.garage.CsvExport
import com.dannylombardo.gpstracker.garage.DriveInsight
import com.dannylombardo.gpstracker.garage.FuelPlan
import com.dannylombardo.gpstracker.garage.GaragePrefs
import com.dannylombardo.gpstracker.garage.GarageReminders
import com.dannylombardo.gpstracker.garage.GarageRepository
import com.dannylombardo.gpstracker.garage.GarageWidget
import com.dannylombardo.gpstracker.garage.Maintenance
import com.dannylombardo.gpstracker.garage.MaintenanceItem
import com.dannylombardo.gpstracker.garage.MaintenanceLog
import com.dannylombardo.gpstracker.garage.PdfReport
import com.dannylombardo.gpstracker.garage.PlaceKind
import com.dannylombardo.gpstracker.garage.Places
import com.dannylombardo.gpstracker.garage.Recap
import com.dannylombardo.gpstracker.garage.Records
import com.dannylombardo.gpstracker.garage.SavedPlace
import com.dannylombardo.gpstracker.tracking.DriveState
import com.dannylombardo.gpstracker.tracking.DriveTrackingService
import com.dannylombardo.gpstracker.tracking.LiveDrive
import com.dannylombardo.gpstracker.tracking.TrackingPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The pages the Garage opens over the tabs. */
sealed interface GaragePage {
    data object Live : GaragePage
    data object Spec : GaragePage
    data object Maintenance : GaragePage
    data object FuelPlanner : GaragePage
    data object Score : GaragePage
    data object Records : GaragePage
    data object Places : GaragePage
    data object Data : GaragePage
    data class Wrapped(val period: Recap.Period) : GaragePage
}

/** One car's garage card: its spec sheet, odometer and fuel range. */
data class CarGarage(
    val car: Car,
    val spec: CarSpec?,
    val odometerKm: Double,
    val trackedKm: Double,
    val driveCount: Int,
    val economy: FuelEconomy.Summary,
    val range: FuelPlan.Range?,
    val maintenance: List<Maintenance.Status>,
    val maintenanceSpent: Double,
)

/** A drive with its score, for the driving score page. */
data class ScoredDrive(val trip: Trip, val insight: DriveInsight)

data class ScoreSummary(
    val weekAverage: Int?,
    val lastWeekAverage: Int?,
    val allTimeAverage: Int?,
    val recent: List<ScoredDrive>,
    val weekHardBrakes: Int,
    val weekHardAccels: Int,
    val weekMinutesOver120: Long,
    val weekNightShare: Double,
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class GarageViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val repo = GarageRepository.get(application)
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val trips = TripRepository.get(application).observeFinishedTrips()
    private val fuelUps = FuelUpRepository.get(application).observeAll()
    val cars: StateFlow<List<Car>> = CarRepository.get(application).observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _carPick = MutableStateFlow(TrackingPrefs.activeCarId(application))

    /** The car the Garage is showing. */
    val carId: StateFlow<Long?> = combine(cars, _carPick) { cars, picked ->
        (cars.firstOrNull { it.id == picked } ?: cars.firstOrNull())?.id
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val insightMap = repo.insights.map { list -> list.associateBy { it.tripId } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val specs = repo.specs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val logs = repo.logs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val items = repo.items.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val savedPlaces = repo.places.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allTrips: StateFlow<List<Trip>> = trips.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allFuelUps: StateFlow<List<FuelUp>> = fuelUps.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val live: StateFlow<LiveDrive?> = DriveState.live

    val garage: StateFlow<CarGarage?> = combine(
        combine(cars, carId, specs) { c, id, s -> Triple(c, id, s) },
        allTrips,
        allFuelUps,
        items,
        logs,
    ) { (cars, id, specs), trips, fuelUps, items, logs ->
        val car = cars.firstOrNull { it.id == id } ?: return@combine null
        val spec = specs.firstOrNull { it.carId == car.id }
        val carTrips = trips.filter { it.carId == car.id }
        val economy = FuelEconomy.summarise(fuelUps.filter { it.carId == car.id }, carTrips)
        val now = System.currentTimeMillis()
        CarGarage(
            car = car,
            spec = spec,
            odometerKm = Maintenance.odometerKm(spec, trips, car.id),
            trackedKm = carTrips.sumOf { it.distanceMeters } / 1000,
            driveCount = carTrips.size,
            economy = economy,
            range = FuelPlan.range(spec?.fuelTankLitres, economy.averageLitresPer100Km, economy.distanceSinceLastFullMeters?.div(1000)),
            maintenance = Maintenance.statuses(items.filter { it.carId == car.id }, trips, now, zone),
            maintenanceSpent = logs.filter { it.carId == car.id }.sumOf { it.cost ?: 0.0 },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _budgetAmount = MutableStateFlow(GaragePrefs.monthlyBudget(application))
    val budgetAmount: StateFlow<Double?> = _budgetAmount.asStateFlow()

    /** This month's fuel budget across every car. */
    val budget: StateFlow<FuelPlan.Budget?> = combine(allFuelUps, _budgetAmount) { fuelUps, amount ->
        amount?.let { FuelPlan.budget(fuelUps, it, LocalDate.now(zone), zone) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** This month's spend even without a budget set. */
    val monthSpent: StateFlow<Double> = allFuelUps.map { list ->
        val month = YearMonth.now(zone)
        list.filter { YearMonth.from(java.time.Instant.ofEpochMilli(it.time).atZone(zone)) == month }.sumOf { it.totalCost }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val prices: StateFlow<FuelPlan.Prices?> = combine(allFuelUps, carId) { list, id ->
        FuelPlan.prices(list.filter { it.carId == id }, zone)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val score: StateFlow<ScoreSummary?> = combine(allTrips, insightMap) { trips, insights ->
        val mine = trips.filter { it.countsAsMine }
        val scored = mine.mapNotNull { t -> insights[t.id]?.takeIf { it.score != null }?.let { ScoredDrive(t, it) } }
        val weekStart = com.dannylombardo.gpstracker.data.WeeklySummary.weekStartOf(LocalDate.now(zone))
        val from = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val lastFrom = weekStart.minusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val week = scored.filter { it.trip.startTime >= from }
        val lastWeek = scored.filter { it.trip.startTime in lastFrom until from }
        val weekTrips = mine.filter { it.startTime >= from }
        val weekMillis = weekTrips.sumOf { (it.endTime ?: it.startTime) - it.startTime }.coerceAtLeast(1)
        fun avg(list: List<ScoredDrive>): Int? {
            // Weighted by distance, so a long smooth drive counts more than a short hop.
            val km = list.sumOf { it.trip.distanceMeters }
            return if (list.isEmpty() || km <= 0) null else (list.sumOf { it.insight.score!! * it.trip.distanceMeters } / km).toInt()
        }
        ScoreSummary(
            weekAverage = avg(week),
            lastWeekAverage = avg(lastWeek),
            allTimeAverage = avg(scored),
            recent = scored.sortedByDescending { it.trip.startTime }.take(30),
            weekHardBrakes = weekTrips.sumOf { insights[it.id]?.hardBrakes ?: 0 },
            weekHardAccels = weekTrips.sumOf { insights[it.id]?.hardAccels ?: 0 },
            weekMinutesOver120 = weekTrips.sumOf { insights[it.id]?.secondsOver120 ?: 0 } / 60,
            weekNightShare = weekTrips.sumOf { insights[it.id]?.nightMillis ?: 0 }.toDouble() / weekMillis,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bests: StateFlow<Records.Bests?> = combine(allTrips, insightMap) { trips, insights ->
        Records.bests(trips, insights, zone, LocalDate.now(zone))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val badges: StateFlow<List<Records.Badge>> = combine(allTrips, insightMap, allFuelUps, logs, bests) { trips, insights, fuelUps, logs, bests ->
        if (bests == null) emptyList() else Records.badges(trips, insights, fuelUps, logs.size, bests, zone)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val places: StateFlow<Places.Summary?> = combine(allTrips, insightMap, savedPlaces) { trips, insights, saved ->
        Places.summarise(trips.filter { it.countsAsMine }, insights, saved, zone)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Months and years with drives, newest first, for the recap picker. */
    val recapPeriods: StateFlow<Pair<List<YearMonth>, List<Int>>> = allTrips.map { Recap.periodsWithDrives(it, zone) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<YearMonth>() to emptyList())

    fun recap(period: Recap.Period): StateFlow<Recap?> =
        combine(allTrips, allFuelUps, insightMap, places) { trips, fuelUps, insights, places ->
            Recap.of(period, trips, fuelUps, insights, places, zone)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _tabRequests = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    /** Fires when something (a notification, the widget) asks for the Garage tab. */
    val tabRequests = _tabRequests.receiveAsFlow()

    /** Opens the app on the Garage tab, or straight onto the live dash. */
    fun handleOpen(what: String?) {
        when (what) {
            com.dannylombardo.gpstracker.garage.GarageIntents.GARAGE -> {
                _page.value = null
                _tabRequests.trySend(Unit)
            }
            com.dannylombardo.gpstracker.garage.GarageIntents.LIVE -> {
                _tabRequests.trySend(Unit)
                _page.value = GaragePage.Live
            }
        }
    }

    private val _page = MutableStateFlow<GaragePage?>(null)
    val page: StateFlow<GaragePage?> = _page.asStateFlow()

    private val _lowFuelReminder = MutableStateFlow(GaragePrefs.lowFuelReminder(application))
    val lowFuelReminder: StateFlow<Boolean> = _lowFuelReminder.asStateFlow()
    private val _maintenanceReminder = MutableStateFlow(GaragePrefs.maintenanceReminder(application))
    val maintenanceReminder: StateFlow<Boolean> = _maintenanceReminder.asStateFlow()
    private val _budgetAlert = MutableStateFlow(GaragePrefs.budgetAlert(application))
    val budgetAlert: StateFlow<Boolean> = _budgetAlert.asStateFlow()
    private val _keepScreenOn = MutableStateFlow(GaragePrefs.keepScreenOnWhileLive(application))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)

    /** What's being saved or loaded right now, for a spinner. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    init {
        // Score any drives that haven't been yet, whenever the list of drives changes.
        viewModelScope.launch {
            allTrips.debounce(500).collect {
                repo.analysePending(it)
                GarageWidget.requestUpdate(app)
            }
        }
        // Budget alerts for fill-ups logged in the app.
        viewModelScope.launch {
            allFuelUps.debounce(1_000).distinctUntilChanged().collect {
                GarageReminders.checkBudget(app, it)
                GarageWidget.requestUpdate(app)
            }
        }
    }

    fun open(page: GaragePage) {
        if (page == GaragePage.Maintenance) {
            carId.value?.let { id -> viewModelScope.launch { repo.addStarterItems(id, System.currentTimeMillis()) } }
        }
        _page.value = page
    }

    fun close() {
        _page.value = null
    }

    fun pickCar(id: Long) {
        _carPick.value = id
    }

    fun saveSpec(spec: CarSpec) = viewModelScope.launch { repo.saveSpec(spec) }

    fun saveItem(item: MaintenanceItem) = viewModelScope.launch { repo.saveItem(item) }

    fun deleteItem(id: Long) = viewModelScope.launch { repo.deleteItem(id) }

    fun markDone(item: MaintenanceItem, at: Long, cost: Double?, note: String?) = viewModelScope.launch {
        val odometer = garage.value?.takeIf { it.car.id == item.carId }?.odometerKm
        repo.markDone(item, at, cost, odometer, note)
    }

    fun addLog(carId: Long, name: String, at: Long, cost: Double?, note: String?) = viewModelScope.launch {
        repo.addLog(MaintenanceLog(itemId = null, carId = carId, name = name, time = at, cost = cost, odometerKm = garage.value?.odometerKm, note = note))
    }

    fun deleteLog(id: Long) = viewModelScope.launch { repo.deleteLog(id) }

    fun namePlace(place: Places.Place, name: String, kind: PlaceKind) = viewModelScope.launch {
        repo.savePlace(
            SavedPlace(id = place.savedId ?: 0, name = name.ifBlank { place.name }, latitude = place.latitude, longitude = place.longitude, kind = kind.name),
        )
    }

    fun forgetPlaceName(place: Places.Place) = viewModelScope.launch { place.savedId?.let { repo.deletePlace(it) } }

    fun setBudget(amount: Double?) {
        GaragePrefs.setMonthlyBudget(app, amount)
        _budgetAmount.value = amount
    }

    fun setLowFuelReminder(on: Boolean) {
        GaragePrefs.setLowFuelReminder(app, on)
        _lowFuelReminder.value = on
    }

    fun setMaintenanceReminder(on: Boolean) {
        GaragePrefs.setMaintenanceReminder(app, on)
        _maintenanceReminder.value = on
    }

    fun setBudgetAlert(on: Boolean) {
        GaragePrefs.setBudgetAlert(app, on)
        _budgetAlert.value = on
    }

    fun setKeepScreenOn(on: Boolean) {
        GaragePrefs.setKeepScreenOnWhileLive(app, on)
        _keepScreenOn.value = on
    }

    fun endDrive() = DriveTrackingService.stopManually(app)

    fun startDrive() = DriveTrackingService.startManually(app)

    enum class CsvKind { DRIVES, FILL_UPS, MAINTENANCE }

    fun exportCsv(kind: CsvKind, uri: Uri) = runFileJob("Exporting") {
        val cars = cars.value
        val text = when (kind) {
            CsvKind.DRIVES -> CsvExport.drives(allTrips.value, cars, insightMap.value, zone)
            CsvKind.FILL_UPS -> {
                val stats = cars.flatMap { car ->
                    FuelEconomy.summarise(allFuelUps.value.filter { it.carId == car.id }, allTrips.value.filter { it.carId == car.id }).stats.entries
                }.associate { it.key to it.value }
                CsvExport.fillUps(allFuelUps.value, cars, stats, zone)
            }
            CsvKind.MAINTENANCE -> CsvExport.maintenance(logs.value, cars, zone)
        }
        withContext(Dispatchers.IO) {
            app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: error("Couldn't open the file")
        }
        "Saved"
    }

    fun exportPdf(range: PdfReport.Range, uri: Uri) = runFileJob("Making the report") {
        PdfReport.write(app, uri, range, allTrips.value, allFuelUps.value, logs.value, cars.value, zone)
        "Report saved"
    }

    fun backup(uri: Uri) = runFileJob("Backing up") {
        val r = Backup.write(app, uri)
        "Backed up ${r.drives} drives and ${r.fillUps} fill-ups"
    }

    fun restore(uri: Uri) = runFileJob("Restoring") {
        if (DriveState.isServiceRunning) error("Finish the drive in progress first")
        val r = Backup.restore(app, uri)
        repo.analysePending(TripRepository.get(app).observeFinishedTrips().first())
        GarageWidget.updateAll(app)
        "Restored ${r.drives} drives, ${r.fillUps} fill-ups and ${r.cars} cars"
    }

    private fun runFileJob(label: String, job: suspend () -> String) {
        viewModelScope.launch {
            _busy.value = label
            val message = try {
                job()
            } catch (e: Exception) {
                e.message ?: "Something went wrong"
            } finally {
                _busy.value = null
            }
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }
}
