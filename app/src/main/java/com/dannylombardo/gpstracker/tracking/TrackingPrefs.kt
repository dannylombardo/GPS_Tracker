package com.dannylombardo.gpstracker.tracking

import android.content.Context
import androidx.core.content.edit

/** The user's tracking switches (automatic drive detection, gas station spotting) and car choices. */
object TrackingPrefs {
    private const val FILE = "tracking"
    private const val KEY_AUTO_TRACK = "auto_track"
    private const val KEY_SPOT_STATIONS = "spot_gas_stations"
    private const val KEY_ACTIVE_CAR = "active_car"
    private const val KEY_VIEWED_CAR = "viewed_car"

    fun isAutoTrackEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_TRACK, false)

    fun setAutoTrackEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY_AUTO_TRACK, enabled) }
    }

    /** On unless turned off: checks OpenStreetMap after each drive for a gas station stop. */
    fun isStationSpottingEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_SPOT_STATIONS, true)

    fun setStationSpottingEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY_SPOT_STATIONS, enabled) }
    }

    /** The car new drives go to, or null if none was picked yet. */
    fun activeCarId(context: Context): Long? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_ACTIVE_CAR, -1).takeIf { it >= 0 }

    fun setActiveCarId(context: Context, carId: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putLong(KEY_ACTIVE_CAR, carId) }
    }

    /** The car the screens are showing, or null for all cars together. */
    fun viewedCarId(context: Context): Long? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_VIEWED_CAR, -1).takeIf { it >= 0 }

    fun setViewedCarId(context: Context, carId: Long?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putLong(KEY_VIEWED_CAR, carId ?: -1) }
    }
}
