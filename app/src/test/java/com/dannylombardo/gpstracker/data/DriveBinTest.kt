package com.dannylombardo.gpstracker.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class DriveBinTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_790_000_000_000L

    @Test
    fun justDeletedHasThirtyDaysLeft() {
        assertEquals(30, DriveBin.daysLeft(deletedAt = now, now = now))
    }

    @Test
    fun partDaysRoundUp() {
        assertEquals(30, DriveBin.daysLeft(deletedAt = now - day / 2, now = now))
        assertEquals(29, DriveBin.daysLeft(deletedAt = now - day - 1, now = now))
        assertEquals(1, DriveBin.daysLeft(deletedAt = now - 29 * day - day / 2, now = now))
    }

    @Test
    fun neverShowsLessThanOneDayWhileStillThere() {
        assertEquals(1, DriveBin.daysLeft(deletedAt = now - 31 * day, now = now))
    }

    @Test
    fun expiresThirtyDaysAfterDeleting() {
        assertEquals(now - 30 * day, DriveBin.expiryCutoff(now))
    }
}
