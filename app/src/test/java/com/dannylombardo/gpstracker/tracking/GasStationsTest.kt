package com.dannylombardo.gpstracker.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GasStationsTest {

    private val metersPerDegreeLat = 111_195.0

    private fun stopAt(northMeters: Double, isDriveEnd: Boolean = false) =
        Stop(45.0 + northMeters / metersPerDegreeLat, -75.0, 0, isDriveEnd)

    private fun stationAt(northMeters: Double, name: String? = "Esso") =
        GasStation(name, 45.0 + northMeters / metersPerDegreeLat, -75.0)

    @Test
    fun queryAsksAroundEveryStop() {
        val query = GasStations.query(listOf(stopAt(0.0), stopAt(1_000.0)))
        assertTrue(query.startsWith("[out:csv(::lat,::lon,name,brand;false)]"))
        assertEquals(2, Regex("amenity\"=\"fuel\"\\]\\(around:80,").findAll(query).count())
        assertTrue(query.contains("45.000000,-75.000000"))
        assertTrue(query.endsWith("out center;"))
    }

    @Test
    fun parsesOverpassCsv() {
        val csv = "43.6510000\t-79.3800000\tShell Queen St\tShell\n" +
            "43.6520000\t-79.3810000\t\tPetro-Canada\n" +
            "43.6530000\t-79.3820000\t\t\n" +
            "not a row\n"
        val stations = GasStations.parseCsv(csv)
        assertEquals(3, stations.size)
        assertEquals("Shell Queen St", stations[0].name)
        assertEquals("Petro-Canada", stations[1].name)
        assertNull(stations[2].name)
        assertEquals(43.651, stations[0].latitude, 1e-9)
    }

    @Test
    fun matchesOnlyStationsCloseToAStop() {
        assertNull(GasStations.match(listOf(stopAt(0.0, isDriveEnd = true)), listOf(stationAt(150.0))))
        val visit = GasStations.match(listOf(stopAt(0.0, isDriveEnd = true)), listOf(stationAt(40.0)))
        assertEquals("Esso", visit?.station?.name)
    }

    @Test
    fun driveEndWinsOverACloserMidDriveStop() {
        val midDrive = stopAt(0.0)
        val end = stopAt(5_000.0, isDriveEnd = true)
        val visit = GasStations.match(
            listOf(midDrive, end),
            listOf(stationAt(5.0, "Near mid"), stationAt(5_060.0, "At end")),
        )
        assertEquals("At end", visit?.station?.name)
        assertEquals(end, visit?.stop)
    }
}
