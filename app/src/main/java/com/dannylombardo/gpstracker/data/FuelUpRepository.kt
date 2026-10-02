package com.dannylombardo.gpstracker.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class FuelUpRepository(private val dao: FuelUpDao) {

    fun observeAll(): Flow<List<FuelUp>> = dao.observeAll()

    /** Adds [fuelUp] if it's new (id 0), otherwise saves the changes to it. */
    suspend fun save(fuelUp: FuelUp) {
        if (fuelUp.id == 0L) dao.insert(fuelUp) else dao.update(fuelUp)
    }

    fun observeBinned(): Flow<List<FuelUp>> = dao.observeBinned()

    /** Moves a fill-up to Recently deleted, out of every total. */
    suspend fun moveToBin(id: Long, now: Long) = dao.setDeletedAt(id, now)

    suspend fun restore(id: Long) = dao.setDeletedAt(id, null)

    /** Removes a fill-up for good. */
    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun emptyBin() = dao.emptyBin()

    /** Clears out fill-ups that have sat in Recently deleted longer than [DriveBin.KEEP_DAYS]. */
    suspend fun purgeExpired(now: Long) = dao.deleteBinnedBefore(DriveBin.expiryCutoff(now))

    companion object {
        fun get(context: Context) = FuelUpRepository(AppDatabase.get(context).fuelUpDao())
    }
}
