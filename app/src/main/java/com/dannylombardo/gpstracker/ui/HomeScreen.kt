package com.dannylombardo.gpstracker.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dannylombardo.gpstracker.data.Trip
import com.dannylombardo.gpstracker.data.WeeklySummary
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel = viewModel()) {
    val week by viewModel.week.collectAsStateWithLifecycle()
    val activeTrip by viewModel.activeTrip.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val autoTrack by viewModel.autoTrack.collectAsStateWithLifecycle()
    val fuelEconomy by viewModel.fuelEconomy.collectAsStateWithLifecycle()
    val fuelDraft by viewModel.fuelDraft.collectAsStateWithLifecycle()
    val stationSpotting by viewModel.stationSpotting.collectAsStateWithLifecycle()
    var askingTrip by remember { mutableStateOf<Trip?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(topBar = { TopAppBar(title = { Text("Drives") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!permissions.canAutoTrack) {
                item { SetupCard(permissions, onChanged = viewModel::refresh) }
            }
            item {
                TrackingCard(
                    autoTrack = autoTrack,
                    canAutoTrack = permissions.canAutoTrack,
                    activeTrip = activeTrip,
                    onAutoTrackChange = viewModel::setAutoTrack,
                    onStartDrive = viewModel::startDrive,
                    onEndDrive = viewModel::endDrive,
                )
            }
            item {
                WeekCard(
                    week = week,
                    isCurrentWeek = viewModel.isCurrentWeek(week.weekStart),
                    onPrevious = viewModel::previousWeek,
                    onNext = viewModel::nextWeek,
                )
            }
            item {
                FuelCard(
                    economy = fuelEconomy,
                    stationSpotting = stationSpotting,
                    onAddFuelUp = { viewModel.openFuelUp() },
                    onStationSpottingChange = viewModel::setStationSpotting,
                )
            }
            if (week.fuelUps.isNotEmpty()) {
                item { Text("Fill-ups", style = MaterialTheme.typography.titleMedium) }
                items(week.fuelUps, key = { "fuel-${it.id}" }) { fuelUp ->
                    FuelUpRow(
                        fuelUp = fuelUp,
                        litresPer100Km = fuelEconomy.byFuelUp[fuelUp.id],
                        onClick = { viewModel.openFuelUp(fuelUp) },
                    )
                }
            }
            if (week.trips.isEmpty()) {
                item {
                    Text(
                        "No drives this week.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item { Text("Drives", style = MaterialTheme.typography.titleMedium) }
                items(week.trips, key = { "trip-${it.id}" }) { trip ->
                    TripRow(trip, onClick = { askingTrip = trip })
                }
            }
        }
    }

    askingTrip?.let { trip ->
        DriverDialog(
            trip = trip,
            onAnswer = { isMine ->
                viewModel.setDriver(trip.id, isMine)
                askingTrip = null
            },
            onDismiss = { askingTrip = null },
        )
    }

    fuelDraft?.let { draft ->
        FuelUpDialog(
            draft = draft,
            onSave = viewModel::saveFuelUp,
            onDelete = { viewModel.deleteFuelUp(draft.id) },
            onDismiss = viewModel::closeFuelUp,
        )
    }
}

@Composable
private fun WeekCard(
    week: WeeklySummary,
    isCurrentWeek: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isCurrentWeek) "This week" else "Week of ${formatShortDate(week.weekStart)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${formatShortDate(week.weekStart)} – ${formatShortDate(week.weekEnd)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onNext, enabled = !isCurrentWeek) {
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }

            Text(
                formatKm(week.distanceMeters),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Row(Modifier.fillMaxWidth()) {
                Stat("Drives", week.driveCount.toString(), Modifier.weight(1f))
                Stat("Time", formatDuration(week.drivingMillis, zero = "0 min"), Modifier.weight(1f))
                Stat("Avg speed", formatSpeed(week.averageSpeedMetersPerSecond), Modifier.weight(1f))
                Stat("Top speed", formatSpeed(week.topSpeedMetersPerSecond), Modifier.weight(1f))
            }

            Row(Modifier.fillMaxWidth()) {
                Stat("Spent on fuel", formatMoney(week.moneySpent), Modifier.weight(1f))
                Stat("Litres", formatLitres(week.litresBought), Modifier.weight(1f))
                Stat("Fill-ups", week.fuelUps.size.toString(), Modifier.weight(1f))
            }

            DailyBars(week.dailyDistanceMeters)

            val notes = buildList {
                if (week.otherDriverCount > 0) {
                    add("${week.otherDriverCount} driven by someone else, not counted")
                }
                if (week.unansweredCount > 0) {
                    add("${week.unansweredCount} not confirmed yet, tap a drive to say who drove")
                }
            }
            if (notes.isNotEmpty()) {
                Text(
                    notes.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Kilometres per day, Monday to Sunday. */
@Composable
private fun DailyBars(dailyMeters: List<Double>) {
    val max = dailyMeters.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val days = DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
    Row(
        Modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        dailyMeters.forEachIndexed { index, meters ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (meters > 0) String.format(Locale.getDefault(), "%.0f", meters / 1000) else "",
                    style = MaterialTheme.typography.labelSmall,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((56 * (meters / max)).dp.coerceAtLeast(2.dp))
                        .background(if (meters > 0) barColor else emptyColor, RoundedCornerShape(4.dp)),
                )
                Text(
                    days[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DriverDialog(trip: Trip, onAnswer: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who was driving?") },
        text = {
            Text(
                "${formatDate(trip.startTime)}, ${formatTime(trip.startTime)}, ${formatKm(trip.distanceMeters)}. " +
                    "Drives by someone else stay in the list but don't count towards your totals.",
            )
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Me") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("Someone else") } },
    )
}

@Composable
private fun SetupCard(permissions: PermissionState, onChanged: () -> Unit) {
    val context = LocalContext.current
    val requestMany = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onChanged()
    }
    val requestOne = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onChanged()
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Set up drive tracking", style = MaterialTheme.typography.titleMedium)
            val (explanation, action) = when {
                !permissions.location ->
                    "Step 1 of 3: allow location so drives can be measured." to {
                        requestMany.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    }
                !permissions.activityRecognition ->
                    "Step 2 of 3: allow physical activity so the app knows when you're in a car. GPS stays off the rest of the time." to {
                        val wanted = buildList {
                            add(Manifest.permission.ACTIVITY_RECOGNITION)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        requestMany.launch(wanted.toTypedArray())
                    }
                else ->
                    "Step 3 of 3: choose \"Allow all the time\" for location, so a drive can start while the app is closed." to {
                        requestOne.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
            }
            Text(explanation, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = action) { Text("Allow") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                }) { Text("Open settings") }
            }
        }
    }
}

@Composable
private fun TrackingCard(
    autoTrack: Boolean,
    canAutoTrack: Boolean,
    activeTrip: Trip?,
    onAutoTrackChange: (Boolean) -> Unit,
    onStartDrive: () -> Unit,
    onEndDrive: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Track drives automatically", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (canAutoTrack) "Starts recording when you get in a car" else "Finish setup above first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoTrack && canAutoTrack,
                    enabled = canAutoTrack,
                    onCheckedChange = onAutoTrackChange,
                )
            }
            if (activeTrip != null) {
                Text(
                    "Driving now: ${formatKm(activeTrip.distanceMeters)} since ${formatTime(activeTrip.startTime)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(onClick = onEndDrive) { Text("End drive") }
            } else {
                Text(
                    if (autoTrack && canAutoTrack) "Waiting for a drive" else "Not tracking",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onStartDrive, enabled = canAutoTrack) { Text("Start a drive now") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripRow(trip: Trip, onClick: () -> Unit) {
    val end = trip.endTime ?: trip.startTime
    val muted = !trip.countsAsMine
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().alpha(if (muted) 0.6f else 1f),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatDate(trip.startTime), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${formatTime(trip.startTime)} – ${formatTime(end)} · ${formatDuration(end - trip.startTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Avg ${formatSpeed(trip.averageSpeedMetersPerSecond)} · Top ${formatSpeed(trip.topSpeedMetersPerSecond)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (trip.isMine) {
                    false -> Text(
                        "Someone else drove",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    null -> Text(
                        "Were you driving? Tap to answer",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    true -> Unit
                }
            }
            Text(formatKm(trip.distanceMeters), style = MaterialTheme.typography.titleMedium)
        }
    }
}

internal fun formatKm(meters: Double) = String.format(Locale.getDefault(), "%.1f km", meters / 1000)

internal fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.FULL).format(Date(millis))

internal fun formatTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

private fun formatShortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

private fun formatSpeed(metersPerSecond: Double?): String =
    if (metersPerSecond == null) "–" else String.format(Locale.getDefault(), "%.0f km/h", metersPerSecond * 3.6)

private fun formatDuration(millis: Long, zero: String? = null): String {
    if (millis <= 0 && zero != null) return zero
    val minutes = (millis / 60_000).coerceAtLeast(1)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}
