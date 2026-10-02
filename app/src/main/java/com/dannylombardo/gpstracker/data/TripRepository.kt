package com.dannylombardo.gpstracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TripRepository(private val dao: TripDao) {

    fun observeFinishedTrips(): Flow<List<Trip>> = dao.observeFinishedTrips()

    fun observeTrip(tripId: Long): Flow<Trip?> = dao.observeTrip(tripId)

    suspend fun startTrip(startTime: Long, carId: Long): Long =
        dao.insertTrip(Trip(startTime = startTime, carId = carId))

    suspend fun trip(tripId: Long): Trip? = dao.trip(tripId)

    suspend fun addPoint(point: RoutePoint, distanceMeters: Double, topSpeedMetersPerSecond: Double) {
        dao.insertPoint(point)
        dao.updateProgress(point.tripId, distanceMeters, topSpeedMetersPerSecond)
    }

    suspend fun setDriver(tripId: Long, isMine: Boolean) = dao.setDriver(tripId, isMine)

    suspend fun setCar(tripId: Long, carId: Long) = dao.setCar(tripId, carId)

    fun observeBinnedTrips(): Flow<List<Trip>> = dao.observeBinnedTrips()

    /** Moves a drive to Recently deleted, out of every list and total. */
    suspend fun moveToBin(tripId: Long, now: Long) = dao.setDeletedAt(tripId, now)

    suspend fun restore(tripId: Long) = dao.setDeletedAt(tripId, null)

    /** Removes a drive and its route for good. */
    suspend fun deleteTrip(tripId: Long) = dao.deleteTrip(tripId)

    suspend fun emptyBin() = dao.emptyBin()

    /** Clears out drives that have sat in Recently deleted longer than [DriveBin.KEEP_DAYS]. */
    suspend fun purgeExpired(now: Long) = dao.deleteBinnedBefore(DriveBin.expiryCutoff(now))

    suspend fun lastPoint(tripId: Long): RoutePoint? = dao.lastPoint(tripId)

    suspend fun routePoints(tripId: Long): List<RoutePoint> = dao.routePoints(tripId)

    /**
     * Closes a trip. Trips shorter than [minDistanceMeters] are false starts
     * (sitting in a parked car, a bus that barely moved) and get deleted.
     * Returns true if the trip was kept.
     */
    suspend fun finishTrip(
        tripId: Long,
        endTime: Long,
        distanceMeters: Double,
        topSpeedMetersPerSecond: Double?,
        minDistanceMeters: Double,
    ): Boolean {
        if (distanceMeters < minDistanceMeters) {
            dao.deleteTrip(tripId)
            return false
        }
        dao.finishTrip(tripId, endTime, distanceMeters, topSpeedMetersPerSecond)
        return true
    }

    /**
     * An unfinished trip whose last fix is newer than [windowMillis] can be picked
     * back up, e.g. when Android restarted the tracking service mid-drive.
     */
    suspend fun findResumableTrip(now: Long, windowMillis: Long): Trip? =
        dao.unfinishedTrips().firstOrNull { trip ->
            val lastSeen = dao.lastPoint(trip.id)?.time ?: trip.startTime
            now - lastSeen <= windowMillis
        }

    /** Closes trips left open by a crash or a killed process, ending them at their last fix. */
    suspend fun closeAbandonedTrips(exceptTripId: Long?, minDistanceMeters: Double) {
        dao.unfinishedTrips()
            .filter { it.id != exceptTripId }
            .forEach { trip ->
                val endTime = dao.lastPoint(trip.id)?.time ?: trip.startTime
                finishTrip(trip.id, endTime, trip.distanceMeters, trip.topSpeedMetersPerSecond, minDistanceMeters)
            }
    }

    companion object {
        fun get(context: Context) = TripRepository(AppDatabase.get(context).tripDao())
    }
}
