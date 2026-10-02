package com.dannylombardo.gpstracker.data

import java.time.Instant
import java.time.ZoneId

/** Every past drive, organised for the All drives page. */
object DriveHistory {
    /** One summary per week that has drives, newest week first. */
    fun byWeek(trips: List<Trip>, zone: ZoneId): List<WeeklySummary> =
        trips
            .filter { it.endTime != null }
            .groupBy { WeeklySummary.weekStartOf(Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate()) }
            .toSortedMap(compareByDescending { it })
            .map { (weekStart, inWeek) -> WeeklySummary.of(inWeek, weekStart, zone) }
}
