package com.dannylombardo.gpstracker.garage

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import com.dannylombardo.gpstracker.R
import com.dannylombardo.gpstracker.data.AppDatabase
import com.dannylombardo.gpstracker.data.Trip
import com.dannylombardo.gpstracker.data.WeeklySummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The home screen widget: this week's kilometres with a bar per day, and your last drive. */
class GarageWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                render(context, manager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Redraws every widget on the home screen. Cheap: two small queries. */
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GarageWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        /** Fire-and-forget version for places that aren't in a coroutine. */
        fun requestUpdate(context: Context) {
            val app = context.applicationContext
            scope.launch { runCatching { updateAll(app) } }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val trips = AppDatabase.get(context).tripDao().observeFinishedTrips().first()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val week = WeeklySummary.of(trips, WeeklySummary.weekStartOf(today), zone)
            val last = trips.filter { it.countsAsMine }.maxByOrNull { it.startTime }
            val views = RemoteViews(context.packageName, R.layout.widget_garage).apply {
                setTextViewText(R.id.widget_km, String.format(Locale.getDefault(), "%.1f", week.distanceMeters / 1000))
                setTextViewText(R.id.widget_week_detail, weekDetail(week))
                setTextViewText(R.id.widget_last, lastDrive(last, today, zone))
                setImageViewBitmap(R.id.widget_bars, bars(week.dailyDistanceMeters, today.dayOfWeek.value - 1))
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context,
                        42,
                        GarageIntents.openGarage(context),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            manager.updateAppWidget(ids, views)
        }

        private fun weekDetail(week: WeeklySummary): String {
            if (week.driveCount == 0) return "No drives yet"
            val minutes = week.drivingMillis / 60_000
            val time = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
            val drives = if (week.driveCount == 1) "1 drive" else "${week.driveCount} drives"
            return "$drives · $time"
        }

        private fun lastDrive(trip: Trip?, today: LocalDate, zone: ZoneId): String {
            if (trip == null) return "Last drive: none yet"
            val start = Instant.ofEpochMilli(trip.startTime).atZone(zone)
            val day = when (start.toLocalDate()) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> start.format(DateTimeFormatter.ofPattern("EEE d MMM"))
            }
            val time = start.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            val km = String.format(Locale.getDefault(), "%.1f km", trip.distanceMeters / 1000)
            val top = trip.topSpeedMetersPerSecond?.let { " · top ${(it * 3.6).toInt()} km/h" } ?: ""
            return "Last drive  $day $time · $km$top"
        }

        /** Seven little bars, Monday to Sunday, today's in red. */
        private fun bars(daily: List<Double>, todayIndex: Int): Bitmap {
            val w = 280
            val h = 96
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val max = daily.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            val gap = 10f
            val barW = (w - gap * 6) / 7
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            daily.forEachIndexed { i, meters ->
                val left = i * (barW + gap)
                val track = RectF(left, 0f, left + barW, h.toFloat())
                paint.color = Color.argb(28, 255, 255, 255)
                canvas.drawRoundRect(track, 8f, 8f, paint)
                if (meters > 0) {
                    val top = h - (meters / max * h).toFloat().coerceAtLeast(10f)
                    paint.color = if (i == todayIndex) Color.rgb(225, 6, 0) else Color.argb(220, 255, 255, 255)
                    canvas.drawRoundRect(RectF(left, top, left + barW, h.toFloat()), 8f, 8f, paint)
                }
            }
            return bitmap
        }
    }
}
