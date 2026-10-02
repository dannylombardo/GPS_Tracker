package com.dannylombardo.gpstracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insertTrip(trip: Trip): Long

    @Insert
    suspend fun insertPoint(point: RoutePoint)

    @Query("UPDATE trips SET distanceMeters = :distanceMeters, topSpeedMetersPerSecond = :topSpeed WHERE id = :tripId")
    suspend fun updateProgress(tripId: Long, distanceMeters: Double, topSpeed: Double)

    @Query("UPDATE trips SET endTime = :endTime, distanceMeters = :distanceMeters, topSpeedMetersPerSecond = :topSpeed WHERE id = :tripId")
    suspend fun finishTrip(tripId: Long, endTime: Long, distanceMeters: Double, topSpeed: Double?)

    @Query("UPDATE trips SET isMine = :isMine WHERE id = :tripId")
    suspend fun setDriver(tripId: Long, isMine: Boolean)

    @Query("UPDATE trips SET carId = :carId WHERE id = :tripId")
    suspend fun setCar(tripId: Long, carId: Long)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: Long)

    @Query("UPDATE trips SET deletedAt = :deletedAt WHERE id = :tripId")
    suspend fun setDeletedAt(tripId: Long, deletedAt: Long?)

    @Query("DELETE FROM trips WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun deleteBinnedBefore(before: Long)

    @Query("DELETE FROM trips WHERE deletedAt IS NOT NULL")
    suspend fun emptyBin()

    @Query("SELECT * FROM trips WHERE endTime IS NULL ORDER BY startTime DESC")
    suspend fun unfinishedTrips(): List<Trip>

    @Query("SELECT * FROM route_points WHERE tripId = :tripId ORDER BY time DESC LIMIT 1")
    suspend fun lastPoint(tripId: Long): RoutePoint?

    @Query("SELECT * FROM route_points WHERE tripId = :tripId ORDER BY time")
    suspend fun routePoints(tripId: Long): List<RoutePoint>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun trip(tripId: Long): Trip?

    @Query("SELECT * FROM trips WHERE id = :tripId")
    fun observeTrip(tripId: Long): Flow<Trip?>

    /** Finished drives, leaving out the ones in Recently deleted. */
    @Query("SELECT * FROM trips WHERE endTime IS NOT NULL AND deletedAt IS NULL ORDER BY startTime DESC")
    fun observeFinishedTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeBinnedTrips(): Flow<List<Trip>>
}
