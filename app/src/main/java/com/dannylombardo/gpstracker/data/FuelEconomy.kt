package com.dannylombardo.gpstracker.data

/**
 * Real fuel consumption, worked out the full-tank-to-full-tank way: fill right up,
 * drive, fill right up again, and the litres it took to refill (plus any part fills
 * in between) are what the drives in between used.
 *
 * Only drives that count as yours go into the distance. A drive belongs to the
 * stretch in which it started.
 */
object FuelEconomy {

    /** Fuel used between two full tanks, ending at the fill-up [endFuelUpId]. */
    data class Interval(
        val endFuelUpId: Long,
        val startTime: Long,
        val endTime: Long,
        val litres: Double,
        val distanceMeters: Double,
    ) {
        val litresPer100Km: Double?
            get() = if (distanceMeters > 0) litres / (distanceMeters / 100_000) else null
    }

    data class Summary(
        val intervals: List<Interval>,
        /** Kilometres driven since the last full tank, not yet matched by a fill-up. */
        val distanceSinceLastFullMeters: Double?,
    ) {
        /** Total litres over total distance across every complete stretch, so long stretches weigh more. */
        val averageLitresPer100Km: Double?
            get() {
                val counted = intervals.filter { it.distanceMeters > 0 }
                val meters = counted.sumOf { it.distanceMeters }
                return if (meters > 0) counted.sumOf { it.litres } / (meters / 100_000) else null
            }

        /** The L/100km of the stretch that ended at each fill-up, by fill-up id. */
        val byFuelUp: Map<Long, Double> by lazy {
            intervals.mapNotNull { interval -> interval.litresPer100Km?.let { interval.endFuelUpId to it } }.toMap()
        }
    }

    fun summarise(fuelUps: List<FuelUp>, trips: List<Trip>): Summary {
        val sorted = fuelUps.sortedWith(compareBy({ it.time }, { it.id }))
        val mine = trips.filter { it.endTime != null && it.countsAsMine }

        fun distanceBetween(from: Long, until: Long) =
            mine.filter { it.startTime >= from && it.startTime < until }.sumOf { it.distanceMeters }

        val intervals = mutableListOf<Interval>()
        var lastFull: FuelUp? = null
        var litresSinceFull = 0.0
        for (fuelUp in sorted) {
            if (lastFull == null) {
                // Nothing to measure from until the first full tank.
                if (fuelUp.isFullTank) lastFull = fuelUp
                continue
            }
            litresSinceFull += fuelUp.litres
            if (fuelUp.isFullTank) {
                intervals += Interval(
                    endFuelUpId = fuelUp.id,
                    startTime = lastFull.time,
                    endTime = fuelUp.time,
                    litres = litresSinceFull,
                    distanceMeters = distanceBetween(lastFull.time, fuelUp.time),
                )
                lastFull = fuelUp
                litresSinceFull = 0.0
            }
        }
        val sinceLast = lastFull?.let { distanceBetween(it.time, Long.MAX_VALUE) }
        return Summary(intervals, sinceLast)
    }
}
