package com.dannylombardo.gpstracker.data

import android.content.Context
import com.dannylombardo.gpstracker.tracking.TrackingPrefs
import kotlinx.coroutines.flow.Flow

class CarRepository(private val context: Context, private val dao: CarDao) {

    fun observeAll(): Flow<List<Car>> = dao.observeAll()

    suspend fun add(name: String): Long = dao.insert(Car(name = name))

    suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    /**
     * Deletes a car, moving its drives and fill-ups to [moveToCarId], or deleting them too when null.
     * The last car can't go: drives always need a car to belong to.
     */
    suspend fun delete(id: Long, moveToCarId: Long?) {
        val remaining = dao.all().filter { it.id != id }
        val fallback = remaining.firstOrNull { it.id == moveToCarId } ?: remaining.firstOrNull() ?: return
        dao.delete(id, moveToCarId?.let { fallback.id }, fallback.id)
        if (TrackingPrefs.activeCarId(context) == id) TrackingPrefs.setActiveCarId(context, fallback.id)
    }

    /**
     * The car new drives go to: the one picked on the home screen, or the first car
     * if that one was deleted. Makes a car if somehow there are none, so a drive
     * always has somewhere to go.
     */
    suspend fun activeCarId(): Long {
        val cars = dao.all()
        val picked = TrackingPrefs.activeCarId(context)
        return cars.firstOrNull { it.id == picked }?.id
            ?: cars.firstOrNull()?.id
            ?: dao.insert(Car(name = DEFAULT_NAME))
    }

    companion object {
        const val DEFAULT_NAME = "My car"

        fun get(context: Context) =
            CarRepository(context.applicationContext, AppDatabase.get(context).carDao())
    }
}
