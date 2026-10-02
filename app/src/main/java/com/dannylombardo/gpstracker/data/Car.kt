package com.dannylombardo.gpstracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One of your cars. Every drive and fill-up belongs to a car, so fuel economy is worked out per car. */
@Entity(tableName = "cars")
data class Car(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)
