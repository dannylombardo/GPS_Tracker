package com.dannylombardo.gpstracker.garage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The spec sheet for one of your cars (the car itself lives in the main database).
 * [odometerKm] is what the dash read at [odometerSetAt]; every drive in this car after
 * that is added on top, so the app's odometer keeps up on its own.
 */
@Entity(tableName = "car_specs")
data class CarSpec(
    @PrimaryKey val carId: Long,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val trim: String? = null,
    val horsepower: Int? = null,
    val torqueNm: Int? = null,
    val engine: String? = null,
    val drivetrain: String? = null,
    val fuelTankLitres: Double? = null,
    val odometerKm: Double? = null,
    val odometerSetAt: Long? = null,
    val plate: String? = null,
) {
    /** "2019 Honda Civic Si", or null when nothing's filled in. */
    val title: String?
        get() = listOfNotNull(year?.toString(), make, model, trim)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
}

/**
 * Something on a car that needs doing every so often: every [intervalKm] kilometres,
 * every [intervalMonths] months, or whichever comes first when both are set.
 */
@Entity(tableName = "maintenance_items", indices = [Index("carId")])
data class MaintenanceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val carId: Long,
    val name: String,
    val kind: String = MaintenanceKind.OTHER.name,
    val intervalKm: Int? = null,
    val intervalMonths: Int? = null,
    /** When it was last done; km and time are counted from here. */
    val lastDoneAt: Long,
    /** The due-soon notification already went out for this round. */
    val remindedAt: Long? = null,
)

/** One time a maintenance job was done, with what it cost. */
@Entity(tableName = "maintenance_logs", indices = [Index("itemId"), Index("carId")])
data class MaintenanceLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long?,
    val carId: Long,
    val name: String,
    val time: Long,
    val cost: Double? = null,
    val odometerKm: Double? = null,
    val note: String? = null,
)

/** A place you named (Home, Work, the gym), matched to drives that start or end nearby. */
@Entity(tableName = "places")
data class SavedPlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val kind: String = PlaceKind.OTHER.name,
)

enum class PlaceKind { HOME, WORK, OTHER }

/**
 * What the app worked out from one drive's GPS trace, saved so it's only done once:
 * where it started and ended, a smoothness score, and the fun numbers like 0-100 km/h.
 */
@Entity(tableName = "drive_insights")
data class DriveInsight(
    @PrimaryKey val tripId: Long,
    val version: Int,
    val startLat: Double? = null,
    val startLon: Double? = null,
    val endLat: Double? = null,
    val endLon: Double? = null,
    val score: Int? = null,
    val hardBrakes: Int = 0,
    val hardAccels: Int = 0,
    val secondsOver120: Long = 0,
    val nightMillis: Long = 0,
    val best0to100Millis: Long? = null,
    val best80to120Millis: Long? = null,
    val peakAccelMs2: Double? = null,
    val peakBrakeMs2: Double? = null,
)

enum class MaintenanceKind(val label: String, val defaultKm: Int?, val defaultMonths: Int?) {
    OIL("Oil change", 8_000, 12),
    TIRE_ROTATION("Tire rotation", 10_000, null),
    TIRE_SWAP("Seasonal tire swap", null, 6),
    BRAKES("Brake inspection", 20_000, 12),
    AIR_FILTER("Engine air filter", 20_000, 24),
    CABIN_FILTER("Cabin air filter", 20_000, 12),
    SPARK_PLUGS("Spark plugs", 100_000, null),
    COOLANT("Coolant flush", 80_000, 60),
    TRANSMISSION("Transmission fluid", 60_000, null),
    BATTERY("Battery check", null, 12),
    WIPERS("Wiper blades", null, 12),
    INSPECTION("Safety inspection", null, 12),
    WASH("Wash & detail", null, 1),
    OTHER("Other", null, null),
    ;

    companion object {
        fun of(name: String): MaintenanceKind = entries.firstOrNull { it.name == name } ?: OTHER

        /** What a new car's list starts with. */
        val starters = listOf(OIL, TIRE_ROTATION, BRAKES, AIR_FILTER, CABIN_FILTER, WIPERS)
    }
}
