package com.dannylombardo.gpstracker.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Activity recognition subscriptions don't survive a reboot or an app update, so renew them. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        if (!TrackingPrefs.isAutoTrackEnabled(context)) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                DriveDetection.start(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
