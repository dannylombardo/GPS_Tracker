package com.dannylombardo.gpstracker.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/** Receives "got into a vehicle" / "left the vehicle" events and starts or ends a drive. */
class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        if (!TrackingPrefs.isAutoTrackEnabled(context)) return

        // Events arrive oldest first; only the most recent one says where we are now.
        val latest = result.transitionEvents.lastOrNull() ?: return
        val enteredVehicle = latest.activityType == DetectedActivity.IN_VEHICLE &&
            latest.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER

        if (enteredVehicle) {
            DriveTrackingService.vehicleEntered(context)
        } else if (DriveState.isServiceRunning) {
            DriveTrackingService.vehicleExited(context)
        }
    }
}
