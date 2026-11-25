package com.dannylombardo.gpstracker.garage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GarageDao {
    @Query("SELECT * FROM car_specs")
    fun observeSpecs(): Flow<List<CarSpec>>

    @Query("SELECT * FROM car_specs WHERE carId = :carId")
    suspend fun spec(carId: Long): CarSpec?

    @Upsert
    suspend fun saveSpec(spec: CarSpec)

    @Query("SELECT * FROM maintenance_items ORDER BY id")
    fun observeItems(): Flow<List<MaintenanceItem>>

    @Query("SELECT * FROM maintenance_items ORDER BY id")
    suspend fun items(): List<MaintenanceItem>

    @Query("SELECT COUNT(*) FROM maintenance_items WHERE carId = :carId")
    suspend fun itemCount(carId: Long): Int

    @Insert
    suspend fun insertItem(item: MaintenanceItem): Long

    @Insert
    suspend fun insertItems(items: List<MaintenanceItem>)

    @Upsert
    suspend fun saveItem(item: MaintenanceItem)

    @Query("DELETE FROM maintenance_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("UPDATE maintenance_items SET remindedAt = :at WHERE id = :id")
    suspend fun setReminded(id: Long, at: Long?)

    @Query("SELECT * FROM maintenance_logs ORDER BY time DESC")
    fun observeLogs(): Flow<List<MaintenanceLog>>

    @Insert
    suspend fun insertLog(log: MaintenanceLog): Long

    @Query("DELETE FROM maintenance_logs WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("SELECT * FROM places ORDER BY id")
    fun observePlaces(): Flow<List<SavedPlace>>

    @Upsert
    suspend fun savePlace(place: SavedPlace): Long

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deletePlace(id: Long)

    @Query("SELECT * FROM drive_insights")
    fun observeInsights(): Flow<List<DriveInsight>>

    @Query("SELECT tripId, version FROM drive_insights")
    suspend fun insightVersions(): List<InsightVersion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveInsights(insights: List<DriveInsight>)

    /** Call with at most a few hundred ids at a time (SQLite's variable limit). */
    @Query("DELETE FROM drive_insights WHERE tripId IN (:ids)")
    suspend fun deleteInsights(ids: List<Long>)
}

data class InsightVersion(val tripId: Long, val version: Int)

/**
 * The garage's own database, kept apart from drives and fill-ups so neither ever needs
 * migrating for the other. Backups include both.
 */
@Database(
    entities = [CarSpec::class, MaintenanceItem::class, MaintenanceLog::class, SavedPlace::class, DriveInsight::class],
    version = 1,
    exportSchema = true,
)
abstract class GarageDatabase : RoomDatabase() {
    abstract fun dao(): GarageDao

    companion object {
        const val NAME = "garage.db"

        @Volatile
        private var instance: GarageDatabase? = null

        fun get(context: Context): GarageDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, GarageDatabase::class.java, NAME)
                    .build().also { instance = it }
            }
    }
}
