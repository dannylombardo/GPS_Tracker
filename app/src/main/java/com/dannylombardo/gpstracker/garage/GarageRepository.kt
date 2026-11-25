package com.dannylombardo.gpstracker.garage

import android.content.Context
import android.util.Log
import com.dannylombardo.gpstracker.data.AppDatabase
import com.dannylombardo.gpstracker.data.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** The garage's data, plus the job of analysing drives that haven't been yet. */
class GarageRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val dao = GarageDatabase.get(app).dao()
    private val tripDao = AppDatabase.get(app).tripDao()
    private val analyseLock = Mutex()

    val specs: Flow<List<CarSpec>> = dao.observeSpecs()
    val items: Flow<List<MaintenanceItem>> = dao.observeItems()
    val logs: Flow<List<MaintenanceLog>> = dao.observeLogs()
    val places: Flow<List<SavedPlace>> = dao.observePlaces()
    val insights: Flow<List<DriveInsight>> = dao.observeInsights()

    suspend fun saveSpec(spec: CarSpec) = dao.saveSpec(spec)

    suspend fun spec(carId: Long) = dao.spec(carId)

    suspend fun saveItem(item: MaintenanceItem) = dao.saveItem(item)

    suspend fun deleteItem(id: Long) = dao.deleteItem(id)

    /** Gives a car the usual list of jobs the first time its maintenance page is opened. */
    suspend fun addStarterItems(carId: Long, now: Long) {
        if (dao.itemCount(carId) > 0) return
        dao.insertItems(
            MaintenanceKind.starters.map {
                MaintenanceItem(
                    carId = carId,
                    name = it.label,
                    kind = it.name,
                    intervalKm = it.defaultKm,
                    intervalMonths = it.defaultMonths,
                    lastDoneAt = now,
                )
            },
        )
    }

    /** Logs a job as done, which also restarts its countdown. */
    suspend fun markDone(item: MaintenanceItem, at: Long, cost: Double?, odometerKm: Double?, note: String?) {
        dao.insertLog(
            MaintenanceLog(itemId = item.id, carId = item.carId, name = item.name, time = at, cost = cost, odometerKm = odometerKm, note = note),
        )
        dao.saveItem(item.copy(lastDoneAt = maxOf(item.lastDoneAt, at), remindedAt = null))
    }

    suspend fun addLog(log: MaintenanceLog) = dao.insertLog(log)

    suspend fun deleteLog(id: Long) = dao.deleteLog(id)

    suspend fun savePlace(place: SavedPlace) = dao.savePlace(place)

    suspend fun deletePlace(id: Long) = dao.deletePlace(id)

    /**
     * Works through finished drives that haven't been analysed (or were analysed by older
     * maths), a batch at a time so it never holds much in memory, and tidies away results
     * for drives that no longer exist.
     */
    suspend fun analysePending(trips: List<Trip>) = withContext(Dispatchers.IO) {
        analyseLock.withLock {
            val zone = ZoneId.systemDefault()
            val known = dao.insightVersions().associate { it.tripId to it.version }
            val finished = trips.filter { it.endTime != null }
            val todo = finished.filter { known[it.id] != DriveAnalysis.VERSION }
            todo.chunked(20).forEach { batch ->
                val results = batch.mapNotNull { trip ->
                    try {
                        DriveAnalysis.analyse(trip, tripDao.routePoints(trip.id), zone)
                    } catch (e: Exception) {
                        Log.w(TAG, "Couldn't analyse drive ${trip.id}", e)
                        null
                    }
                }
                dao.saveInsights(results)
            }
            val ids = finished.map { it.id }.toSet()
            known.keys.filter { it !in ids }.chunked(400).forEach { dao.deleteInsights(it) }
        }
    }

    /** Analyses every finished drive in the database (used after a drive ends, outside the app). */
    suspend fun analyseAll() {
        analysePending(tripDao.observeFinishedTrips().first())
    }

    companion object {
        private const val TAG = "GarageRepository"

        @Volatile
        private var instance: GarageRepository? = null

        fun get(context: Context): GarageRepository =
            instance ?: synchronized(this) { instance ?: GarageRepository(context).also { instance = it } }
    }
}
