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

    @Query("UPDATE fuel_ups SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun setDeletedAt(id: Long, deletedAt: Long?)

    @Query("DELETE FROM fuel_ups WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun deleteBinnedBefore(before: Long)

    @Query("DELETE FROM fuel_ups WHERE deletedAt IS NOT NULL")
    suspend fun emptyBin()

    /** Every fill-up except the ones in Recently deleted. */
    @Query("SELECT * FROM fuel_ups WHERE deletedAt IS NULL ORDER BY time DESC")
    fun observeAll(): Flow<List<FuelUp>>

    @Query("SELECT * FROM fuel_ups WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeBinned(): Flow<List<FuelUp>>
}
