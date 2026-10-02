package com.dannylombardo.gpstracker.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopFinderTest {

    private val metersPerDegreeLat = 111_195.0

    private fun at(northMeters: Double, seconds: Long) =
        Fix(latitude = 45.0 + northMeters / metersPerDegreeLat, longitude = -75.0, timeMillis = seconds * 1000)

    /** Driving north at 20 m/s with a fix every 5 s. */
    private fun driving(fromMeters: Double, fromSeconds: Long, count: Int) =
        (0 until count).map { at(fromMeters + it * 100.0, fromSeconds + it * 5L) }

    @Test
    fun driveEndIsAlwaysAStop() {
        val route = driving(0.0, 0, 10)
        val stops = StopFinder.find(route)
        assertEquals(1, stops.size)
        assertTrue(stops.single().isDriveEnd)
        assertEquals(route.last().timeMillis, stops.single().timeMillis)
    }

    @Test
    fun longPauseMidDriveIsAStop() {
        // Pull in, sit at the pump for 5 minutes, creep 20 m forward, drive off.
        val before = driving(0.0, 0, 10)
        val pump = at(920.0, 50)
        val crept = at(940.0, 350)
        val after = driving(1_040.0, 355, 10)
        val stops = StopFinder.find(before + pump + crept + after)
        assertEquals(2, stops.size)
        assertEquals(pump.timeMillis, stops[0].timeMillis)
        assertEquals(pump.latitude, stops[0].latitude, 1e-9)
        assertTrue(!stops[0].isDriveEnd)
        assertTrue(stops[1].isDriveEnd)
    }

    @Test
    fun crawlingInTrafficIsNotAStop() {
        // 15 m every 20 s for 4 minutes: slow, but always moving.
        val crawl = (0 until 12).map { at(it * 15.0, it * 20L) }
        val stops = StopFinder.find(crawl + driving(400.0, 240, 5))
        assertEquals(1, stops.size)
    }

    @Test
    fun shortPauseIsNotAStop() {
        val route = driving(0.0, 0, 5) + driving(500.0, 80, 5)
        assertEquals(1, StopFinder.find(route).size)
    }

    @Test
    fun keepsOnlyTheLongestPauses() {
        val route = mutableListOf<Fix>()
        var seconds = 0L
        repeat(12) { i ->
            route += at(i * 1_000.0, seconds)
            // Pauses of 3, 4, ... 14 minutes.
            seconds += (3 + i) * 60L
        }
        val stops = StopFinder.find(route, maxStops = 4)
        assertEquals(4, stops.size)
        assertTrue(stops.last().isDriveEnd)
        // The three longest pauses come right before the end, in time order.
        assertEquals(listOf(route[8], route[9], route[10]).map { it.timeMillis }, stops.dropLast(1).map { it.timeMillis })
    }
}
