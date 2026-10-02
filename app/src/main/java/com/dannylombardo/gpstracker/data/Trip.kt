package com.dannylombardo.gpstracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One drive. [endTime] stays null while the drive is still being recorded.
 * [isMine] is the answer to "who was driving?": null until answered. Unanswered
 * drives count as yours, so ignoring the question never loses a drive.
 * [carId] is the car it was driven in. [deletedAt] is set while the drive sits in
 * Recently deleted: it's left out of everything until restored, or removed for good
 * once it has been there for [DriveBin.KEEP_DAYS] days.
 */
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val distanceMeters: Double = 0.0,
    val topSpeedMetersPerSecond: Double? = null,
    val isMine: Boolean? = null,
    val carId: Long? = null,
    val deletedAt: Long? = null,
) {
    val countsAsMine: Boolean get() = isMine != false

    /** Distance over the whole time from start to end, stops included. */
    val averageSpeedMetersPerSecond: Double?
        get() {
            val seconds = ((endTime ?: return null) - startTime) / 1000.0
            return if (seconds > 0) distanceMeters / seconds else null
        }
}

/** A GPS fix recorded during a trip; together these make up the route. */
@Entity(
    tableName = "route_points",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class RoutePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val time: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
)
