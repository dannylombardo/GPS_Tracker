package com.dannylombardo.gpstracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Trip::class, RoutePoint::class, FuelUp::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    abstract fun fuelUpDao(): FuelUpDao

    companion object {
        /** Adds top speed and the "who was driving?" answer to trips. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN topSpeedMetersPerSecond REAL")
                db.execSQL("ALTER TABLE trips ADD COLUMN isMine INTEGER")
            }
        }

        /** Adds fill-ups at the pump. Existing drives are untouched. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS fuel_ups (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "time INTEGER NOT NULL, " +
                        "litres REAL NOT NULL, " +
                        "pricePerLitre REAL NOT NULL, " +
                        "isFullTank INTEGER NOT NULL, " +
                        "stationName TEXT, " +
                        "latitude REAL, " +
                        "longitude REAL, " +
                        "tripId INTEGER)",
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gps_tracker.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
