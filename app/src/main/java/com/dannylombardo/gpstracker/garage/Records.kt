package com.dannylombardo.gpstracker.garage

import com.dannylombardo.gpstracker.data.FuelUp
import com.dannylombardo.gpstracker.data.Trip
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Personal bests and unlockable badges, from your own drives (not someone else's). */
object Records {
    data class Record<T>(val value: T, val tripId: Long?, val time: Long)

    data class Bests(
        val topSpeed: Record<Double>?,
        val longestDrive: Record<Double>?,
        val longestTime: Record<Long>?,
        val mostKmInADay: Record<Double>?,
        val best0to100: Record<Long>?,
        val best80to120: Record<Long>?,
        val bestScore: Record<Int>?,
        val longestStreakDays: Int,
        val currentStreakDays: Int,
        val totalMeters: Double,
        val totalMillis: Long,
        val driveCount: Int,
    )

    fun bests(trips: List<Trip>, insights: Map<Long, DriveInsight>, zone: ZoneId, today: LocalDate): Bests {
        val mine = trips.filter { it.endTime != null && it.countsAsMine && it.deletedAt == null }
        fun dur(t: Trip) = (t.endTime ?: t.startTime) - t.startTime
        val top = mine.filter { it.topSpeedMetersPerSecond != null }.maxByOrNull { it.topSpeedMetersPerSecond!! }
        val longest = mine.maxByOrNull { it.distanceMeters }
        val longestTime = mine.maxByOrNull(::dur)
        val byDay = mine.groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
        val bestDay = byDay.maxByOrNull { (_, l) -> l.sumOf { it.distanceMeters } }
        val withInsight = mine.mapNotNull { t -> insights[t.id]?.let { t to it } }
        val zeroTo100 = withInsight.filter { it.second.best0to100Millis != null }.minByOrNull { it.second.best0to100Millis!! }
        val passing = withInsight.filter { it.second.best80to120Millis != null }.minByOrNull { it.second.best80to120Millis!! }
        // Only drives of 5 km or more, so a 1 km hop to the shop can't take the crown.
        val score = withInsight.filter { it.second.score != null && it.first.distanceMeters >= 5_000 }
            .maxWithOrNull(compareBy({ it.second.score!! }, { it.first.distanceMeters }))
        val (longestStreak, currentStreak) = streaks(byDay.keys, today)
        return Bests(
            topSpeed = top?.let { Record(it.topSpeedMetersPerSecond!!, it.id, it.startTime) },
            longestDrive = longest?.let { Record(it.distanceMeters, it.id, it.startTime) },
            longestTime = longestTime?.let { Record(dur(it), it.id, it.startTime) },
            mostKmInADay = bestDay?.let { (date, l) ->
                Record(l.sumOf { it.distanceMeters }, null, date.atStartOfDay(zone).toInstant().toEpochMilli())
            },
            best0to100 = zeroTo100?.let { Record(it.second.best0to100Millis!!, it.first.id, it.first.startTime) },
            best80to120 = passing?.let { Record(it.second.best80to120Millis!!, it.first.id, it.first.startTime) },
            bestScore = score?.let { Record(it.second.score!!, it.first.id, it.first.startTime) },
            longestStreakDays = longestStreak,
            currentStreakDays = currentStreak,
            totalMeters = mine.sumOf { it.distanceMeters },
            totalMillis = mine.sumOf(::dur),
            driveCount = mine.size,
        )
    }

    /** Longest run of days in a row with a drive, and the run going on now (today or up to yesterday). */
    fun streaks(days: Set<LocalDate>, today: LocalDate): Pair<Int, Int> {
        if (days.isEmpty()) return 0 to 0
        val sorted = days.sorted()
        var longest = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i - 1].plusDays(1) == sorted[i]) run + 1 else 1
            longest = maxOf(longest, run)
        }
        var current = 0
        var day = if (today in days) today else today.minusDays(1)
        while (day in days) {
            current++
            day = day.minusDays(1)
        }
        return longest to current
    }

    data class Badge(
        val id: String,
        val title: String,
        val description: String,
        val progress: Double,
        val icon: BadgeIcon,
    ) {
        val unlocked: Boolean get() = progress >= 1.0
    }

    enum class BadgeIcon { FLAG, ROAD, SPEED, MOON, SUN, LEAF, FIRE, FUEL, WRENCH, TROPHY, TIMER, GLOBE }

    fun badges(
        trips: List<Trip>,
        insights: Map<Long, DriveInsight>,
        fuelUps: List<FuelUp>,
        maintenanceLogs: Int,
        bests: Bests,
        zone: ZoneId,
    ): List<Badge> {
        val mine = trips.filter { it.endTime != null && it.countsAsMine && it.deletedAt == null }
        val km = bests.totalMeters / 1000
        val topKmh = (bests.topSpeed?.value ?: 0.0) * 3.6
        val nightDrives = mine.count { t ->
            val h = Instant.ofEpochMilli(t.startTime).atZone(zone).hour
            h >= 22 || h < 4
        }
        val earlyDrives = mine.count { t -> Instant.ofEpochMilli(t.startTime).atZone(zone).hour in 4..5 }
        val smooth = mine.count { t -> (insights[t.id]?.score ?: 0) >= 95 && t.distanceMeters >= 20_000 }
        val weekendBest = mine
            .filter {
                val d = Instant.ofEpochMilli(it.startTime).atZone(zone)
                d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            }
            .groupBy {
                val d = Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate()
                d.minusDays((d.dayOfWeek.value - DayOfWeek.SATURDAY.value).toLong())
            }
            .maxOfOrNull { (_, l) -> l.sumOf { it.distanceMeters } / 1000 } ?: 0.0
        val fills = fuelUps.count { it.deletedAt == null }
        fun share(value: Double, goal: Double) = (value / goal).coerceIn(0.0, 1.0)
        return listOf(
            Badge("first", "Keys in the ignition", "Record your first drive", share(mine.size.toDouble(), 1.0), BadgeIcon.FLAG),
            Badge("km100", "Century", "Drive 100 km", share(km, 100.0), BadgeIcon.ROAD),
            Badge("km1000", "Grand tourer", "Drive 1,000 km", share(km, 1_000.0), BadgeIcon.ROAD),
            Badge("km10000", "High miler", "Drive 10,000 km", share(km, 10_000.0), BadgeIcon.GLOBE),
            Badge("roadtrip", "Road trip", "One drive of 300 km or more", share((bests.longestDrive?.value ?: 0.0) / 1000, 300.0), BadgeIcon.ROAD),
            Badge("ton", "Ton up", "Hit 100 km/h", share(topKmh, 100.0), BadgeIcon.SPEED),
            Badge("marathon", "Iron butt", "Two hours behind the wheel in one drive", share((bests.longestTime?.value ?: 0L) / 60_000.0, 120.0), BadgeIcon.TIMER),
            Badge("night", "Night rider", "10 drives starting after 10 PM", share(nightDrives.toDouble(), 10.0), BadgeIcon.MOON),
            Badge("early", "Dawn patrol", "3 drives starting before 6 AM", share(earlyDrives.toDouble(), 3.0), BadgeIcon.SUN),
            Badge("smooth", "Smooth operator", "5 drives of 20 km+ scoring 95 or more", share(smooth.toDouble(), 5.0), BadgeIcon.LEAF),
            Badge("weekend", "Weekend warrior", "200 km in one weekend", share(weekendBest, 200.0), BadgeIcon.FIRE),
            Badge("streak", "Daily driver", "Drive 7 days in a row", share(bests.longestStreakDays.toDouble(), 7.0), BadgeIcon.FIRE),
            Badge("pit", "Pit crew", "Log 10 fill-ups", share(fills.toDouble(), 10.0), BadgeIcon.FUEL),
            Badge("wrench", "Grease monkey", "Log 3 maintenance jobs", share(maintenanceLogs.toDouble(), 3.0), BadgeIcon.WRENCH),
            Badge("regular", "Regular", "Record 100 drives", share(mine.size.toDouble(), 100.0), BadgeIcon.TROPHY),
        )
    }
}
