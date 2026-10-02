package com.dannylombardo.gpstracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProfileTest {

    private val metersPerDegreeLat = 111_195.0

    private fun point(northMeters: Double, seconds: Long, speed: Float? = null) = RoutePoint(
        tripId = 1,
        time = seconds * 1000,
        latitude = 45.0 + northMeters / metersPerDegreeLat,
        longitude = -75.0,
        accuracyMeters = 5f,
        speedMetersPerSecond = speed,
    )

    @Test
    fun `splits moving time from a stop at the lights`() {
        val points = listOf(
            point(0.0, 0, 15f),
            point(75.0, 5, 15f),
            point(150.0, 10, 15f),
            // Sat at a red light for a minute, then crept 10 m.
            point(160.0, 70, 1f),
            point(235.0, 75, 15f),
        )

        val profile = RouteProfile.of(points)

        assertEquals(15_000L, profile.movingMillis)
        assertEquals(60_000L, profile.stoppedMillis)
        // The chart drops to zero across the stop.
        assertTrue(profile.speeds.any { it.time == 11_000L && it.metersPerSecond == 0.0 })
        assertTrue(profile.speeds.any { it.time == 69_000L && it.metersPerSecond == 0.0 })
        assertEquals(235.0 / 15.0, profile.movingAverageMetersPerSecond(235.0)!!, 0.01)
    }

    @Test
    fun `falls back to distance over time when GPS has no speed`() {
        val points = listOf(point(0.0, 0), point(100.0, 5), point(200.0, 10))

        val profile = RouteProfile.of(points)

        assertEquals(20.0, profile.speeds.last().metersPerSecond, 0.1)
    }

    @Test
    fun `smooths a single speed spike`() {
        val points = listOf(
            point(0.0, 0, 20f),
            point(100.0, 5, 20f),
            point(200.0, 10, 50f),
            point(300.0, 15, 20f),
            point(400.0, 20, 20f),
        )

        val profile = RouteProfile.of(points)

        assertEquals(30.0, profile.maxSpeedMetersPerSecond!!, 0.01)
    }

    @Test
    fun `a route with one point has no profile`() {
        val profile = RouteProfile.of(listOf(point(0.0, 0)))

        assertTrue(profile.speeds.isEmpty())
        assertEquals(0L, profile.movingMillis)
    }

    @Test
    fun `thinning keeps the first and last points`() {
        val points = (0..999).map { point(it * 10.0, it.toLong()) }

        val thinned = RouteProfile.thin(points, 100)

        assertEquals(100, thinned.size)
        assertEquals(points.first(), thinned.first())
        assertEquals(points.last(), thinned.last())
    }
}
