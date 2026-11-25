package com.dannylombardo.gpstracker.tracking

/**
 * The drive being recorded right now, for the live screen and notification.
 * [recentSpeeds] holds the last couple of minutes of speed readings (m/s) for the trace.
 */
data class LiveDrive(
    val tripId: Long,
    val startTime: Long,
    val distanceMeters: Double,
    val speedMetersPerSecond: Double?,
    val topSpeedMetersPerSecond: Double,
    val lastFixTime: Long?,
    val recentSpeeds: List<Pair<Long, Float>>,
) {
    val averageSpeedMetersPerSecond: Double?
        get() {
            val seconds = ((lastFixTime ?: startTime) - startTime) / 1000.0
            return if (seconds > 10) distanceMeters / seconds else null
        }

    companion object {
        const val TRACE_MILLIS = 120_000L

        fun trimmed(speeds: List<Pair<Long, Float>>, now: Long): List<Pair<Long, Float>> =
            speeds.filter { now - it.first <= TRACE_MILLIS }
    }
}
