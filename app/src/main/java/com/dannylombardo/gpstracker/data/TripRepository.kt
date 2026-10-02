package com.dannylombardo.gpstracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TripRepository(private val dao: TripDao) {

    fun observeFinishedTrips(): Flow<List<Trip>> = dao.observeFinishedTrips()

    fun observeTrip(tripId: Long): Flow<Trip?> = dao.observeTrip(tripId)

    suspend fun startTrip(startTime: Long): Long = dao.insertTrip(Trip(startTime = startTime))

    suspend fun addPoint(point: RoutePoint, distanceMeters: Double, topSpeedMetersPerSecond: Double) {
        dao.insertPoint(point)
        dao.updateProgress(point.tripId, distanceMeters, topSpeedMetersPerSecond)
    }

    suspend fun setDriver(tripId: Long, isMine: Boolean) = dao.setDriver(tripId, isMine)

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
