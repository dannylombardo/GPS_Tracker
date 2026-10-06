package com.dannylombardo.gpstracker.ui

import android.text.format.DateFormat as AndroidDateFormat
import java.text.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

internal fun kmNumber(meters: Double): String = String.format(Locale.getDefault(), "%.1f", meters / 1000)

internal fun formatKm(meters: Double) = "${kmNumber(meters)} km"

internal fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.FULL).format(Date(millis))

internal fun formatTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

/** With seconds, for reading a moment off the speed chart or the map slider. */
internal fun formatTimeWithSeconds(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(millis))

internal fun formatShortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/** "Wednesday, September 30", without the year. */
internal fun formatDayAndDate(millis: Long): String {
    val pattern = AndroidDateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd")
    return DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}

/** "Today", "Yesterday", or the weekday and date, for headings in the drive list. */
internal fun formatDayHeading(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> formatDayAndDate(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
}

internal fun localDateOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

internal fun speedNumber(metersPerSecond: Double?): String =
    if (metersPerSecond == null) "–" else String.format(Locale.getDefault(), "%.0f", metersPerSecond * 3.6)

internal fun formatSpeed(metersPerSecond: Double?): String =
    if (metersPerSecond == null) "–" else "${speedNumber(metersPerSecond)} km/h"

internal fun formatDuration(millis: Long, zero: String? = null): String {
    if (millis <= 0 && zero != null) return zero
    val minutes = (millis / 60_000).coerceAtLeast(1)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

internal fun formatMoney(amount: Double): String = NumberFormat.getCurrencyInstance().format(amount)

internal fun formatPricePerLitre(price: Double): String =
    NumberFormat.getCurrencyInstance().apply { maximumFractionDigits = 3 }.format(price) + "/L"

internal fun formatLitres(litres: Double): String = String.format(Locale.getDefault(), "%.1f L", litres)

internal fun consumptionNumber(litresPer100Km: Double): String =
    String.format(Locale.getDefault(), "%.1f", litresPer100Km)

internal fun formatConsumption(litresPer100Km: Double): String = "${consumptionNumber(litresPer100Km)} L/100km"

internal fun formatCostPerKm(cost: Double): String =
    NumberFormat.getCurrencyInstance().apply { maximumFractionDigits = 3 }.format(cost) + "/km"

/** How long between two fill-ups: "5 h" under a day, else whole days like "6 days". */
internal fun formatGap(millis: Double): String {
    val hours = millis / 3_600_000
    if (hours < 24) return "${hours.toInt().coerceAtLeast(1)} h"
    val days = Math.round(hours / 24)
    return if (days == 1L) "1 day" else "$days days"
}
