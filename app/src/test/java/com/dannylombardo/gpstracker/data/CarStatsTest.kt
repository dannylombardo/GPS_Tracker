package com.dannylombardo.gpstracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarStatsTest {
    private val civic = Car(id = 1, name = "Civic")
    private val truck = Car(id = 2, name = "Truck")

    private fun trip(start: Long, km: Double, carId: Long, isMine: Boolean? = null) =
        Trip(id = start, startTime = start, endTime = start + 1, distanceMeters = km * 1000, isMine = isMine, carId = carId)

    private fun fill(id: Long, time: Long, litres: Double, carId: Long, full: Boolean = true) =
        FuelUp(id = id, time = time, litres = litres, pricePerLitre = 2.0, isFullTank = full, carId = carId)

    @Test
    fun `each car's L per 100km uses only its own fill-ups and drives`() {
        val trips = listOf(
            trip(start = 10, km = 100.0, carId = 1),
            trip(start = 20, km = 300.0, carId = 2),
            trip(start = 30, km = 100.0, carId = 1),
        )
        val fuelUps = listOf(
            fill(id = 1, time = 0, litres = 40.0, carId = 1),
            fill(id = 2, time = 5, litres = 50.0, carId = 2),
            fill(id = 3, time = 100, litres = 14.0, carId = 1),
            fill(id = 4, time = 100, litres = 30.0, carId = 2),
        )

        val byCar = CarStats.economyByCar(listOf(civic, truck), fuelUps, trips)

        assertEquals(7.0, byCar.getValue(1).averageLitresPer100Km!!, 1e-9)
        assertEquals(10.0, byCar.getValue(2).averageLitresPer100Km!!, 1e-9)
    }

    @Test
    fun `a car with no fill-ups has no economy yet`() {
        val byCar = CarStats.economyByCar(listOf(civic, truck), listOf(fill(1, 0, 40.0, carId = 1)), emptyList())
        assertNull(byCar.getValue(2).averageLitresPer100Km)
    }

    @Test
    fun `all cars totals add up every car and skip someone else's drives`() {
        val trips = listOf(
            trip(start = 10, km = 10.0, carId = 1),
            trip(start = 20, km = 20.0, carId = 2),
            trip(start = 30, km = 99.0, carId = 2, isMine = false),
        )
        val fuelUps = listOf(fill(1, 0, 10.0, carId = 1), fill(2, 0, 5.0, carId = 2))

        val all = CarStats.totals(CarStats.tripsFor(trips, null), CarStats.fuelUpsFor(fuelUps, null))
        assertEquals(30_000.0, all.distanceMeters, 1e-9)
        assertEquals(2, all.driveCount)
        assertEquals(30.0, all.moneySpent, 1e-9)

        val truckOnly = CarStats.totals(CarStats.tripsFor(trips, 2), CarStats.fuelUpsFor(fuelUps, 2))
        assertEquals(20_000.0, truckOnly.distanceMeters, 1e-9)
        assertEquals(1, truckOnly.driveCount)
        assertEquals(10.0, truckOnly.moneySpent, 1e-9)
    }

    @Test
    fun `distance by car splits a week between cars`() {
        val trips = listOf(trip(10, 10.0, carId = 1), trip(20, 5.0, carId = 2), trip(30, 2.5, carId = 1))
        val split = CarStats.distanceByCar(listOf(civic, truck), trips)
        assertEquals(listOf(civic to 12_500.0, truck to 5_000.0), split)
    }
}
