package com.dannylombardo.gpstracker.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDistanceTrackerTest {

    // Roughly 0.0009 degrees of latitude is 100 m.
    private val metersPerDegreeLat = 111_195.0

    private fun fixAt(northMeters: Double, seconds: Long, accuracy: Float? = 5f, speed: Float? = null) =
        Fix(
            latitude = 45.0 + northMeters / metersPerDegreeLat,
            longitude = -75.0,
            timeMillis = seconds * 1000,
            accuracyMeters = accuracy,
            speedMetersPerSecond = speed,
        )

    @Test
    fun haversineMatchesKnownDistance() {
        // Ottawa to Montreal city centres, about 166 km apart.
        val ottawa = Fix(45.4215, -75.6972, 0)
        val montreal = Fix(45.5019, -73.5674, 0)
        assertEquals(166_000.0, TripDistanceTracker.distanceBetween(ottawa, montreal), 2_000.0)
    }

    @Test
    fun sumsDistanceAlongAStraightDrive() {
        val tracker = TripDistanceTracker(startTimeMillis = 0)
        for (i in 0..10) tracker.add(fixAt(northMeters = i * 100.0, seconds = i * 5L))
        assertEquals(1_000.0, tracker.distanceMeters, 5.0)
    }

    @Test
    fun ignoresInaccurateFixes() {
        val tracker = TripDistanceTracker(startTimeMillis = 0)
        tracker.add(fixAt(0.0, 0))
        assertFalse(tracker.add(fixAt(500.0, 10, accuracy = 120f)))
        assertEquals(0.0, tracker.distanceMeters, 0.0)
    }

    @Test
    fun ignoresJitterWhileStopped() {
        val tracker = TripDistanceTracker(startTimeMillis = 0)
        tracker.add(fixAt(0.0, 0))
        for (i in 1..60) tracker.add(fixAt(if (i % 2 == 0) 6.0 else -6.0, i * 5L))
        assertEquals(0.0, tracker.distanceMeters, 0.0)
    }

    @Test
    fun rejectsImpossibleJumps() {
        val tracker = TripDistanceTracker(startTimeMillis = 0)
        tracker.add(fixAt(0.0, 0))
        // 2 km in 5 seconds is 1440 km/h.
        assertFalse(tracker.add(fixAt(2_000.0, 5)))
        assertTrue(tracker.add(fixAt(100.0, 10)))
        assertEquals(100.0, tracker.distanceMeters, 1.0)
    }

    @Test
    fun slowCreepStillAddsUpOnceBeyondMinimumStep() {
        val tracker = TripDistanceTracker(startTimeMillis = 0)
        for (i in 0..20) tracker.add(fixAt(i * 5.0, i * 5L))
        // 100 m of crawling traffic in 5 m steps is counted in 15 m chunks.
        assertTrue(tracker.distanceMeters in 90.0..100.5)
    }

    @Test
    fun detectsWhenTheCarHasBeenStillForAWhile() {
        val tracker = TripDistanceTracker(startTimeMillis = 0)
        tracker.add(fixAt(0.0, 0))
        tracker.add(fixAt(500.0, 30))
        for (i in 1..20) tracker.add(fixAt(503.0, 30 + i * 30L))
        val tenMinutes = 10 * 60_000L
        assertFalse(tracker.isStationary(nowMillis = 30_000 + tenMinutes - 1, timeoutMillis = tenMinutes))
        assertTrue(tracker.isStationary(nowMillis = 30_000 + tenMinutes, timeoutMillis = tenMinutes))
    }

    @Test
    fun reportedSpeedCountsAsMovement() {
        val tracker = TripDistanceTracker(startTimeMillis = 0)
        tracker.add(fixAt(0.0, 0))
        tracker.add(fixAt(10.0, 600, speed = 8f))
        assertEquals(600_000L, tracker.lastMovementMillis)
    }

    @Test
    fun restoreContinuesFromSavedDistance() {
        val tracker = TripDistanceTracker(startTimeMillis = 1_000_000)
        tracker.restore(distanceMeters = 4_000.0, lastFix = fixAt(0.0, 900))
        tracker.add(fixAt(200.0, 920))
        assertEquals(4_200.0, tracker.distanceMeters, 2.0)
    }
}
