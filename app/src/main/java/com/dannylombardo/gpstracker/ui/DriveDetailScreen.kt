package com.dannylombardo.gpstracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.data.RouteProfile
import com.dannylombardo.gpstracker.data.Trip
import com.dannylombardo.gpstracker.ui.theme.RouteColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A drive's route and profile, worked out once when its page opens. */
private class RouteData(val points: List<LatLon>, val profile: RouteProfile)

/** Wraps the trip so "still loading" (no value yet) differs from "deleted" (null trip). */
private class LoadedTrip(val trip: Trip?)

/** Everything about one drive: where it went, how fast, and who drove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DriveDetailScreen(tripId: Long, viewModel: MainViewModel, onBack: () -> Unit) {
    val loaded by remember(tripId) { viewModel.observeTrip(tripId).map { LoadedTrip(it) } }
        .collectAsStateWithLifecycle(initialValue = null)
    val fuelUps by remember(tripId) { viewModel.fuelUpsForTrip(tripId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val fuelEconomy by viewModel.fuelEconomy.collectAsStateWithLifecycle()
    val route by produceState<RouteData?>(null, tripId) {
        val points = viewModel.routePoints(tripId)
        value = withContext(Dispatchers.Default) {
            RouteData(points.map { LatLon(it.latitude, it.longitude) }, RouteProfile.of(points))
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var fullMap by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val trip = loaded?.trip

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(trip?.let { formatDayAndDate(it.startTime) } ?: "Drive") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (trip != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete drive")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            loaded == null -> Unit
            trip == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "This drive was deleted.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MapPreview(route, onExpand = { fullMap = true })
                Summary(trip)
                Stats(trip, route?.profile)
                route?.profile?.takeIf { it.speeds.size >= 2 }?.let { SpeedCard(it) }
                DriverCard(trip, onAnswer = { viewModel.setDriver(trip.id, it) })
                if (fuelUps.isNotEmpty()) {
                    SectionHeader("Filled up on this drive")
                    fuelUps.forEach { fuelUp ->
                        FuelUpRow(fuelUp, fuelEconomy.byFuelUp[fuelUp.id], onClick = { viewModel.openFuelUp(fuelUp) })
                    }
                }
            }
        }
    }

    if (confirmDelete && trip != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("Delete this drive?") },
            text = {
                Text(
                    "The ${formatKm(trip.distanceMeters)} drive and its route are removed for good. " +
                        "To just leave it out of your totals, choose \"Someone else\" instead.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteTrip(trip.id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    val points = route?.points
    if (fullMap && points != null) {
        FullScreenMap(points, onClose = { fullMap = false })
    }
}

@Composable
private fun MapPreview(route: RouteData?, onExpand: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        when {
            route == null -> CircularProgressIndicator()
            route.points.size < 2 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Map, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "No route was recorded for this drive",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                RouteMap(
                    points = route.points,
                    routeColor = MaterialTheme.colorScheme.primary,
                    dark = isSystemInDarkTheme(),
                    modifier = Modifier.fillMaxSize(),
                )
                // Keeps the preview still while the page scrolls; a tap opens the full map.
                Box(Modifier.matchParentSize().clickable(onClick = onExpand))
                FilledTonalIconButton(
                    onClick = onExpand,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(Icons.Rounded.Fullscreen, contentDescription = "Full screen map")
                }
                MapAttribution(Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

@Composable
private fun FullScreenMap(points: List<LatLon>, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            RouteMap(
                points = points,
                routeColor = MaterialTheme.colorScheme.primary,
                dark = isSystemInDarkTheme(),
                modifier = Modifier.fillMaxSize(),
            )
            FilledTonalIconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close map")
            }
            MapAttribution(Modifier.align(Alignment.BottomStart))
        }
    }
}

/** OpenStreetMap's licence asks for this credit wherever its map is shown. */
@Composable
private fun MapAttribution(modifier: Modifier = Modifier) {
    Text(
        "© OpenStreetMap contributors",
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF333333),
        modifier = modifier
            .padding(8.dp)
            .background(Color.White.copy(alpha = 0.8f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Distance front and centre, with when it started and ended. */
@Composable
private fun Summary(trip: Trip) {
    val end = trip.endTime ?: trip.startTime
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            BigNumber(kmNumber(trip.distanceMeters), "km", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.weight(1f))
            Text(
                formatDuration(end - trip.startTime),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimePoint(RouteColors.start, "Start", formatTime(trip.startTime))
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            TimePoint(RouteColors.end, "End", formatTime(end))
        }
    }
}

@Composable
private fun TimePoint(color: Color, label: String, time: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(time, style = MaterialTheme.typography.titleSmall)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Stats(trip: Trip, profile: RouteProfile?) {
    val moving = profile?.movingMillis?.takeIf { it > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatRow {
            StatTile(Icons.Rounded.Speed, "Average speed", formatSpeed(trip.averageSpeedMetersPerSecond), Modifier.weight(1f))
            StatTile(Icons.AutoMirrored.Rounded.TrendingUp, "Top speed", formatSpeed(trip.topSpeedMetersPerSecond), Modifier.weight(1f))
        }
        StatRow {
            StatTile(Icons.Rounded.Timer, "Moving", moving?.let { formatDuration(it) } ?: "–", Modifier.weight(1f))
            StatTile(
                Icons.Rounded.PauseCircleOutline,
                "Stopped",
                profile?.let { formatDuration(it.stoppedMillis, zero = "0 min") } ?: "–",
                Modifier.weight(1f),
            )
        }
        profile?.movingAverageMetersPerSecond(trip.distanceMeters)?.let { movingAverage ->
            Text(
                "Average while moving: ${formatSpeed(movingAverage)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun SpeedCard(profile: RouteProfile) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Speed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("km/h", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SpeedChart(profile)
            Row {
                Text(
                    formatTime(profile.speeds.first().time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatTime(profile.speeds.last().time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverCard(trip: Trip, onAnswer: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Who was driving?", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = trip.isMine == true,
                    onClick = { onAnswer(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                ) { Text("Me") }
                SegmentedButton(
                    selected = trip.isMine == false,
                    onClick = { onAnswer(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Rounded.Group, contentDescription = null, modifier = Modifier.size(18.dp)) },
                ) { Text("Someone else") }
            }
            Text(
                when (trip.isMine) {
                    null -> "Not answered yet, so it counts as yours for now."
                    true -> "Counts towards your weekly totals and fuel economy."
                    false -> "Left out of your weekly totals and fuel economy, but kept here."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
