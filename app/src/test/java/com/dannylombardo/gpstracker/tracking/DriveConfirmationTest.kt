package com.dannylombardo.gpstracker.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveConfirmationTest {

    private val metersPerDegreeLat = 111_195.0

    private fun fixAt(northMeters: Double, seconds: Long, speed: Float? = null, accuracy: Float? = 8f) =
        Fix(
            latitude = 45.0 + northMeters / metersPerDegreeLat,
            longitude = -75.0,
            timeMillis = seconds * 1000,
            accuracyMeters = accuracy,
            speedMetersPerSecond = speed,
        )

    @Test
    fun walkingNeverConfirms() {
        val check = DriveConfirmation()
        // Ten minutes of brisk walking, 1.6 m/s, fix every 5 s.
        for (i in 0..120) check.add(fixAt(i * 8.0, i * 5L, speed = 1.6f))
        assertFalse(check.isConfirmed)
    }

    @Test
    fun parkedCarWithGpsJitterNeverConfirms() {
        val check = DriveConfirmation()
        for (i in 0..120) {
            val wobble = if (i % 2 == 0) 25.0 else -25.0
            check.add(fixAt(wobble, i * 5L, speed = 0.3f, accuracy = 30f))
        }
        assertFalse(check.isConfirmed)
    }

    @Test
    fun oneSpeedSpikeIsNotEnough() {
        val check = DriveConfirmation()
        check.add(fixAt(0.0, 0, speed = 0.5f))
        check.add(fixAt(5.0, 5, speed = 9f))
        check.add(fixAt(10.0, 10, speed = 0.5f))
        assertFalse(check.isConfirmed)
    }

    @Test
    fun twoFixesAtDrivingSpeedConfirm() {
        val check = DriveConfirmation()
        check.add(fixAt(0.0, 0, speed = 0f))
        check.add(fixAt(40.0, 5, speed = 8f))
        assertTrue(check.add(fixAt(90.0, 10, speed = 10f)))
    }

    @Test
    fun slowTrafficConfirmsByDistance() {
        val check = DriveConfirmation()
        // A 16 km/h crawl: under the speed rule, but 300 m covered at a car-like average.
        var confirmed = false
        for (i in 0..20) confirmed = check.add(fixAt(i * 22.0, i * 5L, speed = 4.4f))
        assertTrue(confirmed)
    }

    @Test
    fun inaccurateFixesAreIgnored() {
        val check = DriveConfirmation()
        check.add(fixAt(0.0, 0, speed = 9f, accuracy = 200f))
        check.add(fixAt(50.0, 5, speed = 9f, accuracy = 200f))
        assertFalse(check.isConfirmed)
    }

    @Test
    fun routeStartsWhereTheCarSetOffNotWhenBluetoothConnected() {
        val check = DriveConfirmation()
        // Bluetooth connects at 0 s; the car sits until 300 s, GPS mostly quiet meanwhile.
        check.add(fixAt(0.0, 0, speed = 0f))
        check.add(fixAt(3.0, 120, speed = 0f))
        check.add(fixAt(60.0, 300, speed = 7f))
        check.add(fixAt(110.0, 305, speed = 10f))
        assertTrue(check.isConfirmed)

        val route = check.routeFixes()
        assertEquals(3, route.size)
        // The last parked fix is kept as the starting point but moved up to 30 s before setting off.
        assertEquals(270_000L, route.first().timeMillis)
        assertEquals(270_000L, check.startTimeMillis(noticedAtMillis = 0))
    }

    @Test
    fun alreadyMovingKeepsEveryFix() {
        val check = DriveConfirmation()
        check.add(fixAt(0.0, 60, speed = 12f))
        check.add(fixAt(60.0, 65, speed = 12f))
        assertTrue(check.isConfirmed)
        assertEquals(2, check.routeFixes().size)
        assertEquals(60_000L, check.startTimeMillis(noticedAtMillis = 40_000))
    }

    @Test
    fun readingsNeedConfidenceAndRespectCooldown() {
        assertFalse(DriveTrigger.shouldStartFromReading(60, nowMillis = 1_000_000, lastFalseStartMillis = 0))
        assertTrue(DriveTrigger.shouldStartFromReading(80, nowMillis = 1_000_000, lastFalseStartMillis = 0))
        // Five minutes after GPS found we weren't driving: wait.
        assertFalse(DriveTrigger.shouldStartFromReading(90, nowMillis = 900_000, lastFalseStartMillis = 600_000))
    }
}
