package com.dannylombardo.gpstracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One visit to the pump. [isFullTank] matters for L/100km: consumption is only
 * worked out between two fill-ups that both topped the tank right up.
 * [tripId] is the drive that stopped at the station, when the app spotted it.
 */
@Entity(tableName = "fuel_ups")
data class FuelUp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val litres: Double,
    val pricePerLitre: Double,
    val isFullTank: Boolean = true,
    val stationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val tripId: Long? = null,
) {
    val totalCost: Double get() = litres * pricePerLitre
}
