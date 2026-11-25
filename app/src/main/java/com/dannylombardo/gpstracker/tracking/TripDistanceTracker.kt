package com.dannylombardo.gpstracker.tracking

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A location fix, kept free of Android types so the distance logic is unit-testable. */
data class Fix(
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long,
    val accuracyMeters: Float? = null,
    val speedMetersPerSecond: Float? = null,
)

/**
 * Turns a stream of GPS fixes into a trip distance, ignoring the noise that would
 * otherwise inflate it: inaccurate fixes, jitter while stopped, and impossible jumps.
 * It also tracks when the car last moved, so a drive can be ended if Android never
 * reports that we left the vehicle, and the drive's top speed.
 */
class TripDistanceTracker(
    startTimeMillis: Long,
    private val maxAccuracyMeters: Float = 50f,
    private val minStepMeters: Double = 15.0,
    private val maxSpeedMetersPerSecond: Double = 70.0,
    private val movementRadiusMeters: Double = 75.0,
    private val movingSpeedMetersPerSecond: Float = 3f,
) {
    var distanceMeters: Double = 0.0
        private set

    var lastMovementMillis: Long = startTimeMillis
        private set

    /**
     * Highest speed held across two fixes in a row. A single fix isn't enough,
     * because GPS speed occasionally spikes for one reading.
     */
    var topSpeedMetersPerSecond: Double = 0.0
        private set

    private var lastCounted: Fix? = null
    private var anchor: Fix? = null
    private var previousSpeed: Double? = null

    /** Continues a trip that was already partly recorded. */
    fun restore(distanceMeters: Double, lastFix: Fix?, topSpeedMetersPerSecond: Double = 0.0) {
        this.distanceMeters = distanceMeters
        this.topSpeedMetersPerSecond = topSpeedMetersPerSecond
        lastCounted = lastFix
        anchor = lastFix
        if (lastFix != null) lastMovementMillis = max(lastMovementMillis, lastFix.timeMillis)
    }

    /** Returns true when [fix] counted towards the distance and should be saved to the route. */
    fun add(fix: Fix): Boolean {
        if (fix.accuracyMeters != null && fix.accuracyMeters > maxAccuracyMeters) return false

        val previous = lastCounted
        if (previous == null) {
            recordSpeed(fix.speedMetersPerSecond?.toDouble())
            lastCounted = fix
            anchor = fix
            lastMovementMillis = max(lastMovementMillis, fix.timeMillis)
            return true
        }

        val seconds = (fix.timeMillis - previous.timeMillis) / 1000.0
        if (seconds <= 0) return false
        val step = distanceBetween(previous, fix)
        if (step / seconds > maxSpeedMetersPerSecond) return false

        val speed = fix.speedMetersPerSecond
        recordSpeed(speed?.toDouble() ?: if (step >= minStepMeters) step / seconds else null)

        // Parked indoors or in a garage, GPS wanders by tens of metres while reporting
        // no speed at all. A jump that's within the fixes' own error and comes with
        // "not moving" is noise, not driving: it mustn't add distance or keep the drive alive.
        val noiseMeters = (fix.accuracyMeters ?: 0f) + (previous.accuracyMeters ?: 0f)
        val standingStill = speed != null && speed < STILL_SPEED_METERS_PER_SECOND
        fun isJitter(meters: Double) = standingStill && meters < noiseMeters

        val currentAnchor = anchor ?: fix
        val fromAnchor = distanceBetween(currentAnchor, fix)
        if ((fromAnchor >= movementRadiusMeters && !isJitter(fromAnchor)) ||
            (speed != null && speed >= movingSpeedMetersPerSecond)
        ) {
            anchor = fix
            lastMovementMillis = max(lastMovementMillis, fix.timeMillis)
        }

        if (step < minStepMeters || isJitter(step)) return false
        distanceMeters += step
        lastCounted = fix
        return true
    }

    private fun recordSpeed(speed: Double?) {
        if (speed == null || speed > maxSpeedMetersPerSecond) {
            previousSpeed = null
            return
        }
        previousSpeed?.let { topSpeedMetersPerSecond = max(topSpeedMetersPerSecond, min(it, speed)) }
        previousSpeed = speed
    }

    fun isStationary(nowMillis: Long, timeoutMillis: Long): Boolean =
        nowMillis - lastMovementMillis >= timeoutMillis

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_008.8
        private const val STILL_SPEED_METERS_PER_SECOND = 1f

        /** Great-circle (haversine) distance in metres. */
        fun distanceBetween(a: Fix, b: Fix): Double {
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val dLat = lat2 - lat1
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
            return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }
}
