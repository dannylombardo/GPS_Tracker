package com.dannylombardo.gpstracker.tracking

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dannylombardo.gpstracker.GpsTrackerApp
import com.dannylombardo.gpstracker.R
import com.dannylombardo.gpstracker.data.FuelUp
import com.dannylombardo.gpstracker.ui.MainActivity

/**
 * After a drive that stopped at a gas station, offers to log the fill-up.
 * Tapping opens the app with the fill-up form already pointing at that stop.
 */
object FuelPrompt {
    private const val EXTRA_TIME = "fuelTime"
    private const val EXTRA_LATITUDE = "fuelLatitude"
    private const val EXTRA_LONGITUDE = "fuelLongitude"
    private const val EXTRA_STATION = "fuelStation"
    private const val EXTRA_TRIP_ID = "fuelTripId"

    // Clear of the tracking (1) and driver check (1_000 and up) notification ids.
    private const val NOTIFICATION_ID_BASE = 2_000_000

    private fun notificationId(tripId: Long) = NOTIFICATION_ID_BASE + (tripId % 1_000_000).toInt()

    @SuppressLint("MissingPermission")
    fun show(context: Context, tripId: Long, visit: StationVisit) {
        if (!Permissions.hasNotifications(context)) return
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_TIME, visit.stop.timeMillis)
            .putExtra(EXTRA_LATITUDE, visit.stop.latitude)
            .putExtra(EXTRA_LONGITUDE, visit.stop.longitude)
            .putExtra(EXTRA_STATION, visit.station.name)
            .putExtra(EXTRA_TRIP_ID, tripId)
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId(tripId),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val where = visit.station.name ?: "a gas station"
        val notification = NotificationCompat.Builder(context, GpsTrackerApp.CHANNEL_FUEL)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle("Filled up at $where?")
            .setContentText("Tap to add the litres and price")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(tripId), notification)
    }

    /** The fill-up a tapped notification was about, with litres and price still to fill in. */
    fun draftFrom(intent: Intent?): FuelUp? {
        if (intent == null || !intent.hasExtra(EXTRA_TIME)) return null
        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1).takeIf { it >= 0 }
        return FuelUp(
            time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis()),
            litres = 0.0,
            pricePerLitre = 0.0,
            stationName = intent.getStringExtra(EXTRA_STATION),
            latitude = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN).takeUnless { it.isNaN() },
            longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN).takeUnless { it.isNaN() },
            tripId = tripId,
        )
    }
}
