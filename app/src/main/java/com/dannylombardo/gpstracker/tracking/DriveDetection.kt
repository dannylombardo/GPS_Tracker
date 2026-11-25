package com.dannylombardo.gpstracker.tracking

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.tasks.await

/**
 * Subscribes to Android's activity recognition, which uses the low-power motion
 * sensors to tell when we get into or out of a vehicle. GPS stays off until then.
 *
 * Two feeds come in:
 * - transitions ("entered a vehicle", "left it"), which are reliable but Android waits
 *   until it's quite sure, often a minute or two into the drive, and
 * - a reading every [SAMPLE_INTERVAL_MS], so a drive can start as soon as the sensors
 *   lean strongly towards "in a vehicle". GPS then double-checks before anything is saved.
 */
object DriveDetection {
    private const val TAG = "DriveDetection"
    private const val SAMPLE_INTERVAL_MS = 30_000L

    private fun pendingIntent(context: Context, requestCode: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ActivityTransitionReceiver::class.java),
            // Must be mutable: Play services fills in the transition result.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    private fun samplesIntent(context: Context) = pendingIntent(context, requestCode = 1)

    private val request = ActivityTransitionRequest(
        listOf(
            transition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_EXIT),
            transition(DetectedActivity.WALKING, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
        ),
    )

    private fun transition(activity: Int, type: Int) =
        ActivityTransition.Builder().setActivityType(activity).setActivityTransition(type).build()

    @SuppressLint("MissingPermission")
    suspend fun start(context: Context): Boolean {
        if (!Permissions.hasActivityRecognition(context)) return false
        val client = ActivityRecognition.getClient(context)
        return try {
            client.requestActivityTransitionUpdates(request, pendingIntent(context)).await()
            try {
                client.requestActivityUpdates(SAMPLE_INTERVAL_MS, samplesIntent(context)).await()
            } catch (e: Exception) {
                // Transitions alone still work, just a little slower to notice a drive.
                Log.w(TAG, "Could not register for activity readings", e)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not register for activity transitions", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun stop(context: Context) {
        if (!Permissions.hasActivityRecognition(context)) return
        val client = ActivityRecognition.getClient(context)
        try {
            client.removeActivityTransitionUpdates(pendingIntent(context)).await()
        } catch (e: Exception) {
            Log.w(TAG, "Could not unregister activity transitions", e)
        }
        try {
            client.removeActivityUpdates(samplesIntent(context)).await()
        } catch (e: Exception) {
            Log.w(TAG, "Could not unregister activity readings", e)
        }
    }
}
