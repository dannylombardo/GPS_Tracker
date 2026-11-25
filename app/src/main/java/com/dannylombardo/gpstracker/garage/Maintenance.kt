package com.dannylombardo.gpstracker.garage

import com.dannylombardo.gpstracker.data.Trip
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How each maintenance job is doing: kilometres and time since it was last done. */
object Maintenance {
    enum class State { OK, SOON, DUE }

    /** Due soon from this share of the interval. */
    const val SOON_AT = 0.85

    data class Status(
        val item: MaintenanceItem,
        val kmSince: Double,
        val daysSince: Long,
        /** How far through its interval it is: 1.0 means due now. Null without an interval. */
        val progress: Double?,
        val kmLeft: Double?,
        val daysLeft: Long?,
    ) {
        val state: State
            get() = when {
                progress == null -> State.OK
                progress >= 1.0 -> State.DUE
                progress >= SOON_AT -> State.SOON
                else -> State.OK
            }
    }

    /**
     * Drives in [carId] since [since], whoever drove: the car wears the same either way.
     */
    fun kmSince(trips: List<Trip>, carId: Long, since: Long): Double =
        trips.filter { it.carId == carId && it.endTime != null && it.startTime >= since && it.deletedAt == null }
            .sumOf { it.distanceMeters } / 1000

    fun status(item: MaintenanceItem, trips: List<Trip>, now: Long, zone: ZoneId): Status {
        val km = kmSince(trips, item.carId, item.lastDoneAt)
        val done = Instant.ofEpochMilli(item.lastDoneAt).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone)
        val days = ChronoUnit.DAYS.between(done.toLocalDate(), today.toLocalDate()).coerceAtLeast(0)
        val kmShare = item.intervalKm?.takeIf { it > 0 }?.let { km / it }
        val dueDate = item.intervalMonths?.takeIf { it > 0 }?.let { done.toLocalDate().plusMonths(it.toLong()) }
        val totalDays = dueDate?.let { ChronoUnit.DAYS.between(done.toLocalDate(), it).coerceAtLeast(1) }
        val timeShare = totalDays?.let { days.toDouble() / it }
        val progress = listOfNotNull(kmShare, timeShare).maxOrNull()
        return Status(
            item = item,
            kmSince = km,
            daysSince = days,
            progress = progress,
            kmLeft = item.intervalKm?.let { it - km },
            daysLeft = dueDate?.let { ChronoUnit.DAYS.between(today.toLocalDate(), it) },
        )
    }

    /** Most urgent first. */
    fun statuses(items: List<MaintenanceItem>, trips: List<Trip>, now: Long, zone: ZoneId): List<Status> =
        items.map { status(it, trips, now, zone) }.sortedByDescending { it.progress ?: -1.0 }

    /**
     * What the dash reads now: the reading you entered plus everything driven in the car
     * since, or just the tracked kilometres if you never entered one.
     */
    fun odometerKm(spec: CarSpec?, trips: List<Trip>, carId: Long): Double {
        val base = spec?.odometerKm
        val since = if (base != null) spec.odometerSetAt ?: 0L else 0L
        return (base ?: 0.0) + kmSince(trips, carId, since)
    }
}
