package com.dannylombardo.gpstracker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.tracking.FuelPrompt
import com.dannylombardo.gpstracker.ui.theme.AppTheme
import com.dannylombardo.gpstracker.ui.theme.LocalDarkTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // On recreation (e.g. rotation) the view model still has any open fill-up form or drive.
        if (savedInstanceState == null) handle(intent)
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            AppTheme(themeMode) {
                // Status and navigation bar icons follow the app's theme, which can differ from the phone's.
                val dark = LocalDarkTheme.current
                DisposableEffect(dark) {
                    val style = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose { }
                }
                AppRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Opens the fill-up form or a drive's page when we were launched from one of our notifications. */
    private fun handle(intent: Intent?) {
        if (intent == null || (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
        FuelPrompt.draftFrom(intent)?.let(viewModel::openFuelUp)
        intent.getLongExtra(EXTRA_OPEN_TRIP_ID, -1).takeIf { it >= 0 }?.let(viewModel::openTrip)
    }

    companion object {
        private const val EXTRA_OPEN_TRIP_ID = "openTripId"

        /** Opens the app on a drive's page. */
        fun openTripIntent(context: Context, tripId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_TRIP_ID, tripId)
    }
}
