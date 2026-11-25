package com.dannylombardo.gpstracker.garage

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.dannylombardo.gpstracker.data.Car
import com.dannylombardo.gpstracker.data.FuelUp
import com.dannylombardo.gpstracker.data.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A printable PDF of a stretch of time: totals, then every drive, fill-up and maintenance job. */
object PdfReport {
    private const val W = 595 // A4 in points
    private const val H = 842
    private const val M = 40f

    data class Range(val title: String, val from: LocalDate, val until: LocalDate)

    suspend fun write(
        context: Context,
        uri: Uri,
        range: Range,
        trips: List<Trip>,
        fuelUps: List<FuelUp>,
        logs: List<MaintenanceLog>,
        cars: List<Car>,
        zone: ZoneId,
    ) = withContext(Dispatchers.IO) {
        val from = range.from.atStartOfDay(zone).toInstant().toEpochMilli()
        val until = range.until.atStartOfDay(zone).toInstant().toEpochMilli()
        val drives = trips.filter { it.endTime != null && it.deletedAt == null && it.startTime in from until until }.sortedBy { it.startTime }
        val fills = fuelUps.filter { it.deletedAt == null && it.time in from until until }.sortedBy { it.time }
        val jobs = logs.filter { it.time in from until until }.sortedBy { it.time }
        val names = cars.associate { it.id to it.name }
        val money = NumberFormat.getCurrencyInstance()
        val day = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
        val clock = DateTimeFormatter.ofPattern("HH:mm")
        fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone)
        fun f(v: Double, d: Int = 1) = String.format(Locale.getDefault(), "%.${d}f", v)

        val doc = PdfDocument()
        val title = Paint().apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK; isAntiAlias = true }
        val h2 = Paint().apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK; isAntiAlias = true }
        val body = Paint().apply { textSize = 9.5f; color = Color.rgb(30, 30, 30); isAntiAlias = true }
        val muted = Paint().apply { textSize = 9f; color = Color.rgb(110, 110, 115); isAntiAlias = true }
        val headCell = Paint().apply { textSize = 9f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(90, 90, 95); isAntiAlias = true }
        val rule = Paint().apply { color = Color.rgb(215, 40, 40); strokeWidth = 2f }
        val hair = Paint().apply { color = Color.rgb(225, 225, 230); strokeWidth = 0.6f }

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M
            page!!.canvas.drawText("GPS Tracker · ${range.title} · page $pageNo", M, H - 20f, muted)
        }
        fun ensure(space: Float) {
            if (page == null || y + space > H - 50f) newPage()
        }
        fun table(header: List<String>, widths: List<Float>, rows: List<List<String>>) {
            fun drawRow(cells: List<String>, paint: Paint) {
                var x = M
                cells.forEachIndexed { i, text ->
                    val w = widths[i]
                    var t = text
                    while (t.isNotEmpty() && paint.measureText(t) > w - 6) t = t.dropLast(1)
                    page!!.canvas.drawText(t, x, y, paint)
                    x += w
                }
                y += 14f
            }
            ensure(30f)
            drawRow(header, headCell)
            page!!.canvas.drawLine(M, y - 10f, W - M, y - 10f, hair)
            for (row in rows) {
                if (y + 14f > H - 50f) {
                    newPage()
                    drawRow(header, headCell)
                    page!!.canvas.drawLine(M, y - 10f, W - M, y - 10f, hair)
                }
                drawRow(row, body)
            }
            y += 10f
        }

        newPage()
        val c = page!!.canvas
        c.drawText("Driving report", M, y + 18f, title)
        y += 34f
        c.drawText(range.title, M, y, h2)
        y += 10f
        c.drawLine(M, y, W - M, y, rule)
        y += 22f

        val mine = drives.filter { it.countsAsMine }
        val km = mine.sumOf { it.distanceMeters } / 1000
        val hours = mine.sumOf { it.endTime!! - it.startTime } / 3_600_000.0
        val fuelCost = fills.sumOf { it.totalCost }
        val litres = fills.sumOf { it.litres }
        val jobCost = jobs.sumOf { it.cost ?: 0.0 }
        val summary = listOf(
            "Distance driven (you)" to "${f(km)} km",
            "Drives (you)" to mine.size.toString(),
            "Time behind the wheel" to "${f(hours)} h",
            "Drives by someone else" to (drives.size - mine.size).toString(),
            "Fuel bought" to "${f(litres)} L",
            "Fuel spent" to money.format(fuelCost),
            "Maintenance spent" to money.format(jobCost),
            "Total running cost" to money.format(fuelCost + jobCost),
            "Cost per km" to if (km > 0) money.format((fuelCost + jobCost) / km) else "–",
        )
        summary.forEach { (k, v) ->
            ensure(16f)
            page!!.canvas.drawText(k, M, y, body)
            page!!.canvas.drawText(v, M + 220f, y, h2.apply { textSize = 10.5f })
            y += 16f
        }
        h2.textSize = 14f
        y += 14f

        if (drives.isNotEmpty()) {
            ensure(40f)
            page!!.canvas.drawText("Drives", M, y, h2)
            y += 18f
            table(
                listOf("Date", "Start", "End", "Car", "Driver", "km", "min"),
                listOf(130f, 45f, 45f, 120f, 80f, 50f, 45f),
                drives.map {
                    listOf(
                        day.format(at(it.startTime)),
                        clock.format(at(it.startTime)),
                        clock.format(at(it.endTime!!)),
                        it.carId?.let(names::get) ?: "",
                        if (it.countsAsMine) "Me" else "Someone else",
                        f(it.distanceMeters / 1000),
                        f((it.endTime - it.startTime) / 60_000.0, 0),
                    )
                },
            )
        }
        if (fills.isNotEmpty()) {
            ensure(40f)
            page!!.canvas.drawText("Fill-ups", M, y, h2)
            y += 18f
            table(
                listOf("Date", "Car", "Station", "Litres", "Per litre", "Total"),
                listOf(120f, 100f, 125f, 50f, 60f, 60f),
                fills.map {
                    listOf(
                        day.format(at(it.time)),
                        it.carId?.let(names::get) ?: "",
                        it.stationName ?: "",
                        f(it.litres, 2),
                        String.format(Locale.getDefault(), "%.3f", it.pricePerLitre),
                        money.format(it.totalCost),
                    )
                },
            )
        }
        if (jobs.isNotEmpty()) {
            ensure(40f)
            page!!.canvas.drawText("Maintenance", M, y, h2)
            y += 18f
            table(
                listOf("Date", "Car", "Job", "Odometer", "Cost"),
                listOf(120f, 110f, 150f, 70f, 65f),
                jobs.map {
                    listOf(
                        day.format(at(it.time)),
                        names[it.carId] ?: "",
                        it.name,
                        it.odometerKm?.let { o -> "${f(o, 0)} km" } ?: "",
                        it.cost?.let(money::format) ?: "",
                    )
                },
            )
        }
        page?.let { doc.finishPage(it) }
        try {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file")
            out.use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }
}
