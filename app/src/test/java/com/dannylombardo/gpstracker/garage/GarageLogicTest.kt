package com.dannylombardo.gpstracker.garage

import com.dannylombardo.gpstracker.data.Car
import com.dannylombardo.gpstracker.data.FuelUp
import com.dannylombardo.gpstracker.data.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class GarageLogicTest {
    private val zone = ZoneOffset.UTC
    private val day = 86_400_000L
    private val t0 = LocalDate.of(2026, 3, 2).atStartOfDay(zone).toInstant().toEpochMilli() // a Monday

    private fun trip(id: Long, start: Long, km: Double, carId: Long = 1, mine: Boolean? = null, minutes: Long = 20) =
        Trip(id = id, startTime = start, endTime = start + minutes * 60_000, distanceMeters = km * 1000, carId = carId, isMine = mine)

    @Test
    fun maintenanceCountsKmSinceLastDoneInThatCarOnly() {
        val item = MaintenanceItem(id = 1, carId = 1, name = "Oil", intervalKm = 1_000, intervalMonths = null, lastDoneAt = t0)
        val trips = listOf(
            trip(1, t0 - day, 500.0), // before: ignored
            trip(2, t0 + day, 400.0),
            trip(3, t0 + 2 * day, 500.0, mine = false), // someone else drove: the car still wears
            trip(4, t0 + 2 * day, 900.0, carId = 2), // other car
        )
        val s = Maintenance.status(item, trips, t0 + 3 * day, zone)
        assertEquals(900.0, s.kmSince, 1e-9)
        assertEquals(0.9, s.progress!!, 1e-9)
        assertEquals(Maintenance.State.SOON, s.state)
        assertEquals(100.0, s.kmLeft!!, 1e-9)
    }

    @Test
    fun timeIntervalCanMakeAJobDue() {
        val item = MaintenanceItem(id = 1, carId = 1, name = "Wipers", intervalMonths = 1, lastDoneAt = t0)
        val s = Maintenance.status(item, emptyList(), t0 + 40 * day, zone)
        assertEquals(Maintenance.State.DUE, s.state)
        assertTrue(s.daysLeft!! < 0)
    }

    @Test
    fun odometerAddsDrivesAfterTheReading() {
        val spec = CarSpec(carId = 1, odometerKm = 50_000.0, odometerSetAt = t0)
        val trips = listOf(trip(1, t0 - day, 100.0), trip(2, t0 + day, 42.0))
        assertEquals(50_042.0, Maintenance.odometerKm(spec, trips, 1), 1e-9)
        assertEquals(142.0, Maintenance.odometerKm(null, trips, 1), 1e-9)
    }

    @Test
    fun rangeFromTankAndConsumption() {
        val r = FuelPlan.range(50.0, 8.0, 300.0)!!
        assertEquals(26.0, r.litresLeft, 1e-9)
        assertEquals(325.0, r.kmLeft, 1e-9)
        assertNull(FuelPlan.range(null, 8.0, 300.0))
    }

    @Test
    fun budgetProjectsFromThePaceSoFar() {
        val fills = listOf(FuelUp(id = 1, time = t0 + 5 * day, litres = 40.0, pricePerLitre = 1.5)) // 60 in March
        val b = FuelPlan.budget(fills, 200.0, LocalDate.of(2026, 3, 10), zone)
        assertEquals(60.0, b.spent, 1e-9)
        assertEquals(60.0 / 10 * 31, b.projected, 1e-9)
        assertEquals(21, b.daysLeft)
        assertEquals(0.3, b.progress, 1e-9)
    }

    @Test
    fun cheapestWeekdayNeedsTwoFillUpsEach() {
        val fills = listOf(
            FuelUp(id = 1, time = t0, litres = 10.0, pricePerLitre = 1.40), // Monday
            FuelUp(id = 2, time = t0 + 7 * day, litres = 10.0, pricePerLitre = 1.42),
            FuelUp(id = 3, time = t0 + 4 * day, litres = 10.0, pricePerLitre = 1.55), // Friday
            FuelUp(id = 4, time = t0 + 11 * day, litres = 10.0, pricePerLitre = 1.57),
        )
        val p = FuelPlan.prices(fills, zone)
        assertEquals(java.time.DayOfWeek.MONDAY, p.cheapestDay)
        assertEquals(0.15, p.cheapestDaySaving!!, 1e-9)
        assertEquals(1.57, p.latest!!, 1e-9)
    }

    @Test
    fun placesFindHomeWorkAndTheCommute() {
        val home = 45.0 to -75.0
        val work = 45.05 to -75.05
        val trips = mutableListOf<Trip>()
        val insights = mutableMapOf<Long, DriveInsight>()
        var id = 1L
        for (d in 0 until 5) {
            val morning = t0 + d * day + 8 * 3_600_000L
            val evening = t0 + d * day + 17 * 3_600_000L
            trips += trip(id, morning, 8.0, minutes = 20L + d)
            insights[id] = DriveInsight(id, 1, home.first, home.second, work.first + 0.0005, work.second)
            id++
            trips += trip(id, evening, 8.0, minutes = 25)
            insights[id] = DriveInsight(id, 1, work.first, work.second, home.first, home.second + 0.0005)
            id++
        }
        val s = Places.summarise(trips, insights, emptyList(), zone)
        assertEquals(2, s.places.size)
        assertEquals("Home", s.home!!.name)
        assertEquals("Work", s.work!!.name)
        val toWork = s.toWork!!
        assertEquals(5, toWork.trips)
        assertEquals(20 * 60_000L, toWork.fastestMillis)
        assertEquals(24 * 60_000L, toWork.slowestMillis)
    }

    @Test
    fun savedNameWins() {
        val trips = (1..3L).map { trip(it, t0 + it * day + 12 * 3_600_000L, 3.0) }
        val insights = trips.associate { it.id to DriveInsight(it.id, 1, 10.0, 10.0, 46.0, 7.0) }
        val saved = listOf(SavedPlace(id = 9, name = "Track", latitude = 46.0005, longitude = 7.0, kind = PlaceKind.OTHER.name))
        val s = Places.summarise(trips, insights, saved, zone)
        assertTrue(s.places.any { it.name == "Track" && it.savedId == 9L })
    }

    @Test
    fun streaksCountDaysInARow() {
        val today = LocalDate.of(2026, 3, 10)
        val days = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(5), today.minusDays(6), today.minusDays(7), today.minusDays(8))
        assertEquals(4 to 3, Records.streaks(days, today))
        assertEquals(0 to 0, Records.streaks(emptySet(), today))
    }

    @Test
    fun bestsIgnoreSomeoneElsesDrives() {
        val trips = listOf(
            trip(1, t0, 10.0).copy(topSpeedMetersPerSecond = 30.0),
            trip(2, t0 + day, 300.0, mine = false).copy(topSpeedMetersPerSecond = 50.0),
        )
        val b = Records.bests(trips, emptyMap(), zone, LocalDate.of(2026, 3, 3))
        assertEquals(30.0, b.topSpeed!!.value, 1e-9)
        assertEquals(10_000.0, b.longestDrive!!.value, 1e-9)
        val badges = Records.badges(trips, emptyMap(), emptyList(), 0, b, zone)
        assertTrue(badges.first { it.id == "first" }.unlocked)
        assertTrue(!badges.first { it.id == "roadtrip" }.unlocked)
    }

    @Test
    fun monthRecapTotalsAndComparesWithTheMonthBefore() {
        val feb = LocalDate.of(2026, 2, 10).atStartOfDay(zone).toInstant().toEpochMilli()
        val trips = listOf(trip(1, feb, 100.0), trip(2, t0, 150.0), trip(3, t0 + day, 50.0), trip(4, t0, 999.0, mine = false))
        val fills = listOf(FuelUp(id = 1, time = t0, litres = 30.0, pricePerLitre = 2.0))
        val r = Recap.of(Recap.Period.Month(java.time.YearMonth.of(2026, 3)), trips, fills, emptyMap(), null, zone)
        assertEquals(200_000.0, r.distanceMeters, 1e-9)
        assertEquals(2, r.driveCount)
        assertEquals(1.0, r.changeVsPrevious!!, 1e-9)
        assertEquals(60.0, r.moneySpent, 1e-9)
        assertEquals(31, r.buckets.size)
        assertEquals(150_000.0, r.buckets[1], 1e-9)
    }

    @Test
    fun csvEscapesAndListsDrives() {
        assertEquals("\"a, \"\"b\"\"\"", CsvExport.escape("a, \"b\""))
        val csv = CsvExport.drives(listOf(trip(1, t0, 12.345)), listOf(Car(1, "Civic, red")), emptyMap(), zone)
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("2026-03-02,00:00,00:20,\"Civic, red\",Me,12.35,20.0"))
    }
}
