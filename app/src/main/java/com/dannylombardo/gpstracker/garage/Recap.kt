package com.dannylombardo.gpstracker.garage

import com.dannylombardo.gpstracker.data.FuelEconomy
import com.dannylombardo.gpstracker.data.FuelUp
import com.dannylombardo.gpstracker.data.Trip
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A Wrapped-style look back at one month or one year of your driving. */
data class Recap(
    val period: Period,
    val distanceMeters: Double,
    val driveCount: Int,
    val drivingMillis: Long,
    val topSpeed: Double?,
    val topSpeedTime: Long?,
    val longestDriveMeters: Double?,
    val longestDriveTime: Long?,
    val busiestDay: DayOfWeek?,
    val favouriteHour: Int?,
    val activeDays: Int,
    val moneySpent: Double,
    val litres: Double,
    val fillUps: Int,
    val litresPer100Km: Double?,
    val averageScore: Int?,
    val hardBrakes: Int,
    val nightDrives: Int,
    val topPlace: String?,
    val previousDistanceMeters: Double?,
    /** Kilometres per month across a year, or per day across a month, for the little chart. */
    val buckets: List<Double>,
) {
    sealed interface Period {
        val title: String
        val start: LocalDate
        val end: LocalDate

        data class Month(val month: YearMonth) : Period {
            override val title get() = month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " " + month.year
            override val start: LocalDate get() = month.atDay(1)
            override val end: LocalDate get() = month.atEndOfMonth().plusDays(1)
        }

        data class Year(val year: Int) : Period {
            override val title get() = year.toString()
            override val start: LocalDate get() = LocalDate.of(year, 1, 1)
            override val end: LocalDate get() = LocalDate.of(year + 1, 1, 1)
        }
    }

    val averageSpeed: Double? get() = if (drivingMillis > 0) distanceMeters / (drivingMillis / 1000.0) else null

    /** How much further (positive) or shorter than the period before, as a share. */
    val changeVsPrevious: Double?
        get() = previousDistanceMeters?.takeIf { it > 0 }?.let { distanceMeters / it - 1 }

    /** Laps of the Earth at the equator. */
    val earthLaps: Double get() = distanceMeters / 40_075_000.0

    companion object {
        fun of(
            period: Period,
            trips: List<Trip>,
            fuelUps: List<FuelUp>,
            insights: Map<Long, DriveInsight>,
            places: Places.Summary?,
            zone: ZoneId,
        ): Recap {
            val from = period.start.atStartOfDay(zone).toInstant().toEpochMilli()
            val until = period.end.atStartOfDay(zone).toInstant().toEpochMilli()
            val mine = trips.filter { it.endTime != null && it.countsAsMine && it.deletedAt == null }
            val inside = mine.filter { it.startTime in from until until }
            fun dur(t: Trip) = (t.endTime ?: t.startTime) - t.startTime
            fun zdt(t: Trip) = Instant.ofEpochMilli(t.startTime).atZone(zone)
            val top = inside.filter { it.topSpeedMetersPerSecond != null }.maxByOrNull { it.topSpeedMetersPerSecond!! }
            val longest = inside.maxByOrNull { it.distanceMeters }
            val fills = fuelUps.filter { it.deletedAt == null && it.time in from until until }
            // Each car's tanks are worked out with that car's drives only.
            val intervals = fuelUps.filter { it.deletedAt == null }.groupBy { it.carId }
                .flatMap { (carId, list) -> FuelEconomy.summarise(list, trips.filter { it.carId == carId }).intervals }
                .filter { it.endTime in from until until && it.distanceMeters > 0 }
            val l100 = if (intervals.isNotEmpty()) {
                intervals.sumOf { it.litres } / (intervals.sumOf { it.distanceMeters } / 100_000)
            } else {
                null
            }
            val scores = inside.mapNotNull { insights[it.id]?.score }
            val previous = when (period) {
                is Period.Month -> Period.Month(period.month.minusMonths(1))
                is Period.Year -> Period.Year(period.year - 1)
            }
            val pFrom = previous.start.atStartOfDay(zone).toInstant().toEpochMilli()
            val pDistance = mine.filter { it.startTime in pFrom until from }.sumOf { it.distanceMeters }
            val buckets = when (period) {
                is Period.Year -> List(12) { m -> inside.filter { zdt(it).monthValue == m + 1 }.sumOf { it.distanceMeters } }
                is Period.Month -> List(period.month.lengthOfMonth()) { d -> inside.filter { zdt(it).dayOfMonth == d + 1 }.sumOf { it.distanceMeters } }
            }
            val topPlace = places?.places
                ?.filter { it.kind == PlaceKind.OTHER }
                ?.maxByOrNull { p ->
                    inside.count { t ->
                        val i = insights[t.id] ?: return@count false
                        i.endLat != null && i.endLon != null &&
                            DriveAnalysis.metersBetween(i.endLat, i.endLon, p.latitude, p.longitude) <= Places.RADIUS_M
                    }
                }?.name
            return Recap(
                period = period,
                distanceMeters = inside.sumOf { it.distanceMeters },
                driveCount = inside.size,
                drivingMillis = inside.sumOf(::dur),
                topSpeed = top?.topSpeedMetersPerSecond,
                topSpeedTime = top?.startTime,
                longestDriveMeters = longest?.distanceMeters,
                longestDriveTime = longest?.startTime,
                busiestDay = inside.groupBy { zdt(it).dayOfWeek }.maxByOrNull { (_, l) -> l.sumOf { it.distanceMeters } }?.key,
                favouriteHour = inside.groupBy { zdt(it).hour }.maxByOrNull { it.value.size }?.key,
                activeDays = inside.map { zdt(it).toLocalDate() }.toSet().size,
                moneySpent = fills.sumOf { it.totalCost },
                litres = fills.sumOf { it.litres },
                fillUps = fills.size,
                litresPer100Km = l100,
                averageScore = if (scores.isNotEmpty()) scores.average().toInt() else null,
                hardBrakes = inside.sumOf { insights[it.id]?.hardBrakes ?: 0 },
                nightDrives = inside.count { zdt(it).hour >= 22 || zdt(it).hour < 4 },
                topPlace = topPlace,
                previousDistanceMeters = if (mine.any { it.startTime < from }) pDistance else null,
                buckets = buckets,
            )
        }

        /** Months and years that have any drives, newest first. */
        fun periodsWithDrives(trips: List<Trip>, zone: ZoneId): Pair<List<YearMonth>, List<Int>> {
            val months = trips.filter { it.endTime != null && it.countsAsMine && it.deletedAt == null }
                .map { YearMonth.from(Instant.ofEpochMilli(it.startTime).atZone(zone)) }
                .distinct().sortedDescending()
            return months to months.map { it.year }.distinct()
        }
    }
}
