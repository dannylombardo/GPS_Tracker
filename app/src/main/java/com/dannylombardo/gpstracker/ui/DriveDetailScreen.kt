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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import com.dannylombardo.gpstracker.data.Car
import com.dannylombardo.gpstracker.data.DriveBin
import com.dannylombardo.gpstracker.data.RoutePoint
import com.dannylombardo.gpstracker.data.RouteProfile
import com.dannylombardo.gpstracker.data.SpeedBand
import com.dannylombardo.gpstracker.data.SpeedRuns
import com.dannylombardo.gpstracker.data.Trip
import com.dannylombardo.gpstracker.ui.theme.RouteColors
import com.dannylombardo.gpstracker.ui.theme.tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A drive's route, its speed colouring and profile, worked out once when its page opens. */
private class RouteData(
    val points: List<LatLon>,
    val stretches: List<ColoredStretch>,
    val profile: RouteProfile,
    /** The fixes in time order, for finding where the car was at a moment. */
    val fixes: List<RoutePoint>,
) {
    /** Whether there's enough of a speed trace to slide along. */
    val canScrub: Boolean get() = profile.speeds.size >= 2 && points.size >= 2

    /** Where the car was at [time], marked in its speed colour with the speed. */
    fun markerAt(time: Long?): MapMarker? {
        if (time == null) return null
        val position = RouteProfile.positionAt(fixes, time) ?: return null
        val speed = profile.speedAt(time)
        return MapMarker(LatLon(position.latitude, position.longitude), SpeedBand.of(speed).color(), formatSpeed(speed))
    }
}

/** Shown on a drive that's in Recently deleted. */
@Composable
private fun BinnedNotice(deletedAt: Long) {
    val days = DriveBin.daysLeft(deletedAt, System.currentTimeMillis())
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Delete, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                "In Recently deleted and left out of your totals. " +
                    "It's removed for good in ${if (days == 1) "1 day" else "$days days"} unless you restore it.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

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
    val consumption by viewModel.consumptionByFuelUp.collectAsStateWithLifecycle()
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val route by produceState<RouteData?>(null, tripId) {
        val points = viewModel.routePoints(tripId)
        value = withContext(Dispatchers.Default) {
            RouteData(
                fixes = points.sortedBy { it.time },
                points = points.map { LatLon(it.latitude, it.longitude) },
                stretches = SpeedRuns.of(points).map { run ->
                    ColoredStretch(run.band.color(), run.points.map { LatLon(it.latitude, it.longitude) })
                },
                profile = RouteProfile.of(points),
            )
        }
    }
    var fullMap by remember { mutableStateOf(false) }
    // The moment picked on the speed chart or the map slider; both show it and both move it.
    var selectedTime by remember(tripId) { mutableStateOf<Long?>(null) }
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
                    if (trip?.deletedAt != null) {
                        TextButton(onClick = { viewModel.restoreTrip(trip.id) }) { Text("Restore") }
                    } else if (trip != null) {
                        IconButton(onClick = { viewModel.moveToBin(trip.id) }) {
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
                    "This drive was deleted for good.",
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
                if (trip.deletedAt != null) BinnedNotice(trip.deletedAt)
                MapPreview(
                    route,
                    selectedTime = selectedTime,
                    onSelect = { selectedTime = it },
                    onExpand = { fullMap = true },
                )
                Summary(trip)
                Stats(trip, route?.profile)
                route?.profile?.takeIf { it.speeds.size >= 2 }?.let { profile ->
                    SpeedCard(
                        profile,
                        selectedTime = selectedTime,
                        onSelect = { selectedTime = it },
                        onClear = { selectedTime = null },
                    )
                }
                DriverCard(trip, onAnswer = { viewModel.setDriver(trip.id, it) })
                if (cars.size > 1) {
                    CarCard(cars, trip.carId, onPick = { viewModel.setTripCar(trip.id, it) })
                }
                if (fuelUps.isNotEmpty()) {
                    SectionHeader("Filled up on this drive")
                    fuelUps.forEach { fuelUp ->
                        FuelUpRow(fuelUp, consumption[fuelUp.id], onClick = { viewModel.openFuelUp(fuelUp) })
                    }
                }
            }
        }
    }

    val fullRoute = route
    if (fullMap && fullRoute != null) {
        FullScreenMap(
            fullRoute,
            selectedTime = selectedTime,
            onSelect = { selectedTime = it },
            onClose = { fullMap = false },
        )
    }
}

@Composable
private fun MapPreview(route: RouteData?, selectedTime: Long?, onSelect: (Long) -> Unit, onExpand: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MapBox(route, selectedTime, onExpand)
        if (route != null && route.canScrub) {
            DriveSlider(route.profile, selectedTime, onSelect)
        }
    }
}

@Composable
private fun MapBox(route: RouteData?, selectedTime: Long?, onExpand: () -> Unit) {
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
                    stretches = route.stretches,
                    marker = route.markerAt(selectedTime),
                )
                // Keeps the preview still while the page scrolls; a tap opens the full map.
                Box(Modifier.matchParentSize().clickable(onClick = onExpand))
                FilledTonalIconButton(
                    onClick = onExpand,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(Icons.Rounded.Fullscreen, contentDescription = "Full screen map")
                }
                SpeedLegend(Modifier.align(Alignment.TopStart).padding(8.dp))
                MapAttribution(Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

@Composable
private fun FullScreenMap(route: RouteData, selectedTime: Long?, onSelect: (Long) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            RouteMap(
                points = route.points,
                routeColor = MaterialTheme.colorScheme.primary,
                dark = isSystemInDarkTheme(),
                modifier = Modifier.fillMaxSize(),
                stretches = route.stretches,
                marker = route.markerAt(selectedTime),
            )
            SpeedLegend(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp))
            FilledTonalIconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close map")
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
                MapAttribution()
                if (route.canScrub) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    ) {
                        DriveSlider(route.profile, selectedTime, onSelect, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
                    }
                }
            }
        }
    }
}

/**
 * A slider along the drive, start to end. Dragging it moves a marker along the route
 * showing the speed there, and moves the line on the speed chart with it.
 */
@Composable
private fun DriveSlider(
    profile: RouteProfile,
    selectedTime: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = profile.speeds.first().time
    val end = profile.speeds.last().time
    val span = (end - start).coerceAtLeast(1)
    val fraction = selectedTime?.let { ((it - start).toFloat() / span).coerceIn(0f, 1f) } ?: 0f
    Column(modifier) {
        if (selectedTime != null) {
            MomentReadout(profile, selectedTime)
        } else {
            Text(
                "Slide to see your speed anywhere on the route",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Slider(
            value = fraction,
            onValueChange = { onSelect(start + (it * span).toLong()) },
        )
    }
}

/** The speed at a picked moment, with its speed colour, and the time. */
@Composable
private fun MomentReadout(profile: RouteProfile, time: Long, modifier: Modifier = Modifier) {
    val speed = profile.speedAt(time)
    Row(modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(SpeedBand.of(speed).color(), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(formatSpeed(speed), style = MaterialTheme.typography.titleMedium.tabular())
        Spacer(Modifier.width(8.dp))
        Text(
            formatTimeWithSeconds(time),
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What the route colours mean, in km/h. */
@Composable
private fun SpeedLegend(modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeedBand.entries.forEach { band ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 12.dp, height = 4.dp).background(band.color(), CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(
                    band.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            "km/h",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

/** The speed chart. Tap or drag across it to read the speed at any moment, like a stock chart. */
@Composable
private fun SpeedCard(profile: RouteProfile, selectedTime: Long?, onSelect: (Long) -> Unit, onClear: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectedTime != null) {
                    MomentReadout(profile, selectedTime, Modifier.weight(1f))
                    IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                } else {
                    Text("Speed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("km/h", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SpeedChart(profile, selectedTime = selectedTime, onSelect = onSelect)
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

/** Which car this drive was in; switching it moves the kilometres to that car's numbers. */
@Composable
private fun CarCard(cars: List<Car>, carId: Long?, onPick: (Long) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Which car?", style = MaterialTheme.typography.titleMedium)
            CarChoiceChips(cars, selectedId = carId, onSelect = onPick)
            Text(
                "Counts towards this car's totals and fuel economy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
