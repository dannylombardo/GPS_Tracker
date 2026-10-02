package com.dannylombardo.gpstracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CarDao {
    @Insert
    abstract suspend fun insert(car: Car): Long

    @Query("UPDATE cars SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("SELECT * FROM cars ORDER BY id")
    abstract fun observeAll(): Flow<List<Car>>

    @Query("SELECT * FROM cars ORDER BY id")
    abstract suspend fun all(): List<Car>

    @Query("DELETE FROM cars WHERE id = :id")
    abstract suspend fun deleteCar(id: Long)

    @Query("UPDATE trips SET carId = :toCarId WHERE carId = :fromCarId")
    abstract suspend fun moveTrips(fromCarId: Long, toCarId: Long)

    @Query("UPDATE fuel_ups SET carId = :toCarId WHERE carId = :fromCarId")
    abstract suspend fun moveFuelUps(fromCarId: Long, toCarId: Long)

    /** Finished drives only: one still being recorded is moved instead, so tracking isn't pulled out from under it. */
    @Query("DELETE FROM trips WHERE carId = :carId AND endTime IS NOT NULL")
    abstract suspend fun deleteFinishedTrips(carId: Long)

    @Query("DELETE FROM fuel_ups WHERE carId = :carId")
    abstract suspend fun deleteFuelUps(carId: Long)

    /**
     * Removes a car, handing its drives and fill-ups to [moveToCarId], or deleting them when
     * that's null. Anything not deleted ends up with [moveToCarId], or [fallbackCarId].
     */
    @Transaction
    open suspend fun delete(id: Long, moveToCarId: Long?, fallbackCarId: Long) {
        if (moveToCarId == null) {
            deleteFinishedTrips(id)
            deleteFuelUps(id)
        }
        moveTrips(id, moveToCarId ?: fallbackCarId)
        moveFuelUps(id, moveToCarId ?: fallbackCarId)
        deleteCar(id)
    }
}
