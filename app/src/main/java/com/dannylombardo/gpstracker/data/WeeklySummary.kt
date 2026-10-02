package com.dannylombardo.gpstracker.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** What was driven in one Monday-to-Sunday week. Only drives that count as yours go into the totals. */
data class WeeklySummary(
    val weekStart: LocalDate,
    /** Every finished drive that started this week, yours or not, newest first. */
    val trips: List<Trip>,
    val distanceMeters: Double,
    val driveCount: Int,
    val drivingMillis: Long,
    val topSpeedMetersPerSecond: Double?,
    /** Kilometres driven each day, Monday first. */
    val dailyDistanceMeters: List<Double>,
    /** Drives marked as someone else's, left out of the totals above. */
    val otherDriverCount: Int,
    /** Drives nobody has answered "who was driving?" for yet. */
    val unansweredCount: Int,
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)

    /** Total distance over total driving time. */
    val averageSpeedMetersPerSecond: Double?
        get() = if (drivingMillis > 0) distanceMeters / (drivingMillis / 1000.0) else null

    companion object {
        fun weekStartOf(date: LocalDate): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /** Summarises the drives in the week starting [weekStart], placing each drive by its start time. */
        fun of(trips: List<Trip>, weekStart: LocalDate, zone: ZoneId): WeeklySummary {
            val from = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
            val until = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val inWeek = trips
                .filter { it.endTime != null && it.startTime in from until until }
                .sortedByDescending { it.startTime }
            val mine = inWeek.filter { it.countsAsMine }

            val daily = MutableList(7) { 0.0 }
            mine.forEach { trip ->
                val day = Instant.ofEpochMilli(trip.startTime).atZone(zone).dayOfWeek
                daily[day.value - 1] += trip.distanceMeters
            }

            return WeeklySummary(
                weekStart = weekStart,
                trips = inWeek,
                distanceMeters = mine.sumOf { it.distanceMeters },
                driveCount = mine.size,
                drivingMillis = mine.sumOf { (it.endTime ?: it.startTime) - it.startTime },
                topSpeedMetersPerSecond = mine.mapNotNull { it.topSpeedMetersPerSecond }.maxOrNull(),
                dailyDistanceMeters = daily,
                otherDriverCount = inWeek.count { it.isMine == false },
                unansweredCount = inWeek.count { it.isMine == null },
            )
        }
    }
}
