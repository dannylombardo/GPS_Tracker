package com.dannylombardo.gpstracker.garage

import android.content.Context
import android.content.Intent
import com.dannylombardo.gpstracker.ui.MainActivity

/** Extras that open the app straight onto the Garage tab or the live drive screen. */
object GarageIntents {
    const val EXTRA_OPEN = "openGaragePage"
    const val GARAGE = "garage"
    const val LIVE = "live"

    fun openLive(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN, LIVE)

    fun openGarage(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN, GARAGE)
}
