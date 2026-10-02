package com.dannylombardo.gpstracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Trip::class, RoutePoint::class, FuelUp::class, Car::class], version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    abstract fun fuelUpDao(): FuelUpDao

    abstract fun carDao(): CarDao

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

        /** Adds cars. Every existing drive and fill-up moves into one starting car. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS cars (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL)",
                )
                db.execSQL("INSERT INTO cars (id, name) VALUES (1, '${CarRepository.DEFAULT_NAME}')")
                db.execSQL("ALTER TABLE trips ADD COLUMN carId INTEGER")
                db.execSQL("ALTER TABLE fuel_ups ADD COLUMN carId INTEGER")
                db.execSQL("UPDATE trips SET carId = 1")
                db.execSQL("UPDATE fuel_ups SET carId = 1")
            }
        }

        /** Adds Recently deleted: a drive's bin date, null for every drive kept so far. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN deletedAt INTEGER")
            }
        }

        /** A fresh install starts with one car, ready to rename. */
        private val seedFirstCar = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT INTO cars (id, name) VALUES (1, '${CarRepository.DEFAULT_NAME}')")
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
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .addCallback(seedFirstCar)
                    .build().also { instance = it }
            }
    }
}
