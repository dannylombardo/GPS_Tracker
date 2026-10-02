package com.dannylombardo.gpstracker.data

/** Splits drives and fill-ups by car, so each car keeps its own numbers next to the all-cars total. */
object CarStats {

    /** Everything ever driven and spent in one car, or in all of them. */
    data class Totals(
        val distanceMeters: Double,
        val driveCount: Int,
        val moneySpent: Double,
        val litres: Double,
    )

    /** Only [carId]'s drives, or every drive when [carId] is null (all cars). */
    fun tripsFor(trips: List<Trip>, carId: Long?): List<Trip> =
        if (carId == null) trips else trips.filter { it.carId == carId }

    fun fuelUpsFor(fuelUps: List<FuelUp>, carId: Long?): List<FuelUp> =
        if (carId == null) fuelUps else fuelUps.filter { it.carId == carId }

    /** Totals for drives that count as yours, plus every fill-up. */
    fun totals(trips: List<Trip>, fuelUps: List<FuelUp>): Totals {
        val mine = trips.filter { it.endTime != null && it.countsAsMine }
        return Totals(
            distanceMeters = mine.sumOf { it.distanceMeters },
            driveCount = mine.size,
            moneySpent = fuelUps.sumOf { it.totalCost },
            litres = fuelUps.sumOf { it.litres },
        )
    }

    /** Fuel economy worked out separately for each car, from only that car's fill-ups and drives. */
    fun economyByCar(cars: List<Car>, fuelUps: List<FuelUp>, trips: List<Trip>): Map<Long, FuelEconomy.Summary> =
        cars.associate { car ->
            car.id to FuelEconomy.summarise(fuelUpsFor(fuelUps, car.id), tripsFor(trips, car.id))
        }

    /** Kilometres each car did in [trips] (already limited to a week, say), counting only your drives. */
    fun distanceByCar(cars: List<Car>, trips: List<Trip>): List<Pair<Car, Double>> =
        cars.map { car -> car to trips.filter { it.carId == car.id && it.countsAsMine }.sumOf { it.distanceMeters } }
}
