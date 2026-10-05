package com.dannylombardo.gpstracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DailySummaryTest {

    private val zone = ZoneId.of("America/Toronto")
    private val day = LocalDate.of(2026, 10, 1)

    private fun millis(at: LocalDateTime) = at.atZone(zone).toInstant().toEpochMilli()

    private fun trip(id: Long, start: LocalDateTime, minutes: Long, km: Double, topKmh: Double? = null, isMine: Boolean? = null): Trip {
        val startMillis = millis(start)
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
    fun `totals only that day's drives that count as yours`() {
        val trips = listOf(
            trip(1, day.atTime(8, 0), 30, 20.0, topKmh = 90.0),
            trip(2, day.atTime(17, 30), 30, 25.0, topKmh = 110.0, isMine = true),
            trip(3, day.atTime(12, 0), 10, 5.0, topKmh = 140.0, isMine = false),
            trip(4, day.minusDays(1).atTime(23, 50), 20, 15.0),
            trip(5, day.plusDays(1).atTime(0, 5), 20, 15.0),
        )

        val summary = DailySummary.of(trips, day, zone)

        assertEquals(listOf(1L, 3L, 2L), summary.trips.map { it.id })
        assertEquals(45_000.0, summary.distanceMeters, 0.001)
        assertEquals(2, summary.driveCount)
        assertEquals(60 * 60_000L, summary.drivingMillis)
        assertEquals(110.0, summary.topSpeedMetersPerSecond!! * 3.6, 0.001)
        assertEquals(45_000.0 / 3600, summary.averageSpeedMetersPerSecond!!, 0.001)
        assertEquals(1, summary.otherDriverCount)
    }

    @Test
    fun `money spent is that day's fill-ups`() {
        val fuelUps = listOf(
            FuelUp(id = 1, time = millis(day.atTime(9, 0)), litres = 40.0, pricePerLitre = 1.5),
            FuelUp(id = 2, time = millis(day.plusDays(1).atTime(9, 0)), litres = 30.0, pricePerLitre = 1.5),
        )

        val summary = DailySummary.of(emptyList(), day, zone, fuelUps)

        assertEquals(60.0, summary.moneySpent, 0.001)
        assertEquals(40.0, summary.litresBought, 0.001)
        assertNull(summary.averageSpeedMetersPerSecond)
        assertNull(summary.topSpeedMetersPerSecond)
    }

    @Test
    fun `unfinished drives are left out`() {
        val live = Trip(id = 1, startTime = millis(day.atTime(10, 0)))

        assertEquals(0, DailySummary.of(listOf(live), day, zone).trips.size)
    }
}
