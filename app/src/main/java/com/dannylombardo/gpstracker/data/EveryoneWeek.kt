package com.dannylombardo.gpstracker.data

/**
 * Everything driven in a week, by anyone, in any car: the kilometres you drove and the
 * ones someone else drove, each car's share, and a per-day total. Unlike [WeeklySummary]
 * this counts "someone else" drives too, and it ignores which car the screens are showing.
 */
data class EveryoneWeek(
    val mineMeters: Double,
    val othersMeters: Double,
    val mineCount: Int,
    val othersCount: Int,
    /** Each car's kilometres, yours and someone else's, for cars that moved this week. Biggest first. */
    val byCar: List<CarShare>,
    /** Total kilometres each day, Monday first, everyone together. */
    val dailyMeters: List<Double>,
) {
    data class CarShare(val car: Car, val mineMeters: Double, val othersMeters: Double) {
        val totalMeters: Double get() = mineMeters + othersMeters
    }

    val totalMeters: Double get() = mineMeters + othersMeters
    val driveCount: Int get() = mineCount + othersCount

    /** How much of the week someone else drove, 0 to 1, or 0 when nothing was driven. */
    val othersShare: Double get() = if (totalMeters > 0) othersMeters / totalMeters else 0.0

    companion object {
        /** From a [WeeklySummary] of every car, whose trips already hold the week's drives, yours or not. */
        fun of(week: WeeklySummary, cars: List<Car>, zone: java.time.ZoneId): EveryoneWeek {
            val trips = week.trips.filter { it.endTime != null }
            val (mine, others) = trips.partition { it.countsAsMine }
            val daily = MutableList(7) { 0.0 }
            trips.forEach { trip ->
                val day = java.time.Instant.ofEpochMilli(trip.startTime).atZone(zone).dayOfWeek
                daily[day.value - 1] += trip.distanceMeters
            }
            val byCar = cars
                .map { car ->
                    val inCar = trips.filter { it.carId == car.id }
                    CarShare(
                        car = car,
                        mineMeters = inCar.filter { it.countsAsMine }.sumOf { it.distanceMeters },
                        othersMeters = inCar.filterNot { it.countsAsMine }.sumOf { it.distanceMeters },
                    )
                }
                .filter { it.totalMeters > 0 }
                .sortedByDescending { it.totalMeters }
            return EveryoneWeek(
                mineMeters = mine.sumOf { it.distanceMeters },
                othersMeters = others.sumOf { it.distanceMeters },
                mineCount = mine.size,
                othersCount = others.size,
                byCar = byCar,
                dailyMeters = daily,
            )
        }
    }
}
