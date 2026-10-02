package com.dannylombardo.gpstracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.data.DriveBin
import com.dannylombardo.gpstracker.data.Trip
import com.dannylombardo.gpstracker.data.WeeklySummary
import com.dannylombardo.gpstracker.ui.theme.tabular
import java.time.LocalDate

/**
 * Every past drive, a section per week with its kilometres, newest first. The two most
 * recent weeks start open; older ones fold away until tapped. [expanded] holds the weeks
 * opened or closed by hand, kept outside so it survives a visit to a drive's page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AllDrivesScreen(
    viewModel: MainViewModel,
    listState: LazyListState,
    expanded: SnapshotStateMap<LocalDate, Boolean>,
    onBack: () -> Unit,
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val binned by viewModel.binnedTrips.collectAsStateWithLifecycle()
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val viewedCarId by viewModel.viewedCarId.collectAsStateWithLifecycle()
    val showCarNames = cars.size > 1 && viewedCarId == null
    val carNames = cars.associate { it.id to it.name }
    val thisWeek = WeeklySummary.weekStartOf(LocalDate.now())
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("All drives") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::openBin) {
                        BadgedBox(badge = { if (binned.isNotEmpty()) Badge { Text(binned.size.toString()) } }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Recently deleted")
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
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "cars") { CarFilterRow(cars, viewedCarId, onSelect = viewModel::viewCar, onManage = viewModel::openCars) }
            if (history.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No drives yet",
                        body = "Every drive you take ends up here, sorted by week.",
                        icon = { IconBadgeHistory() },
                    )
                }
            }
            history.forEach { week ->
                val start = week.weekStart
                val open = expanded[start] ?: (start >= thisWeek.minusWeeks(1))
                item(key = "week-$start") {
                    WeekHeader(
                        week = week,
                        title = weekTitle(start, thisWeek),
                        open = open,
                        onToggle = { expanded[start] = !open },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (open) {
                    week.trips.groupBy { localDateOf(it.startTime) }.forEach { (day, trips) ->
                        item(key = "day-$day") { SectionHeader(formatDayHeading(day), Modifier.animateItem()) }
                        items(trips, key = { "trip-${it.id}" }) { trip ->
                            SwipeToBin(onBin = { viewModel.moveToBin(trip.id) }, modifier = Modifier.animateItem()) {
                                DriveRow(
                                    trip,
                                    carName = trip.carId?.let { carNames[it] }.takeIf { showCarNames },
                                    viewModel = viewModel,
                                    onClick = { viewModel.openTrip(trip.id) },
                                )
                            }
                        }
                    }
                }
            }
            if (history.isNotEmpty()) {
                item(key = "hint") {
                    Text(
                        "Swipe a drive sideways to delete it. Deleted drives wait in Recently deleted for " +
                            "${DriveBin.KEEP_DAYS} days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun weekTitle(start: LocalDate, thisWeek: LocalDate): String = when (start) {
    thisWeek -> "This week"
    thisWeek.minusWeeks(1) -> "Last week"
    else -> "${formatShortDate(start)} – ${formatShortDate(start.plusDays(6))}"
}

/** A week's title and kilometres; tapping it folds the week's drives in or out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekHeader(
    week: WeeklySummary,
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrow by animateFloatAsState(if (open) 180f else 0f, label = "arrow")
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                val drives = if (week.driveCount == 1) "1 drive" else "${week.driveCount} drives"
                val others = if (week.otherDriverCount > 0) " · ${week.otherDriverCount} by someone else" else ""
                Text(
                    "$drives · ${formatDuration(week.drivingMillis, zero = "0 min")}$others",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(kmNumber(week.distanceMeters), style = MaterialTheme.typography.titleLarge.tabular())
                Text("km", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (open) "Hide drives" else "Show drives",
                modifier = Modifier.rotate(arrow),
            )
        }
    }
}

/** Drives deleted in the last 30 days, each with how long it has left, ready to restore or delete for good. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecentlyDeletedScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val binned by viewModel.binnedTrips.collectAsStateWithLifecycle()
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val carNames = cars.associate { it.id to it.name }
    var deleting by remember { mutableStateOf<Trip?>(null) }
    var emptying by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Recently deleted") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (binned.isNotEmpty()) TextButton(onClick = { emptying = true }) { Text("Empty") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "about") {
                Text(
                    "Deleted drives stay here for ${DriveBin.KEEP_DAYS} days and don't count in any totals. " +
                        "After that they're removed for good.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (binned.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "Nothing here",
                        body = "Drives you delete land here first, so you can bring them back.",
                        icon = {
                            IconBadge(
                                Icons.Outlined.Delete,
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                                size = 64.dp,
                            )
                        },
                    )
                }
            }
            items(binned, key = { "binned-${it.id}" }) { trip ->
                Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DriveRow(
                        trip,
                        carName = trip.carId?.let { carNames[it] }.takeIf { cars.size > 1 },
                        viewModel = viewModel,
                        onClick = { viewModel.openTrip(trip.id) },
                    )
                    Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val days = DriveBin.daysLeft(trip.deletedAt ?: now, now)
                        Text(
                            "${formatDayAndDate(trip.startTime)} · ${if (days == 1) "1 day" else "$days days"} left",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.restoreTrip(trip.id) }) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restore")
                        }
                        IconButton(onClick = { deleting = trip }) {
                            Icon(
                                Icons.Outlined.DeleteForever,
                                contentDescription = "Delete for good",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    deleting?.let { trip ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null) },
            title = { Text("Delete for good?") },
            text = { Text("The ${formatKm(trip.distanceMeters)} drive and its route can't be brought back after this.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    viewModel.deleteTripForever(trip.id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    if (emptying) {
        AlertDialog(
            onDismissRequest = { emptying = false },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null) },
            title = { Text("Empty Recently deleted?") },
            text = {
                Text(
                    if (binned.size == 1) "1 drive will be deleted for good." else "${binned.size} drives will be deleted for good.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    emptying = false
                    viewModel.emptyBin()
                }) { Text("Empty") }
            },
            dismissButton = { TextButton(onClick = { emptying = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IconBadgeHistory() {
    IconBadge(
        Icons.Rounded.History,
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        size = 64.dp,
    )
}

@Composable
private fun EmptyState(title: String, body: String, icon: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
