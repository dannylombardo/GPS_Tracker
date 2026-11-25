package com.dannylombardo.gpstracker.garage

import com.dannylombardo.gpstracker.data.RoutePoint
import com.dannylombardo.gpstracker.data.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class DriveAnalysisTest {
    private val zone = ZoneOffset.UTC
    private val noon = 1_700_000_000_000L - (1_700_000_000_000L % 86_400_000L) + 12 * 3_600_000L

    private fun point(sec: Int, kmh: Double, lat: Double = 45.0 + sec * 0.0001) =
        RoutePoint(tripId = 1, time = noon + sec * 1000L, latitude = lat, longitude = -75.0, accuracyMeters = 5f, speedMetersPerSecond = (kmh / 3.6).toFloat())

    @Test
    fun steadyDriveScoresFull() {
        val points = (0..300 step 3).map { point(it, 60.0) }
        val trip = Trip(id = 1, startTime = noon, endTime = noon + 300_000, distanceMeters = 5_000.0)
        val insight = DriveAnalysis.analyse(trip, points, zone)
        assertEquals(100, insight.score)
        assertEquals(0, insight.hardBrakes)
        assertEquals(0, insight.hardAccels)
        assertEquals(45.0, insight.startLat!!, 1e-9)
    }

    @Test
    fun hardBrakesAreCountedOncePerStop() {
        // 80 to 0 in about 6 s: two harsh readings in a row are one brake.
        val speeds = listOf(80.0, 80.0, 80.0, 55.0, 25.0, 0.0, 0.0, 30.0, 50.0, 60.0, 60.0)
        val points = speeds.mapIndexed { i, v -> point(i * 2, v) }
        val trip = Trip(id = 1, startTime = noon, endTime = noon + 22_000, distanceMeters = 2_000.0)
        val insight = DriveAnalysis.analyse(trip, points, zone)
        assertEquals(1, insight.hardBrakes)
        assertTrue(insight.peakBrakeMs2!! > 3.5)
    }

    @Test
    fun zeroToHundredIsInterpolated() {
        // From a stop, +10 km/h per second: 100 at 10 s.
        val samples = (0..14).map { DriveAnalysis.Sample(it * 1000L, minOf(it * 10.0, 130.0) / 3.6) }
        val millis = DriveAnalysis.bestRun(samples, 0.0, 100.0)
        assertNotNull(millis)
        // Starts from the last reading under 5 km/h (0 s) and reaches 100 at 10 s.
        assertEquals(10_000L, millis!!)
        // 80 to 120 is 4 s at this pace.
        assertEquals(4_000L, DriveAnalysis.bestRun(samples, 80.0, 120.0))
    }

    @Test
    fun liftingOffBreaksTheRun() {
        val kmh = listOf(0.0, 30.0, 60.0, 40.0, 70.0, 100.0)
        val samples = kmh.mapIndexed { i, v -> DriveAnalysis.Sample(i * 2000L, v / 3.6) }
        assertNull(DriveAnalysis.bestRun(samples, 0.0, 100.0))
    }

    @Test
    fun speedingCostsPoints() {
        val calm = DriveAnalysis.score(20_000.0, 900_000, 0, 0, 0, 0)
        val fast = DriveAnalysis.score(20_000.0, 900_000, 0, 0, 300, 0)
        val rough = DriveAnalysis.score(20_000.0, 900_000, 4, 4, 0, 0)
        assertEquals(100, calm)
        assertTrue(fast < 80)
        assertTrue(rough < 90)
    }
}
