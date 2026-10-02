package com.dannylombardo.gpstracker.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dannylombardo.gpstracker.data.Trip
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel = viewModel()) {
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val activeTrip by viewModel.activeTrip.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val autoTrack by viewModel.autoTrack.collectAsStateWithLifecycle()

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
            if (trips.isEmpty()) {
                item {
                    Text(
                        "No drives yet. They'll show up here after your first trip.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item { Text("Recent drives", style = MaterialTheme.typography.titleMedium) }
                items(trips, key = { it.id }) { TripRow(it) }
            }
        }
    }
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

@Composable
private fun TripRow(trip: Trip) {
    val end = trip.endTime ?: trip.startTime
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatDate(trip.startTime), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${formatTime(trip.startTime)} – ${formatTime(end)} · ${formatDuration(end - trip.startTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatKm(trip.distanceMeters), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun formatKm(meters: Double) = String.format(Locale.getDefault(), "%.1f km", meters / 1000)

private fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.FULL).format(Date(millis))

private fun formatTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

private fun formatDuration(millis: Long): String {
    val minutes = (millis / 60_000).coerceAtLeast(1)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}
