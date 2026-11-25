package com.dannylombardo.gpstracker.ui

import android.content.Context
import androidx.core.content.edit
import com.dannylombardo.gpstracker.ui.theme.ThemeMode

/** Whether the app follows the phone's light or dark setting, or sticks to one. */
internal object AppearancePrefs {
    private const val FILE = "appearance"
    private const val KEY_THEME = "theme"

    fun themeMode(context: Context): ThemeMode {
        val saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_THEME, null)
        return ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.System
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putString(KEY_THEME, mode.name) }
    }
}
