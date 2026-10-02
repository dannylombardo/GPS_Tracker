package com.dannylombardo.gpstracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelEconomyTest {

    private val hour = 3_600_000L

    private fun fill(id: Long, atHour: Long, litres: Double, full: Boolean = true, price: Double = 1.5) =
        FuelUp(id = id, time = atHour * hour, litres = litres, pricePerLitre = price, isFullTank = full)

    private fun drive(id: Long, atHour: Long, km: Double, isMine: Boolean? = null) =
        Trip(id = id, startTime = atHour * hour, endTime = atHour * hour + hour / 2, distanceMeters = km * 1000, isMine = isMine)

    @Test
    fun noReadingUntilTwoFullTanks() {
        val summary = FuelEconomy.summarise(listOf(fill(1, 0, 40.0)), listOf(drive(1, 1, 300.0)))
        assertTrue(summary.intervals.isEmpty())
        assertNull(summary.averageLitresPer100Km)
        assertEquals(300_000.0, summary.distanceSinceLastFullMeters!!, 0.01)
    }

    @Test
    fun fullToFullUsesLitresOfTheSecondFill() {
        val summary = FuelEconomy.summarise(
            listOf(fill(1, 0, 45.0), fill(2, 10, 32.0)),
            listOf(drive(1, 1, 200.0), drive(2, 5, 200.0), drive(3, 11, 50.0)),
        )
        assertEquals(1, summary.intervals.size)
        assertEquals(8.0, summary.averageLitresPer100Km!!, 1e-9)
        assertEquals(8.0, summary.byFuelUp.getValue(2), 1e-9)
        assertEquals(50_000.0, summary.distanceSinceLastFullMeters!!, 0.01)
    }

    @Test
    fun partFillsAddUpUntilTheNextFullTank() {
        val summary = FuelEconomy.summarise(
            listOf(fill(1, 0, 40.0), fill(2, 5, 10.0, full = false), fill(3, 10, 26.0)),
            listOf(drive(1, 1, 250.0), drive(2, 6, 250.0)),
        )
        assertEquals(1, summary.intervals.size)
        // 36 L over 500 km.
        assertEquals(7.2, summary.averageLitresPer100Km!!, 1e-9)
        assertNull(summary.byFuelUp[2])
    }

    @Test
    fun partFillBeforeFirstFullTankIsIgnored() {
        val summary = FuelEconomy.summarise(
            listOf(fill(1, 0, 20.0, full = false), fill(2, 2, 40.0), fill(3, 10, 30.0)),
            listOf(drive(1, 1, 100.0), drive(2, 3, 400.0)),
        )
        assertEquals(7.5, summary.averageLitresPer100Km!!, 1e-9)
    }

    @Test
    fun drivesBySomeoneElseAreLeftOut() {
        val summary = FuelEconomy.summarise(
            listOf(fill(1, 0, 40.0), fill(2, 10, 20.0)),
            listOf(drive(1, 1, 250.0, isMine = true), drive(2, 2, 300.0, isMine = false), drive(3, 3, 0.0)),
        )
        assertEquals(8.0, summary.averageLitresPer100Km!!, 1e-9)
    }

    @Test
    fun averageWeighsByDistance() {
        val summary = FuelEconomy.summarise(
            listOf(fill(1, 0, 40.0), fill(2, 10, 10.0), fill(3, 20, 60.0)),
            listOf(drive(1, 1, 100.0), drive(2, 11, 500.0)),
        )
        assertEquals(10.0, summary.byFuelUp.getValue(2), 1e-9)
        assertEquals(12.0, summary.byFuelUp.getValue(3), 1e-9)
        // 70 L over 600 km, not the plain mean of 10 and 12.
        assertEquals(70.0 / 6, summary.averageLitresPer100Km!!, 1e-9)
    }

    @Test
    fun stretchWithNoDrivingHasNoReading() {
        val summary = FuelEconomy.summarise(listOf(fill(1, 0, 40.0), fill(2, 1, 2.0)), emptyList())
        assertNull(summary.averageLitresPer100Km)
        assertTrue(summary.byFuelUp.isEmpty())
    }
}
