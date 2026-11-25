package com.dannylombardo.gpstracker.tracking

import kotlin.math.max

/**
 * Checks a possible drive with GPS before anything is saved.
 *
 * Phone motion sensors sometimes think we're in a car when we aren't (a brisk walk,
 * a bumpy bike, the phone bouncing in a bag), and a car's Bluetooth can connect while
 * it's still parked. So a drive only becomes real once GPS shows car-like movement:
 * - two fixes in a row at [confirmSpeed] or more (about 20 km/h), or
 * - [minDisplacementMeters] away from where we started, at [minAverageSpeed] or more on
 *   average (covers slow traffic and phones that don't report speed).
 *
 * Every fix is kept, so once a drive is confirmed its route still starts where
 * the car actually set off, not where confirmation happened.
 */
class DriveConfirmation(
    private val confirmSpeed: Float = 5.5f,
    private val minDisplacementMeters: Double = 300.0,
    private val minAverageSpeed: Double = 4.0,
    private val maxAccuracyMeters: Float = 50f,
    private val maxSpeedMetersPerSecond: Float = 70f,
    private val stillRadiusMeters: Double = 40.0,
    private val movingSpeed: Float = 2f,
    private val maxLeadInMillis: Long = 30_000,
) {
    private val fixes = mutableListOf<Fix>()
    private var fastInARow = 0

    var isConfirmed: Boolean = false
        private set

    /** Adds a fix and returns true once the drive is confirmed. */
    fun add(fix: Fix): Boolean {
        if (fix.accuracyMeters != null && fix.accuracyMeters > maxAccuracyMeters) return isConfirmed
        val previous = fixes.lastOrNull()
        if (previous != null && fix.timeMillis <= previous.timeMillis) return isConfirmed
        fixes += fix
        if (isConfirmed) return true

        val speed = fix.speedMetersPerSecond ?: previous?.let { impliedSpeed(it, fix) }?.toFloat()
        fastInARow = if (speed != null && speed >= confirmSpeed && speed <= maxSpeedMetersPerSecond) {
            fastInARow + 1
        } else {
            0
        }
        if (fastInARow >= 2) isConfirmed = true

        val start = fixes[startIndex()]
        val seconds = (fix.timeMillis - start.timeMillis) / 1000.0
        val away = TripDistanceTracker.distanceBetween(start, fix)
        if (seconds > 0 && away >= minDisplacementMeters && away / seconds >= minAverageSpeed &&
            away / seconds <= maxSpeedMetersPerSecond
        ) {
            isConfirmed = true
        }
        return isConfirmed
    }

    /**
     * The fix where the car set off: the last one still at the first fix's spot before
     * it started moving. Waiting in a parked car (engine warming, Bluetooth connected)
     * doesn't count as part of the drive.
     */
    private fun startIndex(): Int {
        if (fixes.isEmpty()) return 0
        val first = fixes.first()
        val firstMoving = fixes.indexOfFirst { fix ->
            TripDistanceTracker.distanceBetween(first, fix) >= stillRadiusMeters ||
                (fix.speedMetersPerSecond ?: 0f) >= movingSpeed
        }
        return when {
            firstMoving <= 0 -> 0
            else -> firstMoving - 1
        }
    }

    /** When the drive began, given when the possible drive was first noticed. */
    fun startTimeMillis(noticedAtMillis: Long): Long = routeFixes().firstOrNull()?.timeMillis ?: noticedAtMillis

    /**
     * The fixes that belong to the drive, from where the car set off. GPS goes quiet
     * while parked, so a long gap before the first moving fix is trimmed to
     * [maxLeadInMillis] rather than counted as driving time.
     */
    fun routeFixes(): List<Fix> {
        if (fixes.isEmpty()) return emptyList()
        val start = startIndex()
        val route = fixes.subList(start, fixes.size).toMutableList()
        val next = route.getOrNull(1)
        if (start > 0 && next != null && next.timeMillis - route[0].timeMillis > maxLeadInMillis) {
            route[0] = route[0].copy(timeMillis = max(route[0].timeMillis, next.timeMillis - maxLeadInMillis))
        }
        return route
    }

    private fun impliedSpeed(a: Fix, b: Fix): Double? {
        val seconds = (b.timeMillis - a.timeMillis) / 1000.0
        if (seconds <= 0 || seconds > 30) return null
        return TripDistanceTracker.distanceBetween(a, b) / seconds
    }
}
