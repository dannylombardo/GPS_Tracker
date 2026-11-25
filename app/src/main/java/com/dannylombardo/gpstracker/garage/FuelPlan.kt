package com.dannylombardo.gpstracker.garage

import com.dannylombardo.gpstracker.data.FuelUp
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** The monthly fuel budget, how far a tank has left, and what your own receipts say about prices. */
object FuelPlan {
    data class Budget(
        val month: YearMonth,
        val budget: Double,
        val spent: Double,
        /** Where spending lands by month end at this month's pace. */
        val projected: Double,
        val daysLeft: Int,
    ) {
        val progress: Double get() = if (budget > 0) spent / budget else 0.0
        val left: Double get() = budget - spent
        val overPace: Boolean get() = projected > budget
    }

    fun budget(fuelUps: List<FuelUp>, monthlyBudget: Double, today: java.time.LocalDate, zone: ZoneId): Budget {
        val month = YearMonth.from(today)
        val spent = fuelUps.filter { it.deletedAt == null && YearMonth.from(Instant.ofEpochMilli(it.time).atZone(zone)) == month }
            .sumOf { it.totalCost }
        val day = today.dayOfMonth
        val days = month.lengthOfMonth()
        // Early in the month one fill-up says little about pace, so don't project from it yet.
        val projected = if (day >= 7) spent / day * days else maxOf(spent, monthlyBudget * day / days)
        return Budget(month, monthlyBudget, spent, projected, days - day)
    }

    data class Range(
        val litresLeft: Double,
        val kmLeft: Double,
        val tankShare: Double,
    )

    /**
     * Roughly how much is left in the tank: a full tank, minus what the kilometres driven
     * since used at the car's usual L/100km. Null without a tank size, a full fill-up, or
     * a known consumption.
     */
    fun range(tankLitres: Double?, litresPer100Km: Double?, kmSinceLastFull: Double?): Range? {
        if (tankLitres == null || tankLitres <= 0 || litresPer100Km == null || litresPer100Km <= 0 || kmSinceLastFull == null) return null
        val used = kmSinceLastFull * litresPer100Km / 100
        val left = (tankLitres - used).coerceAtLeast(0.0)
        return Range(left, left / litresPer100Km * 100, (left / tankLitres).coerceIn(0.0, 1.0))
    }

    data class PricePoint(val time: Long, val pricePerLitre: Double)

    data class Prices(
        val history: List<PricePoint>,
        val latest: Double?,
        val average: Double?,
        val lowest: PricePoint?,
        val highest: PricePoint?,
        /** The weekday you've paid least on, from at least two fill-ups on each day compared. */
        val cheapestDay: DayOfWeek?,
        val cheapestDaySaving: Double?,
    ) {
        /** How the last fill-up compared with your average: +0.05 is 5% dearer. */
        val latestVsAverage: Double? get() = if (latest != null && average != null && average > 0) latest / average - 1 else null
    }

    fun prices(fuelUps: List<FuelUp>, zone: ZoneId): Prices {
        val list = fuelUps.filter { it.deletedAt == null && it.pricePerLitre > 0 }.sortedBy { it.time }
        val history = list.map { PricePoint(it.time, it.pricePerLitre) }
        val litres = list.sumOf { it.litres }
        val average = if (litres > 0) list.sumOf { it.totalCost } / litres else null
        val byDay = list.groupBy { Instant.ofEpochMilli(it.time).atZone(zone).dayOfWeek }
            .filterValues { it.size >= 2 }
            .mapValues { (_, l) -> l.map { it.pricePerLitre }.average() }
        val cheapest = if (byDay.size >= 2) byDay.minByOrNull { it.value } else null
        val dearest = if (byDay.size >= 2) byDay.maxByOrNull { it.value } else null
        return Prices(
            history = history,
            latest = list.lastOrNull()?.pricePerLitre,
            average = average,
            lowest = history.minByOrNull { it.pricePerLitre },
            highest = history.maxByOrNull { it.pricePerLitre },
            cheapestDay = cheapest?.key,
            cheapestDaySaving = if (cheapest != null && dearest != null) dearest.value - cheapest.value else null,
        )
    }
}
