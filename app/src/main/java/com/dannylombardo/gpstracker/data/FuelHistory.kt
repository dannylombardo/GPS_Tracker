package com.dannylombardo.gpstracker.data

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Every fill-up, organised by month for the All fill-ups page. */
object FuelHistory {
    data class Month(
        val month: YearMonth,
        /** This month's fill-ups, newest first. */
        val fuelUps: List<FuelUp>,
    ) {
        val litres: Double get() = fuelUps.sumOf { it.litres }

        val cost: Double get() = fuelUps.sumOf { it.totalCost }
    }

    fun monthOf(millis: Long, zone: ZoneId): YearMonth = YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))

    /** One entry per month that has fill-ups, newest month first. */
    fun byMonth(fuelUps: List<FuelUp>, zone: ZoneId): List<Month> =
        fuelUps
            .groupBy { monthOf(it.time, zone) }
            .toSortedMap(compareByDescending { it })
            .map { (month, inMonth) -> Month(month, inMonth.sortedByDescending { it.time }) }
}
