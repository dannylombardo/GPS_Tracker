package com.dannylombardo.gpstracker.tracking

import android.content.Context
import androidx.core.content.edit

/** The user's tracking switches: automatic drive detection and gas station spotting. */
object TrackingPrefs {
    private const val FILE = "tracking"
    private const val KEY_AUTO_TRACK = "auto_track"
    private const val KEY_SPOT_STATIONS = "spot_gas_stations"

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
}
