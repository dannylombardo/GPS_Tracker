package com.dannylombardo.gpstracker.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/** Receives activity recognition events and starts or ends a drive. */
class ActivityTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!TrackingPrefs.isAutoTrackEnabled(context)) return
        when {
            ActivityTransitionResult.hasResult(intent) -> onTransition(context, intent)
            ActivityRecognitionResult.hasResult(intent) -> onReading(context, intent)
        }
    }

    /** "Got into a vehicle" / "left the vehicle". */
    private fun onTransition(context: Context, intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        // Events arrive oldest first; only the most recent one says where we are now.
        val latest = result.transitionEvents.lastOrNull() ?: return
        val enteredVehicle = latest.activityType == DetectedActivity.IN_VEHICLE &&
            latest.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER

        if (enteredVehicle) {
            DriveTrackingService.vehicleEntered(context, DriveTrigger.ACTIVITY)
        } else if (DriveState.isServiceRunning) {
            DriveTrackingService.vehicleExited(context)
        }
    }

    /** A regular reading: starts a GPS check early when the sensors are fairly sure we're in a car. */
    private fun onReading(context: Context, intent: Intent) {
        if (DriveState.isServiceRunning) return
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val inVehicle = result.getActivityConfidence(DetectedActivity.IN_VEHICLE)
        val now = System.currentTimeMillis()
        if (DriveTrigger.shouldStartFromReading(inVehicle, now, TrackingPrefs.falseStartAt(context))) {
            DriveTrackingService.vehicleEntered(context, DriveTrigger.READING)
        }
    }
}
