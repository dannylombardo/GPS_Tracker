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
 */
object DriveDetection {
    private const val TAG = "DriveDetection"

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ActivityTransitionReceiver::class.java),
            // Must be mutable: Play services fills in the transition result.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

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
        return try {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(request, pendingIntent(context))
                .await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not register for activity transitions", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun stop(context: Context) {
        if (!Permissions.hasActivityRecognition(context)) return
        try {
            ActivityRecognition.getClient(context)
                .removeActivityTransitionUpdates(pendingIntent(context))
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Could not unregister activity transitions", e)
        }
    }
}
