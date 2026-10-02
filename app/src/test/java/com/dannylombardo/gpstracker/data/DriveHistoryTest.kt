package com.dannylombardo.gpstracker.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DriveHistoryTest {

    private val zone = ZoneId.of("America/Toronto")

    private fun trip(id: Long, start: LocalDateTime, km: Double, isMine: Boolean? = null, finished: Boolean = true): Trip {
        val startMillis = start.atZone(zone).toInstant().toEpochMilli()
        return Trip(
            id = id,
            startTime = startMillis,
            endTime = if (finished) startMillis + 20 * 60_000 else null,
            distanceMeters = km * 1000,
            isMine = isMine,
        )
    }

    @Test
    fun groupsByWeekNewestFirst() {
        val trips = listOf(
            trip(1, LocalDateTime.of(2026, 10, 2, 8, 0), 10.0),
            trip(2, LocalDateTime.of(2026, 9, 28, 0, 30), 5.0),
            trip(3, LocalDateTime.of(2026, 9, 27, 23, 30), 7.0),
            trip(4, LocalDateTime.of(2026, 9, 1, 12, 0), 3.0),
        )

        val weeks = DriveHistory.byWeek(trips, zone)

        assertEquals(
            listOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 8, 31)),
            weeks.map { it.weekStart },
        )
        assertEquals(listOf(1L, 2L), weeks[0].trips.map { it.id })
        assertEquals(15_000.0, weeks[0].distanceMeters, 0.001)
        assertEquals(listOf(3L), weeks[1].trips.map { it.id })
    }

    @Test
    fun someoneElsesDrivesAreListedButNotCounted() {
        val trips = listOf(
            trip(1, LocalDateTime.of(2026, 10, 1, 8, 0), 10.0, isMine = true),
            trip(2, LocalDateTime.of(2026, 10, 1, 17, 0), 40.0, isMine = false),
        )

        val week = DriveHistory.byWeek(trips, zone).single()

        assertEquals(2, week.trips.size)
        assertEquals(1, week.driveCount)
        assertEquals(10_000.0, week.distanceMeters, 0.001)
    }

    @Test
    fun leavesOutDrivesStillBeingRecorded() {
        val trips = listOf(trip(1, LocalDateTime.of(2026, 10, 1, 8, 0), 10.0, finished = false))

        assertEquals(emptyList<WeeklySummary>(), DriveHistory.byWeek(trips, zone))
    }
}
