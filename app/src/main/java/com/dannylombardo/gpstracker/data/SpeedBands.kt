package com.dannylombardo.gpstracker.data

import com.dannylombardo.gpstracker.tracking.Fix
import com.dannylombardo.gpstracker.tracking.TripDistanceTracker

/**
 * Fixed speed ranges for colouring a route on the map. They don't scale to the
 * drive, so a green stretch means the same thing on every drive.
 */
enum class SpeedBand(val fromKmh: Int, val label: String) {
    CRAWLING(0, "<10"),
    SLOW(10, "10–50"),
    FAST(50, "50–90"),
    FASTEST(90, "90+"),
    ;

    companion object {
        fun of(metersPerSecond: Double): SpeedBand {
            val kmh = metersPerSecond * 3.6
            return entries.last { kmh >= it.fromKmh }
        }
    }
}

/** A stretch of route, [points] in order, driven within one [band]. */
data class SpeedRun(val band: SpeedBand, val points: List<RoutePoint>)

object SpeedRuns {
    /** Gaps longer than this between fixes are judged by distance over time, like the chart does. */
    private const val GAP_MILLIS = 20_000L

    /**
     * Splits the route into runs of one speed band. Each step between two fixes takes
     * the average GPS speed at its ends; across a long gap (a stop, where GPS goes
     * quiet) it uses distance over time instead, so the stop shows as crawling.
     * Neighbouring runs share their joining point so the line has no breaks.
     */
    fun of(points: List<RoutePoint>): List<SpeedRun> {
        val sorted = points.sortedBy { it.time }
        if (sorted.size < 2) return emptyList()

        val runs = mutableListOf<SpeedRun>()
        var band: SpeedBand? = null
        var current = mutableListOf(sorted.first())
        for (i in 1 until sorted.size) {
            val stepBand = SpeedBand.of(stepSpeed(sorted[i - 1], sorted[i]))
            if (band != null && stepBand != band) {
                runs += SpeedRun(band, current)
                current = mutableListOf(sorted[i - 1])
            }
            band = stepBand
            current += sorted[i]
        }
        runs += SpeedRun(band!!, current)
        return runs
    }

    private fun stepSpeed(a: RoutePoint, b: RoutePoint): Double {
        val millis = b.time - a.time
        val travelled = if (millis > 0) {
            TripDistanceTracker.distanceBetween(
                Fix(a.latitude, a.longitude, a.time),
                Fix(b.latitude, b.longitude, b.time),
            ) / (millis / 1000.0)
        } else {
            0.0
        }
        val aSpeed = a.speedMetersPerSecond
        val bSpeed = b.speedMetersPerSecond
        return when {
            millis > GAP_MILLIS -> travelled
            aSpeed != null && bSpeed != null -> (aSpeed + bSpeed) / 2.0
            else -> bSpeed?.toDouble() ?: aSpeed?.toDouble() ?: travelled
        }
    }
}
