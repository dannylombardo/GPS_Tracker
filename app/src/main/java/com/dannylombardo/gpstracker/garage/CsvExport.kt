package com.dannylombardo.gpstracker.garage

import com.dannylombardo.gpstracker.data.Car
import com.dannylombardo.gpstracker.data.FuelEconomy
import com.dannylombardo.gpstracker.data.FuelUp
import com.dannylombardo.gpstracker.data.Trip
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Spreadsheet-ready CSV of drives, fill-ups and maintenance, for expenses or taxes. */
object CsvExport {
    private val date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    fun escape(value: String?): String {
        if (value == null) return ""
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun num(value: Double?, decimals: Int = 1): String =
        value?.let { String.format(Locale.US, "%.${decimals}f", it) } ?: ""

    private fun row(vararg cells: String?) = cells.joinToString(",") { escape(it) }

    fun drives(trips: List<Trip>, cars: List<Car>, insights: Map<Long, DriveInsight>, zone: ZoneId): String {
        val names = cars.associate { it.id to it.name }
        val lines = mutableListOf(
            row("Date", "Start", "End", "Car", "Driver", "Distance (km)", "Duration (min)", "Average (km/h)", "Top speed (km/h)", "Drive score", "Hard brakes"),
        )
        trips.filter { it.endTime != null && it.deletedAt == null }.sortedBy { it.startTime }.forEach { t ->
            val start = Instant.ofEpochMilli(t.startTime).atZone(zone)
            val end = Instant.ofEpochMilli(t.endTime!!).atZone(zone)
            val insight = insights[t.id]
            lines += row(
                date.format(start),
                time.format(start),
                time.format(end),
                t.carId?.let { names[it] },
                if (t.countsAsMine) "Me" else "Someone else",
                num(t.distanceMeters / 1000, 2),
                num((t.endTime - t.startTime) / 60_000.0, 1),
                num(t.averageSpeedMetersPerSecond?.times(3.6)),
                num(t.topSpeedMetersPerSecond?.times(3.6)),
                insight?.score?.toString(),
                insight?.hardBrakes?.toString(),
            )
        }
        return lines.joinToString("\r\n") + "\r\n"
    }

    fun fillUps(fuelUps: List<FuelUp>, cars: List<Car>, stats: Map<Long, FuelEconomy.FuelUpStats>, zone: ZoneId): String {
        val names = cars.associate { it.id to it.name }
        val lines = mutableListOf(
            row("Date", "Time", "Car", "Station", "Litres", "Price per litre", "Total", "Full tank", "Km since last", "L/100km", "Cost per km"),
        )
        fuelUps.filter { it.deletedAt == null }.sortedBy { it.time }.forEach { f ->
            val at = Instant.ofEpochMilli(f.time).atZone(zone)
            val s = stats[f.id]
            lines += row(
                date.format(at),
                time.format(at),
                f.carId?.let { names[it] },
                f.stationName,
                num(f.litres, 2),
                num(f.pricePerLitre, 3),
                num(f.totalCost, 2),
                if (f.isFullTank) "Yes" else "No",
                num(s?.distanceSincePreviousMeters?.div(1000)),
                num(s?.litresPer100Km),
                num(s?.costPerKm, 3),
            )
        }
        return lines.joinToString("\r\n") + "\r\n"
    }

    fun maintenance(logs: List<MaintenanceLog>, cars: List<Car>, zone: ZoneId): String {
        val names = cars.associate { it.id to it.name }
        val lines = mutableListOf(row("Date", "Car", "Job", "Cost", "Odometer (km)", "Note"))
        logs.sortedBy { it.time }.forEach { l ->
            lines += row(
                date.format(Instant.ofEpochMilli(l.time).atZone(zone)),
                names[l.carId],
                l.name,
                num(l.cost, 2),
                num(l.odometerKm, 0),
                l.note,
            )
        }
        return lines.joinToString("\r\n") + "\r\n"
    }
}
