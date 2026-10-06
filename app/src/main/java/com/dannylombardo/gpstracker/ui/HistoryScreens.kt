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
import androidx.compose.material.icons.rounded.LocalGasStation
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
import com.dannylombardo.gpstracker.data.FuelUp
import com.dannylombardo.gpstracker.data.Trip
import com.dannylombardo.gpstracker.data.WeeklySummary
import com.dannylombardo.gpstracker.ui.theme.tabular
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

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
    val binnedCount = binCount(viewModel)
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
                actions = { BinButton(binnedCount, onClick = viewModel::openBin) },
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
@Composable
private fun WeekHeader(
    week: WeeklySummary,
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drives = if (week.driveCount == 1) "1 drive" else "${week.driveCount} drives"
    val others = if (week.otherDriverCount > 0) " · ${week.otherDriverCount} by someone else" else ""
    FoldHeader(
        title = title,
        detail = "$drives · ${formatDuration(week.drivingMillis, zero = "0 min")}$others",
        value = kmNumber(week.distanceMeters),
        unit = "km",
        open = open,
        onToggle = onToggle,
        modifier = modifier,
    )
}

/** A section title with its headline figure; tapping it folds the section in or out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoldHeader(
    title: String,
    detail: String,
    value: String,
    unit: String?,
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
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(value, style = MaterialTheme.typography.titleLarge.tabular())
                if (unit != null) Text(unit, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (open) "Hide" else "Show",
                modifier = Modifier.rotate(arrow),
            )
        }
    }
}

/** How many drives and fill-ups are waiting in Recently deleted. */
@Composable
private fun binCount(viewModel: MainViewModel): Int {
    val trips by viewModel.binnedTrips.collectAsStateWithLifecycle()
    val fuelUps by viewModel.binnedFuelUps.collectAsStateWithLifecycle()
    return trips.size + fuelUps.size
}

/** The top bar's way into Recently deleted, with a count when something's in it. */
@Composable
private fun BinButton(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
            Icon(Icons.Outlined.Delete, contentDescription = "Recently deleted")
        }
    }
}

/**
 * Every fill-up, a section per month with its cost, litres and count, newest first. This
 * month and last start open; older ones fold away until tapped. [expanded] holds the
 * months opened or closed by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AllFillUpsScreen(
    viewModel: MainViewModel,
    listState: LazyListState,
    expanded: SnapshotStateMap<YearMonth, Boolean>,
    onBack: () -> Unit,
) {
    val months by viewModel.fuelHistory.collectAsStateWithLifecycle()
    val fuelUpStats by viewModel.fuelUpStats.collectAsStateWithLifecycle()
    val binnedCount = binCount(viewModel)
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val viewedCarId by viewModel.viewedCarId.collectAsStateWithLifecycle()
    val showCarNames = cars.size > 1 && viewedCarId == null
    val carNames = cars.associate { it.id to it.name }
    val thisMonth = YearMonth.now()
    val monthFormat = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("All fill-ups") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { BinButton(binnedCount, onClick = viewModel::openBin) },
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
            if (months.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No fill-ups yet",
                        body = "Every fill-up you log ends up here, sorted by month.",
                        icon = {
                            IconBadge(
                                Icons.Rounded.LocalGasStation,
                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                content = MaterialTheme.colorScheme.onTertiaryContainer,
                                size = 64.dp,
                            )
                        },
                    )
                }
            }
            months.forEach { month ->
                val open = expanded[month.month] ?: (month.month >= thisMonth.minusMonths(1))
                item(key = "month-${month.month}") {
                    val count = month.fuelUps.size
                    FoldHeader(
                        title = when (month.month) {
                            thisMonth -> "This month"
                            thisMonth.minusMonths(1) -> "Last month"
                            else -> month.month.format(monthFormat)
                        },
                        detail = "${if (count == 1) "1 fill-up" else "$count fill-ups"} · ${formatLitres(month.litres)}",
                        value = formatMoney(month.cost),
                        unit = null,
                        open = open,
                        onToggle = { expanded[month.month] = !open },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (open) {
                    items(month.fuelUps, key = { "fuel-${it.id}" }) { fuelUp ->
                        SwipeToBin(onBin = { viewModel.moveFuelUpToBin(fuelUp.id) }, modifier = Modifier.animateItem()) {
                            FuelUpRow(
                                fuelUp,
                                fuelUpStats[fuelUp.id],
                                carName = fuelUp.carId?.let { carNames[it] }.takeIf { showCarNames },
                                onClick = { viewModel.openFuelUp(fuelUp) },
                            )
                        }
                    }
                }
            }
            if (months.isNotEmpty()) {
                item(key = "hint") {
                    Text(
                        "Swipe a fill-up sideways to delete it. Deleted fill-ups wait in Recently deleted for " +
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

/** Drives and fill-ups deleted in the last 30 days, each with how long it has left, ready to restore or delete for good. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecentlyDeletedScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val binned by viewModel.binnedTrips.collectAsStateWithLifecycle()
    val binnedFuelUps by viewModel.binnedFuelUps.collectAsStateWithLifecycle()
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val carNames = cars.associate { it.id to it.name }
    val total = binned.size + binnedFuelUps.size
    var deleting by remember { mutableStateOf<Trip?>(null) }
    var deletingFuelUp by remember { mutableStateOf<FuelUp?>(null) }
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
                    if (total > 0) TextButton(onClick = { emptying = true }) { Text("Empty") }
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
                    "Deleted drives and fill-ups stay here for ${DriveBin.KEEP_DAYS} days and don't count in any totals. " +
                        "After that they're removed for good.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (total == 0) {
                item(key = "empty") {
                    EmptyState(
                        title = "Nothing here",
                        body = "Drives and fill-ups you delete land here first, so you can bring them back.",
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
            if (binned.isNotEmpty() && binnedFuelUps.isNotEmpty()) item(key = "drives") { SectionHeader("Drives") }
            items(binned, key = { "binned-${it.id}" }) { trip ->
                Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DriveRow(
                        trip,
                        carName = trip.carId?.let { carNames[it] }.takeIf { cars.size > 1 },
                        viewModel = viewModel,
                        onClick = { viewModel.openTrip(trip.id) },
                    )
                    BinActions(
                        label = formatDayAndDate(trip.startTime),
                        daysLeft = DriveBin.daysLeft(trip.deletedAt ?: now, now),
                        onRestore = { viewModel.restoreTrip(trip.id) },
                        onDelete = { deleting = trip },
                    )
                }
            }
            if (binnedFuelUps.isNotEmpty()) {
                if (binned.isNotEmpty()) item(key = "fill-ups") { SectionHeader("Fill-ups") }
                items(binnedFuelUps, key = { "binned-fuel-${it.id}" }) { fuelUp ->
                    Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FuelUpRow(
                            fuelUp,
                            stats = null,
                            carName = fuelUp.carId?.let { carNames[it] }.takeIf { cars.size > 1 },
                            onClick = null,
                        )
                        BinActions(
                            label = null,
                            daysLeft = DriveBin.daysLeft(fuelUp.deletedAt ?: now, now),
                            onRestore = { viewModel.restoreFuelUp(fuelUp.id) },
                            onDelete = { deletingFuelUp = fuelUp },
                        )
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

    deletingFuelUp?.let { fuelUp ->
        AlertDialog(
            onDismissRequest = { deletingFuelUp = null },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null) },
            title = { Text("Delete for good?") },
            text = { Text("The ${formatMoney(fuelUp.totalCost)} fill-up can't be brought back after this.") },
            confirmButton = {
                TextButton(onClick = {
                    deletingFuelUp = null
                    viewModel.deleteFuelUpForever(fuelUp.id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingFuelUp = null }) { Text("Cancel") } },
        )
    }

    if (emptying) {
        AlertDialog(
            onDismissRequest = { emptying = false },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null) },
            title = { Text("Empty Recently deleted?") },
            text = {
                val parts = listOfNotNull(
                    binned.size.takeIf { it > 0 }?.let { if (it == 1) "1 drive" else "$it drives" },
                    binnedFuelUps.size.takeIf { it > 0 }?.let { if (it == 1) "1 fill-up" else "$it fill-ups" },
                )
                Text("${parts.joinToString(" and ")} will be deleted for good.")
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

/** Under a binned item: when it's from, how long it has left, and the buttons to restore or delete it for good. */
@Composable
private fun BinActions(label: String?, daysLeft: Int, onRestore: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val left = "${if (daysLeft == 1) "1 day" else "$daysLeft days"} left"
        Text(
            label?.let { "$it · $left" } ?: left,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRestore) {
            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Restore")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.DeleteForever,
                contentDescription = "Delete for good",
                tint = MaterialTheme.colorScheme.error,
            )
        }
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
