package com.dannylombardo.gpstracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class GpsTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_TRACKING,
            getString(R.string.channel_tracking_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.channel_tracking_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_TRACKING = "drive_tracking"
    }
}
