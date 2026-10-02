package com.dannylombardo.gpstracker.tracking

import com.dannylombardo.gpstracker.tracking.TripDistanceTracker.Companion.distanceBetween

/** A place the car sat still during (or at the end of) a drive. */
data class Stop(val latitude: Double, val longitude: Double, val timeMillis: Long, val isDriveEnd: Boolean)

/**
 * Finds where a drive paused long enough to fill up. The route only gets a new point
 * once the car has moved, so a stop shows up as points that stay close together
 * while time passes. The end of the drive always counts as a stop, since filling up
 * with the engine off usually ends the drive right there.
 */
object StopFinder {

    fun find(
        route: List<Fix>,
        minStopMillis: Long = 2 * 60_000L,
        radiusMeters: Double = 50.0,
        maxStops: Int = 8,
    ): List<Stop> {
        if (route.isEmpty()) return emptyList()
        val end = route.last()
        val stops = mutableListOf<Pair<Stop, Long>>()

        var i = 0
        while (i < route.size - 1) {
            val anchor = route[i]
            var j = i
            while (j + 1 < route.size && distanceBetween(anchor, route[j + 1]) <= radiusMeters) j++
            // No new point is saved while parked, so the car sat where the longest gap
            // between saved points begins. Crawling in traffic never leaves a long gap.
            var parkedAt = i
            var parkedMillis = 0L
            for (k in i..minOf(j, route.size - 2)) {
                val gap = route[k + 1].timeMillis - route[k].timeMillis
                if (gap > parkedMillis) {
                    parkedMillis = gap
                    parkedAt = k
                }
            }
            if (parkedMillis >= minStopMillis) {
                val spot = route[parkedAt]
                stops += Stop(spot.latitude, spot.longitude, spot.timeMillis, isDriveEnd = false) to parkedMillis
                i = j + 1
            } else {
                i++
            }
        }

        // Keep the longest pauses if there were a lot of them (a traffic jam, say).
        val longest = stops.sortedByDescending { it.second }.take(maxStops - 1).map { it.first }
        return (longest.sortedBy { it.timeMillis } +
            Stop(end.latitude, end.longitude, end.timeMillis, isDriveEnd = true))
    }
}
