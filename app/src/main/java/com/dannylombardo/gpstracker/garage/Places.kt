package com.dannylombardo.gpstracker.garage

import com.dannylombardo.gpstracker.data.Trip
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * Finds the places you drive to and from most (by grouping where drives start and end)
 * and your commute between home and work. Nothing leaves the phone.
 */
object Places {
    /** Drive ends closer than this belong to the same place. */
    const val RADIUS_M = 250.0

    /** A spot needs this many visits to count as a regular place. */
    const val MIN_VISITS = 3

    data class Place(
        val latitude: Double,
        val longitude: Double,
        val visits: Int,
        val name: String,
        val kind: PlaceKind,
        val savedId: Long?,
        /** Kilometres of driving that started or ended here. */
        val distanceMeters: Double,
        val lastVisit: Long,
    )

    data class Commute(
        val trips: Int,
        val averageMillis: Long,
        val averageMeters: Double,
        val fastestMillis: Long,
        val slowestMillis: Long,
        /** The hour you leave that gets you there quickest, when there's enough data. */
        val bestDepartureHour: Int?,
        val bestDepartureMillis: Long?,
    )

    data class Summary(
        val places: List<Place>,
        val toWork: Commute?,
        val toHome: Commute?,
    ) {
        val home: Place? get() = places.firstOrNull { it.kind == PlaceKind.HOME }
        val work: Place? get() = places.firstOrNull { it.kind == PlaceKind.WORK }
    }

    private class Cluster(var lat: Double, var lon: Double) {
        var visits = 0
        var distance = 0.0
        var last = 0L
        var homeVotes = 0
        var workVotes = 0

        fun add(lat: Double, lon: Double) {
            visits++
            this.lat += (lat - this.lat) / visits
            this.lon += (lon - this.lon) / visits
        }
    }

    fun summarise(trips: List<Trip>, insights: Map<Long, DriveInsight>, saved: List<SavedPlace>, zone: ZoneId): Summary {
        val clusters = mutableListOf<Cluster>()
        fun clusterFor(lat: Double, lon: Double): Cluster {
            val near = clusters.minByOrNull { DriveAnalysis.metersBetween(it.lat, it.lon, lat, lon) }
            if (near != null && DriveAnalysis.metersBetween(near.lat, near.lon, lat, lon) <= RADIUS_M) return near
            return Cluster(lat, lon).also { clusters += it }
        }

        val sorted = trips.filter { it.endTime != null }.sortedBy { it.startTime }
        for (trip in sorted) {
            val insight = insights[trip.id] ?: continue
            val start = Instant.ofEpochMilli(trip.startTime).atZone(zone)
            val end = Instant.ofEpochMilli(trip.endTime ?: trip.startTime).atZone(zone)
            val weekday = start.dayOfWeek != DayOfWeek.SATURDAY && start.dayOfWeek != DayOfWeek.SUNDAY
            if (insight.startLat != null && insight.startLon != null) {
                val c = clusterFor(insight.startLat, insight.startLon)
                c.add(insight.startLat, insight.startLon)
                c.distance += trip.distanceMeters
                c.last = maxOf(c.last, trip.startTime)
                // Leaving in the morning: probably home.
                if (start.hour in 5..10) c.homeVotes++
                // Leaving in the late afternoon on a weekday: probably work.
                if (weekday && start.hour in 15..19) c.workVotes++
            }
            if (insight.endLat != null && insight.endLon != null) {
                val c = clusterFor(insight.endLat, insight.endLon)
                c.add(insight.endLat, insight.endLon)
                c.distance += trip.distanceMeters
                c.last = maxOf(c.last, trip.endTime ?: trip.startTime)
                // Arriving in the evening or at night: probably home.
                if (end.hour >= 18 || end.hour < 3) c.homeVotes++
                // Arriving on a weekday morning: probably work.
                if (weekday && end.hour in 6..10) c.workVotes++
            }
        }

        val regular = clusters.filter { it.visits >= MIN_VISITS }
        val home = regular.filter { it.homeVotes >= 2 }.maxByOrNull { it.homeVotes * 2 + it.visits }
        val work = regular.filter { it !== home && it.workVotes >= 2 }.maxByOrNull { it.workVotes * 2 + it.visits }

        val usedSaved = mutableSetOf<Long>()
        val places = regular.sortedByDescending { it.visits }.mapIndexed { index, c ->
            val match = saved
                .filter { it.id !in usedSaved }
                .minByOrNull { DriveAnalysis.metersBetween(it.latitude, it.longitude, c.lat, c.lon) }
                ?.takeIf { DriveAnalysis.metersBetween(it.latitude, it.longitude, c.lat, c.lon) <= RADIUS_M }
            match?.let { usedSaved += it.id }
            val guessed = when {
                c === home -> PlaceKind.HOME
                c === work -> PlaceKind.WORK
                else -> PlaceKind.OTHER
            }
            val kind = match?.let { PlaceKind.entries.firstOrNull { k -> k.name == it.kind } } ?: guessed
            Place(
                latitude = c.lat,
                longitude = c.lon,
                visits = c.visits,
                name = match?.name ?: when (kind) {
                    PlaceKind.HOME -> "Home"
                    PlaceKind.WORK -> "Work"
                    PlaceKind.OTHER -> "Spot ${index + 1}"
                },
                kind = kind,
                savedId = match?.id,
                distanceMeters = c.distance,
                lastVisit = c.last,
            )
        }
        // A saved Home or Work wins over a guess, so only one of each is shown.
        val fixed = places.map { p ->
            val savedKindTaken = places.any { it.savedId != null && it.kind == p.kind && it !== p }
            if (p.savedId == null && p.kind != PlaceKind.OTHER && savedKindTaken) p.copy(kind = PlaceKind.OTHER) else p
        }
        val homePlace = fixed.firstOrNull { it.kind == PlaceKind.HOME }
        val workPlace = fixed.firstOrNull { it.kind == PlaceKind.WORK }
        return Summary(
            places = fixed,
            toWork = commute(sorted, insights, homePlace, workPlace, zone),
            toHome = commute(sorted, insights, workPlace, homePlace, zone),
        )
    }

    fun commute(trips: List<Trip>, insights: Map<Long, DriveInsight>, from: Place?, to: Place?, zone: ZoneId): Commute? {
        if (from == null || to == null) return null
        val legs = trips.filter { trip ->
            val i = insights[trip.id] ?: return@filter false
            val sLat = i.startLat ?: return@filter false
            val sLon = i.startLon ?: return@filter false
            val eLat = i.endLat ?: return@filter false
            val eLon = i.endLon ?: return@filter false
            trip.endTime != null &&
                DriveAnalysis.metersBetween(sLat, sLon, from.latitude, from.longitude) <= RADIUS_M &&
                DriveAnalysis.metersBetween(eLat, eLon, to.latitude, to.longitude) <= RADIUS_M
        }
        if (legs.isEmpty()) return null
        val durations = legs.map { (it.endTime ?: it.startTime) - it.startTime }
        val byHour = legs.groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).hour }
            .filterValues { it.size >= 2 }
            .mapValues { (_, list) -> list.map { (it.endTime ?: it.startTime) - it.startTime }.average() }
        val best = if (byHour.size >= 2) byHour.minByOrNull { it.value } else null
        return Commute(
            trips = legs.size,
            averageMillis = durations.average().toLong(),
            averageMeters = legs.map { it.distanceMeters }.average(),
            fastestMillis = durations.min(),
            slowestMillis = durations.max(),
            bestDepartureHour = best?.key,
            bestDepartureMillis = best?.value?.toLong(),
        )
    }
}
