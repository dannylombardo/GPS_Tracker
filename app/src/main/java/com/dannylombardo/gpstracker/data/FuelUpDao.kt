package com.dannylombardo.gpstracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelUpDao {
    @Insert
    suspend fun insert(fuelUp: FuelUp): Long

    @Update
    suspend fun update(fuelUp: FuelUp)

    @Query("DELETE FROM fuel_ups WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM fuel_ups ORDER BY time DESC")
    fun observeAll(): Flow<List<FuelUp>>
}
