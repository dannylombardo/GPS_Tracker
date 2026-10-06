package com.dannylombardo.gpstracker.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.data.DailySummary
import com.dannylombardo.gpstracker.data.Trip
import com.dannylombardo.gpstracker.ui.theme.HeroColors
import java.time.LocalDate
import java.time.ZoneId

/**
 * One day on its own, opened from a bar on the week's graph: the day's kilometres and
 * other figures, when the drives happened, then each drive and fill-up. It follows the car chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val day by viewModel.day.collectAsStateWithLifecycle()
    val cars by viewModel.cars.collectAsStateWithLifecycle()
    val viewedCarId by viewModel.viewedCarId.collectAsStateWithLifecycle()
    val fuelUpStats by viewModel.fuelUpStats.collectAsStateWithLifecycle()
    val showCarNames = cars.size > 1 && viewedCarId == null
    val carNames = cars.associate { it.id to it.name }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(day?.let { formatDayHeading(it.date) } ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val summary = day ?: return@Scaffold
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
            item(key = "cars") { CarFilterRow(cars, viewedCarId, onSelect = viewModel::viewCar, onManage = viewModel::openCars) }
            item(key = "hero") {
                DayCard(
                    summary,
                    isToday = summary.date >= LocalDate.now(),
                    onPrevious = { viewModel.shiftDay(-1) },
                    onNext = { viewModel.shiftDay(1) },
                )
            }
            item(key = "drives-header") { SectionHeader("Drives") }
            if (summary.trips.isEmpty()) {
                item(key = "empty") { NoDrivesThatDay() }
            } else {
                items(summary.trips, key = { "trip-${it.id}" }) { trip ->
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
            if (summary.fuelUps.isNotEmpty()) {
                item(key = "fuel-header") { SectionHeader("Fill-ups") }
                items(summary.fuelUps, key = { "fuel-${it.id}" }) { fuelUp ->
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
    }
}

/** The day's headline card, in the same style as the week's, with arrows to step through days. */
@Composable
private fun DayCard(summary: DailySummary, isToday: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    val onHero = HeroColors.content
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(HeroColors.drive)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDayAndDate(summary.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()),
                    style = MaterialTheme.typography.titleMedium,
                    color = onHero,
                    modifier = Modifier.weight(1f),
                )
                val arrowColors = IconButtonDefaults.iconButtonColors(
                    contentColor = onHero,
                    disabledContentColor = onHero.copy(alpha = 0.3f),
                )
                IconButton(onClick = onPrevious, colors = arrowColors) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous day")
                }
                IconButton(onClick = onNext, enabled = !isToday, colors = arrowColors) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next day")
                }
            }

            BigNumber(kmNumber(summary.distanceMeters), "km", color = onHero, style = MaterialTheme.typography.displayLarge)

            DayTimeline(summary.trips, summary.date, onHero)

            Row(Modifier.fillMaxWidth()) {
                HeroStat("Drives", summary.driveCount.toString(), onHero, Modifier.weight(1f))
                HeroStat("Time", formatDuration(summary.drivingMillis, zero = "0 min"), onHero, Modifier.weight(1f))
                HeroStat("Avg speed", formatSpeed(summary.averageSpeedMetersPerSecond), onHero, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                HeroStat("Top speed", formatSpeed(summary.topSpeedMetersPerSecond), onHero, Modifier.weight(1f))
                HeroStat("Spent on fuel", formatMoney(summary.moneySpent), onHero, Modifier.weight(1f))
                HeroStat("Litres", formatLitres(summary.litresBought), onHero, Modifier.weight(1f))
            }
            if (summary.otherDriverCount > 0) {
                Text(
                    "${summary.otherDriverCount} driven by someone else, not counted",
                    style = MaterialTheme.typography.bodySmall,
                    color = onHero.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/** Midnight to midnight, with a block for each drive where it happened. Someone else's drives are fainter. */
@Composable
private fun DayTimeline(trips: List<Trip>, date: LocalDate, color: Color) {
    val zone = ZoneId.systemDefault()
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayLength = (date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - dayStart).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(color.copy(alpha = 0.12f), cornerRadius = radius)
            listOf(0.25f, 0.5f, 0.75f).forEach { f ->
                drawLine(
                    color.copy(alpha = 0.25f),
                    Offset(size.width * f, 0f),
                    Offset(size.width * f, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            trips.forEach { trip ->
                val from = ((trip.startTime - dayStart) / dayLength).coerceIn(0f, 1f) * size.width
                val until = (((trip.endTime ?: trip.startTime) - dayStart) / dayLength).coerceIn(0f, 1f) * size.width
                // At least a dot, so a five-minute drive still shows.
                val width = (until - from).coerceAtLeast(size.height)
                drawRoundRect(
                    color.copy(alpha = if (trip.countsAsMine) 0.95f else 0.4f),
                    topLeft = Offset(from.coerceAtMost(size.width - width), 0f),
                    size = Size(width, size.height),
                    cornerRadius = radius,
                )
            }
        }
        // Each label centred under its tick at a quarter, half and three quarters of the way.
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(0.125f))
            listOf(6, 12, 18).forEach { hour ->
                Text(
                    formatTime(date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()),
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(0.25f),
                )
            }
            Spacer(Modifier.weight(0.125f))
        }
    }
}

@Composable
private fun NoDrivesThatDay() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconBadge(
            Icons.Rounded.DirectionsCar,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 64.dp,
        )
        Text("No drives this day", style = MaterialTheme.typography.titleMedium)
    }
}
