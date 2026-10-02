package com.dannylombardo.gpstracker.data

import com.dannylombardo.gpstracker.tracking.Fix
import com.dannylombardo.gpstracker.tracking.TripDistanceTracker
import kotlin.math.roundToInt

/**
 * The shape of one drive over time: a speed trace for the chart, and how long the
 * car was moving versus stopped (lights, traffic, a gas station).
 *
 * GPS only reports a fix after the car has moved about 10 m, so a stop shows up as
 * a long gap between two fixes that are close together. That gap is what counts
 * as stopped time, and the speed trace drops to zero across it.
 */
data class RouteProfile(
    val speeds: List<SpeedSample>,
    val movingMillis: Long,
    val stoppedMillis: Long,
) {
    data class SpeedSample(val time: Long, val metersPerSecond: Double)

    val maxSpeedMetersPerSecond: Double? get() = speeds.maxOfOrNull { it.metersPerSecond }

    /** Distance over the time the car was actually moving. */
    fun movingAverageMetersPerSecond(distanceMeters: Double): Double? =
        if (movingMillis > 0) distanceMeters / (movingMillis / 1000.0) else null

    companion object {
        /** Slower than this between two fixes (about 7 km/h) counts as stopped. */
        const val STOPPED_BELOW_METERS_PER_SECOND = 2.0

        /** Gaps longer than this between fixes are drawn as a stop at zero. */
        private const val GAP_MILLIS = 20_000L

        fun of(points: List<RoutePoint>): RouteProfile {
            val sorted = points.sortedBy { it.time }
            if (sorted.size < 2) return RouteProfile(emptyList(), 0, 0)

            val raw = mutableListOf<SpeedSample>()
            var moving = 0L
            var stopped = 0L
            raw += SpeedSample(sorted.first().time, sorted.first().speedMetersPerSecond?.toDouble() ?: 0.0)
            for (i in 1 until sorted.size) {
                val previous = sorted[i - 1]
                val current = sorted[i]
                val millis = current.time - previous.time
                if (millis <= 0) continue
                val stepSpeed = distanceBetween(previous, current) / (millis / 1000.0)
                if (stepSpeed < STOPPED_BELOW_METERS_PER_SECOND) stopped += millis else moving += millis

                if (millis > GAP_MILLIS && stepSpeed < STOPPED_BELOW_METERS_PER_SECOND) {
                    raw += SpeedSample(previous.time + 1_000, 0.0)
                    raw += SpeedSample(current.time - 1_000, 0.0)
                }
                raw += SpeedSample(current.time, current.speedMetersPerSecond?.toDouble() ?: stepSpeed)
            }
            return RouteProfile(smooth(raw), moving, stopped)
        }

        /** At most [maxPoints] evenly spaced points, first and last included, for a quick route preview. */
        fun thin(points: List<RoutePoint>, maxPoints: Int): List<RoutePoint> {
            if (points.size <= maxPoints || maxPoints < 2) return points
            val step = (points.size - 1).toDouble() / (maxPoints - 1)
            return List(maxPoints) { index -> points[(index * step).roundToInt().coerceAtMost(points.lastIndex)] }
        }

        /** A three-point moving average, so one jumpy GPS speed doesn't spike the chart. */
        private fun smooth(samples: List<SpeedSample>): List<SpeedSample> =
            samples.mapIndexed { index, sample ->
                if (index == 0 || index == samples.lastIndex || sample.metersPerSecond == 0.0) {
                    sample
                } else {
                    val average = (samples[index - 1].metersPerSecond + sample.metersPerSecond +
                        samples[index + 1].metersPerSecond) / 3
                    sample.copy(metersPerSecond = average)
                }
            }

        private fun distanceBetween(a: RoutePoint, b: RoutePoint) =
            TripDistanceTracker.distanceBetween(
                Fix(a.latitude, a.longitude, a.time),
                Fix(b.latitude, b.longitude, b.time),
            )
    }
}
