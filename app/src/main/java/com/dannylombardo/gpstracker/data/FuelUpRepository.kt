package com.dannylombardo.gpstracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class FuelUpRepository(private val dao: FuelUpDao) {

    fun observeAll(): Flow<List<FuelUp>> = dao.observeAll()

    /** Adds [fuelUp] if it's new (id 0), otherwise saves the changes to it. */
    suspend fun save(fuelUp: FuelUp) {
        if (fuelUp.id == 0L) dao.insert(fuelUp) else dao.update(fuelUp)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    companion object {
        fun get(context: Context) = FuelUpRepository(AppDatabase.get(context).fuelUpDao())
    }
}
