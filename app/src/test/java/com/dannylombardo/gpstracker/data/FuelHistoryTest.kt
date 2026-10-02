package com.dannylombardo.gpstracker.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class FuelHistoryTest {

    private val zone = ZoneId.of("America/Toronto")

    private fun fuelUp(id: Long, time: LocalDateTime, litres: Double, price: Double) = FuelUp(
        id = id,
        time = time.atZone(zone).toInstant().toEpochMilli(),
        litres = litres,
        pricePerLitre = price,
    )

    @Test
    fun groupsByMonthNewestFirstWithTotals() {
        val fuelUps = listOf(
            fuelUp(1, LocalDateTime.of(2026, 9, 3, 9, 0), 40.0, 1.50),
            fuelUp(2, LocalDateTime.of(2026, 10, 1, 18, 0), 30.0, 1.60),
            fuelUp(3, LocalDateTime.of(2026, 9, 30, 23, 30), 20.0, 1.40),
            fuelUp(4, LocalDateTime.of(2026, 7, 15, 12, 0), 10.0, 1.00),
        )

        val months = FuelHistory.byMonth(fuelUps, zone)

        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9), YearMonth.of(2026, 7)), months.map { it.month })
        assertEquals(listOf(3L, 1L), months[1].fuelUps.map { it.id })
        assertEquals(60.0, months[1].litres, 0.001)
        assertEquals(88.0, months[1].cost, 0.001)
    }

    @Test
    fun noFillUpsMeansNoMonths() {
        assertEquals(emptyList<FuelHistory.Month>(), FuelHistory.byMonth(emptyList(), zone))
    }
}
