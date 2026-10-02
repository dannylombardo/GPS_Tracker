package com.dannylombardo.gpstracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedRunsTest {

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
    fun `bands use fixed km per hour edges`() {
        assertEquals(SpeedBand.CRAWLING, SpeedBand.of(9.9 / 3.6))
        assertEquals(SpeedBand.SLOW, SpeedBand.of(10.0 / 3.6))
        assertEquals(SpeedBand.SLOW, SpeedBand.of(49.0 / 3.6))
        assertEquals(SpeedBand.FAST, SpeedBand.of(50.0 / 3.6))
        assertEquals(SpeedBand.FASTEST, SpeedBand.of(90.0 / 3.6))
        assertEquals(SpeedBand.FASTEST, SpeedBand.of(140.0 / 3.6))
    }

    @Test
    fun `splits the route where the speed band changes and keeps it joined`() {
        val points = listOf(
            point(0.0, 0, 8f), // ~29 km/h
            point(40.0, 5, 8f),
            point(140.0, 10, 20f), // ~72 km/h
            point(240.0, 15, 20f),
            point(370.0, 20, 27f), // ~97 km/h
            point(505.0, 25, 27f),
        )

        val runs = SpeedRuns.of(points)

        assertEquals(listOf(SpeedBand.SLOW, SpeedBand.FAST, SpeedBand.FASTEST), runs.map { it.band })
        // Every run starts where the last one ended.
        for (i in 1 until runs.size) {
            assertEquals(runs[i - 1].points.last(), runs[i].points.first())
        }
        assertEquals(points.first(), runs.first().points.first())
        assertEquals(points.last(), runs.last().points.last())
    }

    @Test
    fun `a stop at the lights shows as crawling even if the last gps speed was high`() {
        val points = listOf(
            point(0.0, 0, 15f),
            point(75.0, 5, 15f),
            // A minute sat at a red light, then pulled away.
            point(85.0, 65, 16f),
            point(160.0, 70, 15f),
        )

        val runs = SpeedRuns.of(points)

        assertEquals(listOf(SpeedBand.FAST, SpeedBand.CRAWLING, SpeedBand.FAST), runs.map { it.band })
        assertTrue(runs[1].points.size == 2)
    }

    @Test
    fun `falls back to distance over time without gps speed`() {
        val runs = SpeedRuns.of(listOf(point(0.0, 0), point(150.0, 5)))
        assertEquals(listOf(SpeedBand.FASTEST), runs.map { it.band })
    }

    @Test
    fun `needs two points`() {
        assertTrue(SpeedRuns.of(listOf(point(0.0, 0, 10f))).isEmpty())
    }
}
