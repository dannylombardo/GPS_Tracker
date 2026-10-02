package com.dannylombardo.gpstracker.tracking

import android.util.Log
import com.dannylombardo.gpstracker.tracking.TripDistanceTracker.Companion.distanceBetween
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class GasStation(val name: String?, val latitude: Double, val longitude: Double)

/** A stop that happened at a gas station. */
data class StationVisit(val stop: Stop, val station: GasStation)

/**
 * Looks up gas stations in OpenStreetMap through the public Overpass API, which
 * needs no account or key. Only the stop coordinates are sent, once per drive.
 */
object GasStations {
    private const val TAG = "GasStations"
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    /** How far from a station's mapped point the car can be parked and still count as being there. */
    const val MATCH_RADIUS_METERS = 80.0

    /** One request covering every stop; returns stations near any of them, or null if the lookup failed. */
    fun fetchNear(stops: List<Stop>): List<GasStation>? {
        if (stops.isEmpty()) return emptyList()
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("User-Agent", "GPS-Tracker-Android/1.0 (personal drive log)")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = "data=" + URLEncoder.encode(query(stops), "UTF-8")
            connection.outputStream.use { it.write(body.toByteArray()) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Overpass answered ${connection.responseCode}")
                return null
            }
            parseCsv(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (e: IOException) {
            Log.w(TAG, "Gas station lookup failed", e)
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Overpass QL for fuel stations around each stop, as tab-separated
     * latitude, longitude, name and brand (the centre point for station outlines).
     */
    internal fun query(stops: List<Stop>): String {
        val radius = MATCH_RADIUS_METERS.toInt()
        val around = stops.joinToString("") { stop ->
            String.format(
                Locale.US,
                "nwr[\"amenity\"=\"fuel\"](around:%d,%.6f,%.6f);",
                radius,
                stop.latitude,
                stop.longitude,
            )
        }
        return "[out:csv(::lat,::lon,name,brand;false)][timeout:15];($around);out center;"
    }

    internal fun parseCsv(text: String): List<GasStation> =
        text.lineSequence().mapNotNull { line ->
            val fields = line.split('\t')
            val latitude = fields.getOrNull(0)?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
            val longitude = fields.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
            val name = fields.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
                ?: fields.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }
            GasStation(name, latitude, longitude)
        }.toList()

    /**
     * The stop that was at a gas station, if any. When several were, the drive's end
     * wins (that's where the fill-up usually ends a drive), then the closest match.
     */
    fun match(stops: List<Stop>, stations: List<GasStation>): StationVisit? =
        stops.flatMap { stop ->
            stations.map { station ->
                Triple(stop, station, distanceBetween(stop.toFix(), Fix(station.latitude, station.longitude, 0)))
            }
        }
            .filter { it.third <= MATCH_RADIUS_METERS }
            .minWithOrNull(compareBy({ !it.first.isDriveEnd }, { it.third }))
            ?.let { StationVisit(it.first, it.second) }

    private fun Stop.toFix() = Fix(latitude, longitude, timeMillis)
}
