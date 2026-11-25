package com.dannylombardo.gpstracker.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EveryoneWeekTest {

    private val zone = ZoneId.of("America/Toronto")
    private val weekStart = LocalDate.of(2026, 9, 28) // Monday
    private val civic = Car(id = 1, name = "Civic")
    private val truck = Car(id = 2, name = "Truck")

    private fun trip(id: Long, day: Int, km: Double, isMine: Boolean?, carId: Long): Trip {
        val start = weekStart.plusDays(day.toLong()).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        return Trip(id = id, startTime = start, endTime = start + 600_000, distanceMeters = km * 1000, isMine = isMine, carId = carId)
    }

    @Test
    fun countsEveryoneInEveryCar() {
        val trips = listOf(
            trip(1, 0, 10.0, isMine = true, carId = 1),
            trip(2, 0, 5.0, isMine = null, carId = 2),
            trip(3, 2, 20.0, isMine = false, carId = 1),
            trip(4, 6, 8.0, isMine = false, carId = 2),
        )
        val week = WeeklySummary.of(trips, weekStart, zone)
        val everyone = EveryoneWeek.of(week, listOf(civic, truck), zone)

        assertEquals(43_000.0, everyone.totalMeters, 0.001)
        assertEquals(15_000.0, everyone.mineMeters, 0.001)
        assertEquals(28_000.0, everyone.othersMeters, 0.001)
        assertEquals(2, everyone.mineCount)
        assertEquals(2, everyone.othersCount)
        assertEquals(listOf(15_000.0, 0.0, 20_000.0, 0.0, 0.0, 0.0, 8_000.0), everyone.dailyMeters)
        assertEquals(listOf(civic, truck), everyone.byCar.map { it.car })
        assertEquals(20_000.0, everyone.byCar[0].othersMeters, 0.001)
        assertEquals(5_000.0, everyone.byCar[1].mineMeters, 0.001)
    }

    @Test
    fun emptyWeek() {
        val everyone = EveryoneWeek.of(WeeklySummary.of(emptyList(), weekStart, zone), listOf(civic), zone)
        assertEquals(0.0, everyone.totalMeters, 0.0)
        assertEquals(0.0, everyone.othersShare, 0.0)
        assertEquals(emptyList<EveryoneWeek.CarShare>(), everyone.byCar)
    }
}
