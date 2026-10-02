package com.dannylombardo.gpstracker.tracking

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dannylombardo.gpstracker.GpsTrackerApp
import com.dannylombardo.gpstracker.R
import com.dannylombardo.gpstracker.data.TripRepository
import com.dannylombardo.gpstracker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * After each drive, asks "were you driving?" so drives where someone else drove
 * (or you were a passenger) can be left out of your totals.
 */
object DriverCheck {
    internal const val ACTION_ANSWER = "com.dannylombardo.gpstracker.action.DRIVER_ANSWER"
    internal const val EXTRA_TRIP_ID = "tripId"
    internal const val EXTRA_IS_MINE = "isMine"

    // Leaves room below for the tracking notification's id.
    private const val NOTIFICATION_ID_BASE = 1_000

    private fun notificationId(tripId: Long) = NOTIFICATION_ID_BASE + (tripId % 1_000_000).toInt()

    @SuppressLint("MissingPermission")
    fun ask(context: Context, tripId: Long, distanceMeters: Double) {
        if (!Permissions.hasNotifications(context)) return
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, GpsTrackerApp.CHANNEL_DRIVER_CHECK)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle("Were you driving?")
            .setContentText(
                String.format(Locale.getDefault(), "Drive of %.1f km just ended", distanceMeters / 1000),
            )
            .setContentIntent(openApp)
            .addAction(0, "Me", answerIntent(context, tripId, isMine = true))
            .addAction(0, "Someone else", answerIntent(context, tripId, isMine = false))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(tripId), notification)
    }

    fun dismiss(context: Context, tripId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(tripId))
    }

    private fun answerIntent(context: Context, tripId: Long, isMine: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            // Unique per trip and answer, so the two buttons don't overwrite each other's extras.
            notificationId(tripId) * 2 + if (isMine) 1 else 0,
            Intent(context, DriverAnswerReceiver::class.java)
                .setAction(ACTION_ANSWER)
                .putExtra(EXTRA_TRIP_ID, tripId)
                .putExtra(EXTRA_IS_MINE, isMine),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Saves the answer from a notification button. */
class DriverAnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DriverCheck.ACTION_ANSWER) return
        val tripId = intent.getLongExtra(DriverCheck.EXTRA_TRIP_ID, -1)
        if (tripId < 0) return
        val isMine = intent.getBooleanExtra(DriverCheck.EXTRA_IS_MINE, true)
        DriverCheck.dismiss(context, tripId)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TripRepository.get(context.applicationContext).setDriver(tripId, isMine)
            } finally {
                pending.finish()
            }
        }
    }
}
