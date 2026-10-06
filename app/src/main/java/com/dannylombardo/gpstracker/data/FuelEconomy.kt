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
        /** What the fuel used over this stretch cost: the closing fill-up plus any part fills before it. */
        val cost: Double = 0.0,
    ) {
        val litresPer100Km: Double?
            get() = if (distanceMeters > 0) litres / (distanceMeters / 100_000) else null

        val costPerKm: Double?
            get() = if (distanceMeters > 0) cost / (distanceMeters / 1000) else null
    }

    /** What happened between one fill-up and the car's fill-up before it. */
    data class FuelUpStats(
        /** Your kilometres since the previous fill-up, of any kind; null for the car's first. */
        val distanceSincePreviousMeters: Double?,
        val millisSincePrevious: Long?,
        /** The full-tank-to-full-tank stretch this fill-up closed, when it's a full tank after another one. */
        val interval: Interval?,
    ) {
        val litresPer100Km: Double? get() = interval?.litresPer100Km
        val costPerKm: Double? get() = interval?.costPerKm
    }

    data class Summary(
        val intervals: List<Interval>,
        /** Kilometres driven since the last full tank, not yet matched by a fill-up. */
        val distanceSinceLastFullMeters: Double?,
        /** Per fill-up numbers, by fill-up id. */
        val stats: Map<Long, FuelUpStats> = emptyMap(),
        /** Every fill-up that went into this summary. */
        val fuelUps: List<FuelUp> = emptyList(),
    ) {
        /** Total litres over total distance across every complete stretch, so long stretches weigh more. */
        val averageLitresPer100Km: Double?
            get() {
                val counted = intervals.filter { it.distanceMeters > 0 }
                val meters = counted.sumOf { it.distanceMeters }
                return if (meters > 0) counted.sumOf { it.litres } / (meters / 100_000) else null
            }

        /** Total cost over total distance across every complete stretch. */
        val averageCostPerKm: Double?
            get() {
                val counted = intervals.filter { it.distanceMeters > 0 }
                val meters = counted.sumOf { it.distanceMeters }
                return if (meters > 0) counted.sumOf { it.cost } / (meters / 1000) else null
            }

        /** Average kilometres driven between one fill-up and the next. */
        val averageDistanceBetweenMeters: Double?
            get() = stats.values.mapNotNull { it.distanceSincePreviousMeters }.takeIf { it.isNotEmpty() }?.average()

        val averageMillisBetween: Double?
            get() = stats.values.mapNotNull { it.millisSincePrevious }.takeIf { it.isNotEmpty() }?.average()

        /** Litres bought over money paid, so big fill-ups weigh more. */
        val averagePricePerLitre: Double?
            get() {
                val litres = fuelUps.sumOf { it.litres }
                return if (litres > 0) fuelUps.sumOf { it.totalCost } / litres else null
            }

        val bestLitresPer100Km: Double? get() = intervals.mapNotNull { it.litresPer100Km }.minOrNull()

        val worstLitresPer100Km: Double? get() = intervals.mapNotNull { it.litresPer100Km }.maxOrNull()

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
        val stats = mutableMapOf<Long, FuelUpStats>()
        var lastFull: FuelUp? = null
        var litresSinceFull = 0.0
        var costSinceFull = 0.0
        var previous: FuelUp? = null
        for (fuelUp in sorted) {
            val before = previous
            previous = fuelUp
            stats[fuelUp.id] = FuelUpStats(
                distanceSincePreviousMeters = before?.let { distanceBetween(it.time, fuelUp.time) },
                millisSincePrevious = before?.let { fuelUp.time - it.time },
                interval = null,
            )
            if (lastFull == null) {
                // Nothing to measure from until the first full tank.
                if (fuelUp.isFullTank) lastFull = fuelUp
                continue
            }
            litresSinceFull += fuelUp.litres
            costSinceFull += fuelUp.totalCost
            if (fuelUp.isFullTank) {
                val interval = Interval(
                    endFuelUpId = fuelUp.id,
                    startTime = lastFull.time,
                    endTime = fuelUp.time,
                    litres = litresSinceFull,
                    distanceMeters = distanceBetween(lastFull.time, fuelUp.time),
                    cost = costSinceFull,
                )
                intervals += interval
                stats[fuelUp.id] = stats.getValue(fuelUp.id).copy(interval = interval)
                lastFull = fuelUp
                litresSinceFull = 0.0
                costSinceFull = 0.0
            }
        }
        val sinceLast = lastFull?.let { distanceBetween(it.time, Long.MAX_VALUE) }
        return Summary(intervals, sinceLast, stats, sorted)
    }
}
