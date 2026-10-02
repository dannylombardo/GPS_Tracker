package com.dannylombardo.gpstracker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.dannylombardo.gpstracker.tracking.FuelPrompt
import com.dannylombardo.gpstracker.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // On recreation (e.g. rotation) the view model still has any open fill-up form or drive.
        if (savedInstanceState == null) handle(intent)
        setContent {
            AppTheme {
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
