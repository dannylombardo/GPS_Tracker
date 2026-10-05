package com.dannylombardo.gpstracker.data

import java.time.LocalDate
import java.time.ZoneId

/**
 * What was driven and spent on one day, counted the same way as [WeeklySummary]: only
 * drives that count as yours go into the driving totals, and every fill-up that day
 * counts towards money spent.
 */
data class DailySummary(
    val date: LocalDate,
    /** Every finished drive that started this day, yours or not, earliest first. */
    val trips: List<Trip>,
    val distanceMeters: Double,
    val driveCount: Int,
    val drivingMillis: Long,
    val topSpeedMetersPerSecond: Double?,
    /** Drives marked as someone else's, left out of the totals above. */
    val otherDriverCount: Int,
    /** Fill-ups this day, earliest first. */
    val fuelUps: List<FuelUp>,
) {
    val moneySpent: Double get() = fuelUps.sumOf { it.totalCost }

    val litresBought: Double get() = fuelUps.sumOf { it.litres }

    /** Total distance over total driving time. */
    val averageSpeedMetersPerSecond: Double?
        get() = if (drivingMillis > 0) distanceMeters / (drivingMillis / 1000.0) else null

    companion object {
        /** Summarises [date], placing each drive by its start time. */
        fun of(trips: List<Trip>, date: LocalDate, zone: ZoneId, fuelUps: List<FuelUp> = emptyList()): DailySummary {
            val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val until = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val onDay = trips
                .filter { it.endTime != null && it.startTime in from until until }
                .sortedBy { it.startTime }
            val mine = onDay.filter { it.countsAsMine }
            return DailySummary(
                date = date,
                trips = onDay,
                distanceMeters = mine.sumOf { it.distanceMeters },
                driveCount = mine.size,
                drivingMillis = mine.sumOf { (it.endTime ?: it.startTime) - it.startTime },
                topSpeedMetersPerSecond = mine.mapNotNull { it.topSpeedMetersPerSecond }.maxOrNull(),
                otherDriverCount = onDay.count { it.isMine == false },
                fuelUps = fuelUps.filter { it.time in from until until }.sortedBy { it.time },
            )
        }
    }
}
