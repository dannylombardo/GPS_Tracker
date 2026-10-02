package com.dannylombardo.gpstracker.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.data.Trip
import com.dannylombardo.gpstracker.data.WeeklySummary
import com.dannylombardo.gpstracker.ui.theme.HeroColors
import com.dannylombardo.gpstracker.ui.theme.tabular
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
internal fun DrivesScreen(viewModel: MainViewModel, listState: LazyListState, padding: PaddingValues) {
    val week by viewModel.week.collectAsStateWithLifecycle()
    val activeTrip by viewModel.activeTrip.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val autoTrack by viewModel.autoTrack.collectAsStateWithLifecycle()
    val isCurrentWeek = viewModel.isCurrentWeek(week.weekStart)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Drives", formatDayAndDate(System.currentTimeMillis())) }
        if (!permissions.canAutoTrack) {
            item { SetupCard(permissions, onChanged = viewModel::refresh) }
        }
        item {
            val trip = activeTrip
            if (trip != null) {
                LiveDriveCard(trip, onEndDrive = viewModel::endDrive)
            } else {
                TrackingCard(
                    autoTrack = autoTrack,
                    canAutoTrack = permissions.canAutoTrack,
                    onAutoTrackChange = viewModel::setAutoTrack,
                    onStartDrive = viewModel::startDrive,
                )
            }
        }
        item {
            WeekCard(
                week = week,
                isCurrentWeek = isCurrentWeek,
                onPrevious = viewModel::previousWeek,
                onNext = viewModel::nextWeek,
            )
        }
        val unanswered = week.trips.filter { it.isMine == null }
        if (unanswered.isNotEmpty()) {
            item { AnswerBanner(unanswered.size, onClick = { viewModel.openTrip(unanswered.first().id) }) }
        }

        if (week.trips.isEmpty()) {
            item { EmptyDrives(isCurrentWeek) }
        } else {
            week.trips.groupBy { localDateOf(it.startTime) }.forEach { (day, trips) ->
                item(key = "day-$day") { SectionHeader(formatDayHeading(day)) }
                items(trips, key = { "trip-${it.id}" }) { trip ->
                    DriveRow(trip, viewModel, onClick = { viewModel.openTrip(trip.id) })
                }
            }
        }
    }
}

/** The headline card: the week's kilometres, a bar per day, and the rest of the week's figures. */
@Composable
private fun WeekCard(
    week: WeeklySummary,
    isCurrentWeek: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val onHero = HeroColors.content
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(HeroColors.drive)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isCurrentWeek) "This week" else "Week of ${formatShortDate(week.weekStart)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = onHero,
                    )
                    Text(
                        "${formatShortDate(week.weekStart)} – ${formatShortDate(week.weekEnd)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onHero.copy(alpha = 0.75f),
                    )
                }
                val arrowColors = IconButtonDefaults.iconButtonColors(
                    contentColor = onHero,
                    disabledContentColor = onHero.copy(alpha = 0.3f),
                )
                IconButton(onClick = onPrevious, colors = arrowColors) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous week")
                }
                IconButton(onClick = onNext, enabled = !isCurrentWeek, colors = arrowColors) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next week")
                }
            }

            BigNumber(kmNumber(week.distanceMeters), "km", color = onHero, style = MaterialTheme.typography.displayLarge)

            val today = LocalDate.now()
            WeekBars(
                dailyMeters = week.dailyDistanceMeters,
                todayIndex = if (isCurrentWeek) today.dayOfWeek.value - 1 else null,
                barColor = onHero,
                trackColor = onHero.copy(alpha = 0.12f),
                labelColor = onHero.copy(alpha = 0.75f),
            )

            Row(Modifier.fillMaxWidth()) {
                HeroStat("Drives", week.driveCount.toString(), onHero, Modifier.weight(1f))
                HeroStat("Time", formatDuration(week.drivingMillis, zero = "0 min"), onHero, Modifier.weight(1f))
                HeroStat("Avg speed", formatSpeed(week.averageSpeedMetersPerSecond), onHero, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                HeroStat("Top speed", formatSpeed(week.topSpeedMetersPerSecond), onHero, Modifier.weight(1f))
                HeroStat("Spent on fuel", formatMoney(week.moneySpent), onHero, Modifier.weight(1f))
                HeroStat("Litres", formatLitres(week.litresBought), onHero, Modifier.weight(1f))
            }
            if (week.otherDriverCount > 0) {
                Text(
                    "${week.otherDriverCount} driven by someone else, not counted",
                    style = MaterialTheme.typography.bodySmall,
                    color = onHero.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnswerBanner(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 1) "1 drive needs an answer" else "$count drives need an answer",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text("Were you driving? Until you say, they count as yours.", style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriveRow(trip: Trip, viewModel: MainViewModel, onClick: () -> Unit) {
    val end = trip.endTime ?: trip.startTime
    val route by produceState<List<LatLon>?>(null, trip.id) { value = viewModel.routePreview(trip.id) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().alpha(if (trip.countsAsMine) 1f else 0.55f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                val points = route
                if (points != null && points.size >= 2) {
                    RouteThumbnail(points, MaterialTheme.colorScheme.primary, Modifier.fillMaxSize())
                } else {
                    Icon(
                        Icons.Rounded.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${formatTime(trip.startTime)} – ${formatTime(end)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${formatDuration(end - trip.startTime)} · avg ${formatSpeed(trip.averageSpeedMetersPerSecond)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (trip.isMine) {
                    null -> Pill("Who drove?", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    false -> Pill("Someone else", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
                    true -> Unit
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(kmNumber(trip.distanceMeters), style = MaterialTheme.typography.titleLarge.tabular())
                Text("km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun Pill(text: String, container: Color, content: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .padding(top = 4.dp)
            .background(container, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun EmptyDrives(isCurrentWeek: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconBadge(
            Icons.Rounded.LocationOn,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 64.dp,
        )
        Text(
            if (isCurrentWeek) "No drives yet this week" else "No drives that week",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Drives show up here as soon as they end.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown while a drive is being recorded, with a pulsing dot and the distance so far. */
@Composable
private fun LiveDriveCard(trip: Trip, onEndDrive: () -> Unit) {
    val onHero = HeroColors.content
    val pulse = rememberInfiniteTransition(label = "recording")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "dot",
    )
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(HeroColors.live)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).alpha(dotAlpha).background(Color(0xFFFF5A4E), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("Recording a drive", style = MaterialTheme.typography.titleMedium, color = onHero)
            }
            BigNumber(kmNumber(trip.distanceMeters), "km", color = onHero)
            Text(
                "Since ${formatTime(trip.startTime)} · ${formatDuration(now - trip.startTime)}",
                style = MaterialTheme.typography.bodyMedium,
                color = onHero.copy(alpha = 0.8f),
            )
            Button(
                onClick = onEndDrive,
                colors = ButtonDefaults.buttonColors(containerColor = onHero, contentColor = HeroColors.live.last()),
            ) {
                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("End drive")
            }
        }
    }
}

@Composable
private fun TrackingCard(
    autoTrack: Boolean,
    canAutoTrack: Boolean,
    onAutoTrackChange: (Boolean) -> Unit,
    onStartDrive: () -> Unit,
) {
    val on = autoTrack && canAutoTrack
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    Icons.Rounded.DirectionsCar,
                    container = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (on) "Auto-tracking on" else "Auto-tracking off", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !canAutoTrack -> "Finish setup above first"
                            on -> "Waiting for your next drive"
                            else -> "Turn on to record drives by themselves"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = on, enabled = canAutoTrack, onCheckedChange = onAutoTrackChange)
            }
            TextButton(onClick = onStartDrive, enabled = canAutoTrack, modifier = Modifier.padding(start = 42.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start a drive now")
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
    val step = when {
        !permissions.location -> 1
        !permissions.activityRecognition -> 2
        else -> 3
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Set up drive tracking", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                if (index < step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                CircleShape,
                            ),
                    )
                }
            }
            val (explanation, action) = when (step) {
                1 -> "Step 1 of 3: allow location so drives can be measured." to {
                    requestMany.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                }
                2 -> "Step 2 of 3: allow physical activity so the app knows when you're in a car. GPS stays off the rest of the time." to {
                    val wanted = buildList {
                        add(Manifest.permission.ACTIVITY_RECOGNITION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    requestMany.launch(wanted.toTypedArray())
                }
                else -> "Step 3 of 3: choose \"Allow all the time\" for location, so a drive can start while the app is closed." to {
                    requestOne.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            Text(explanation, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = action) { Text("Allow") }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                }) { Text("Open settings") }
            }
        }
    }
}
