package com.dannylombardo.gpstracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WeeklySummaryTest {

    private val zone = ZoneId.of("America/Toronto")

    // Monday.
    private val weekStart = LocalDate.of(2026, 9, 28)

    private fun trip(
        id: Long,
        start: LocalDateTime,
        minutes: Long,
        km: Double,
        topKmh: Double? = null,
        isMine: Boolean? = null,
    ): Trip {
        val startMillis = start.atZone(zone).toInstant().toEpochMilli()
        return Trip(
            id = id,
            startTime = startMillis,
            endTime = startMillis + minutes * 60_000,
            distanceMeters = km * 1000,
            topSpeedMetersPerSecond = topKmh?.div(3.6),
            isMine = isMine,
        )
    }

    @Test
    fun weekStartsOnMonday() {
        assertEquals(weekStart, WeeklySummary.weekStartOf(LocalDate.of(2026, 10, 4)))
        assertEquals(weekStart, WeeklySummary.weekStartOf(weekStart))
        assertEquals(LocalDate.of(2026, 10, 5), WeeklySummary.weekStartOf(LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun totalsOnlyCountYourDrivesInTheWeek() {
        val trips = listOf(
            trip(1, LocalDateTime.of(2026, 9, 28, 8, 0), minutes = 30, km = 30.0, topKmh = 100.0, isMine = true),
            trip(2, LocalDateTime.of(2026, 9, 30, 17, 0), minutes = 30, km = 20.0, topKmh = 80.0),
            trip(3, LocalDateTime.of(2026, 10, 1, 12, 0), minutes = 60, km = 90.0, topKmh = 130.0, isMine = false),
            // Sunday night before, and the next Monday: other weeks.
            trip(4, LocalDateTime.of(2026, 9, 27, 23, 30), minutes = 60, km = 50.0, isMine = true),
            trip(5, LocalDateTime.of(2026, 10, 5, 0, 10), minutes = 60, km = 50.0, isMine = true),
        )
        val week = WeeklySummary.of(trips, weekStart, zone)

        assertEquals(listOf(3L, 2L, 1L), week.trips.map { it.id })
        assertEquals(50_000.0, week.distanceMeters, 0.01)
        assertEquals(2, week.driveCount)
        assertEquals(60 * 60_000L, week.drivingMillis)
        assertEquals(50.0, week.averageSpeedMetersPerSecond!! * 3.6, 0.01)
        assertEquals(100.0, week.topSpeedMetersPerSecond!! * 3.6, 0.01)
        assertEquals(1, week.otherDriverCount)
        assertEquals(1, week.unansweredCount)
        assertEquals(listOf(30_000.0, 0.0, 20_000.0, 0.0, 0.0, 0.0, 0.0), week.dailyDistanceMeters)
    }

    @Test
    fun emptyWeekHasNoSpeeds() {
        val week = WeeklySummary.of(emptyList(), weekStart, zone)
        assertEquals(0.0, week.distanceMeters, 0.0)
        assertNull(week.averageSpeedMetersPerSecond)
        assertNull(week.topSpeedMetersPerSecond)
    }

    @Test
    fun averageSpeedOfATripIsDistanceOverTotalTime() {
        val t = trip(1, LocalDateTime.of(2026, 9, 28, 8, 0), minutes = 45, km = 60.0)
        assertEquals(80.0, t.averageSpeedMetersPerSecond!! * 3.6, 0.01)
    }
}
