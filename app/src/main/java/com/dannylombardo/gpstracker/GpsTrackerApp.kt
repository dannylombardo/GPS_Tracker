package com.dannylombardo.gpstracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class GpsTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val tracking = NotificationChannel(
            CHANNEL_TRACKING,
            getString(R.string.channel_tracking_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.channel_tracking_description) }
        val driverCheck = NotificationChannel(
            CHANNEL_DRIVER_CHECK,
            getString(R.string.channel_driver_check_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = getString(R.string.channel_driver_check_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(tracking, driverCheck))
    }

    companion object {
        const val CHANNEL_TRACKING = "drive_tracking"
        const val CHANNEL_DRIVER_CHECK = "driver_check"
    }
}
