package com.dannylombardo.gpstracker.tracking

import android.content.Context
import androidx.core.content.edit

/** Whether the user has turned automatic drive detection on. */
object TrackingPrefs {
    private const val FILE = "tracking"
    private const val KEY_AUTO_TRACK = "auto_track"

    fun isAutoTrackEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_TRACK, false)

    fun setAutoTrackEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY_AUTO_TRACK, enabled) }
    }
}
