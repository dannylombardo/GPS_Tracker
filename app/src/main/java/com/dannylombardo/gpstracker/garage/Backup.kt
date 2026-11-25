package com.dannylombardo.gpstracker.garage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dannylombardo.gpstracker.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Saves everything (drives with their routes, fill-ups, cars, and the garage) to one
 * JSON file you keep wherever you like, and reads it back on a new phone. The file is
 * a plain table-by-table copy, so it survives future app versions adding columns.
 */
object Backup {
    private const val FORMAT = "gps-tracker-backup"
    private const val FORMAT_VERSION = 1

    private val mainTables = listOf("cars", "trips", "route_points", "fuel_ups")
    private val garageTables = listOf("car_specs", "maintenance_items", "maintenance_logs", "places", "drive_insights")

    data class Result(val drives: Int, val fillUps: Int, val cars: Int)

    suspend fun write(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val main = AppDatabase.get(context)
        val garage = GarageDatabase.get(context)
        val counts = mutableMapOf<String, Int>()
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file")
        JsonWriter(OutputStreamWriter(out.buffered(), Charsets.UTF_8)).use { json ->
            json.beginObject()
            json.name("format").value(FORMAT)
            json.name("version").value(FORMAT_VERSION.toLong())
            json.name("createdAt").value(System.currentTimeMillis())
            json.name("main")
            writeTables(json, main, mainTables, counts)
            json.name("garage")
            writeTables(json, garage, garageTables, counts)
            json.name("prefs")
            json.beginObject()
            GaragePrefs.export(context).forEach { (k, v) ->
                json.name(k)
                when (v) {
                    is Boolean -> json.value(v)
                    is Number -> json.value(v)
                    is String -> json.value(v)
                    else -> json.nullValue()
                }
            }
            json.endObject()
            json.endObject()
        }
        Result(counts["trips"] ?: 0, counts["fuel_ups"] ?: 0, counts["cars"] ?: 0)
    }

    private fun writeTables(json: JsonWriter, db: RoomDatabase, tables: List<String>, counts: MutableMap<String, Int>) {
        val sql = db.openHelper.readableDatabase
        json.beginObject()
        for (table in tables) {
            json.name(table)
            json.beginArray()
            var n = 0
            sql.query("SELECT * FROM `$table`").use { c ->
                while (c.moveToNext()) {
                    json.beginObject()
                    for (i in 0 until c.columnCount) {
                        json.name(c.getColumnName(i))
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> json.nullValue()
                            Cursor.FIELD_TYPE_INTEGER -> json.value(c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> json.value(c.getDouble(i))
                            else -> json.value(c.getString(i))
                        }
                    }
                    json.endObject()
                    n++
                }
            }
            json.endArray()
            counts[table] = n
        }
        json.endObject()
    }

    /**
     * Replaces everything in the app with the backup. All or nothing: the file is read
     * straight into an open transaction, so if it turns out broken half way the app's
     * data is left as it was.
     */
    suspend fun restore(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val main = AppDatabase.get(context).openHelper.writableDatabase
        val garage = GarageDatabase.get(context).openHelper.writableDatabase
        val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open the file")
        val counts = mutableMapOf<String, Int>()
        val prefs = mutableMapOf<String, Any?>()
        main.beginTransaction()
        garage.beginTransaction()
        try {
            JsonReader(InputStreamReader(input.buffered(), Charsets.UTF_8)).use { json ->
                json.beginObject()
                require(json.hasNext() && json.nextName() == "format" && json.nextString() == FORMAT) {
                    "That file isn't a GPS Tracker backup"
                }
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "main" -> streamTables(json, main, mainTables, counts)
                        "garage" -> streamTables(json, garage, garageTables, counts)
                        "prefs" -> {
                            json.beginObject()
                            while (json.hasNext()) prefs[json.nextName()] = readValue(json)
                            json.endObject()
                        }
                        else -> json.skipValue()
                    }
                }
                json.endObject()
            }
            require("trips" in counts && "cars" in counts) { "The backup is missing its drives" }
            main.setTransactionSuccessful()
            garage.setTransactionSuccessful()
        } finally {
            garage.endTransaction()
            main.endTransaction()
        }
        GaragePrefs.import(context, prefs)
        // Let every open screen know the tables changed underneath it.
        AppDatabase.get(context).invalidationTracker.refreshVersionsAsync()
        GarageDatabase.get(context).invalidationTracker.refreshVersionsAsync()
        Result(counts["trips"] ?: 0, counts["fuel_ups"] ?: 0, counts["cars"] ?: 0)
    }

    private fun streamTables(json: JsonReader, sql: SupportSQLiteDatabase, tables: List<String>, counts: MutableMap<String, Int>) {
        // Children before parents, so foreign keys never complain.
        for (table in tables.reversed()) sql.execSQL("DELETE FROM `$table`")
        json.beginObject()
        while (json.hasNext()) {
            val table = json.nextName()
            if (table !in tables) {
                json.skipValue()
                continue
            }
            val columns = columnsOf(sql, table)
            var n = 0
            json.beginArray()
            while (json.hasNext()) {
                val values = ContentValues()
                json.beginObject()
                while (json.hasNext()) {
                    val key = json.nextName()
                    val value = readValue(json)
                    if (key !in columns) continue
                    when (value) {
                        null -> values.putNull(key)
                        is Long -> values.put(key, value)
                        is Double -> values.put(key, value)
                        is Boolean -> values.put(key, if (value) 1L else 0L)
                        else -> values.put(key, value.toString())
                    }
                }
                json.endObject()
                sql.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
                n++
            }
            json.endArray()
            counts[table] = n
        }
        json.endObject()
    }

    private fun readValue(json: JsonReader): Any? = when (json.peek()) {
        JsonToken.NULL -> {
            json.nextNull()
            null
        }
        JsonToken.BOOLEAN -> json.nextBoolean()
        JsonToken.NUMBER -> {
            val raw = json.nextString()
            raw.toLongOrNull() ?: raw.toDouble()
        }
        JsonToken.STRING -> json.nextString()
        else -> {
            json.skipValue()
            null
        }
    }

    private fun columnsOf(sql: SupportSQLiteDatabase, table: String): Set<String> =
        sql.query("PRAGMA table_info(`$table`)").use { c ->
            val name = c.getColumnIndexOrThrow("name")
            buildSet { while (c.moveToNext()) add(c.getString(name)) }
        }
}
