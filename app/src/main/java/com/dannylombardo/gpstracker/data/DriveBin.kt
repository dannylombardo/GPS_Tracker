package com.dannylombardo.gpstracker.data

import java.util.concurrent.TimeUnit

/** Rules for Recently deleted: how long a drive waits there before it's gone for good. */
object DriveBin {
    const val KEEP_DAYS = 30

    private val keepMillis = TimeUnit.DAYS.toMillis(KEEP_DAYS.toLong())

    /** Drives binned before this moment have run out their time. */
    fun expiryCutoff(now: Long): Long = now - keepMillis

    /** Whole days left before a drive binned at [deletedAt] is removed, at least 1 while it's still there. */
    fun daysLeft(deletedAt: Long, now: Long): Int {
        val remaining = deletedAt + keepMillis - now
        return TimeUnit.MILLISECONDS.toDays(remaining + TimeUnit.DAYS.toMillis(1) - 1)
            .toInt()
            .coerceIn(1, KEEP_DAYS)
    }
}
