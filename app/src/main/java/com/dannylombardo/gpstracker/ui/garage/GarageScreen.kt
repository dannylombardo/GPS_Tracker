package com.dannylombardo.gpstracker.ui.garage

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.data.Car
import com.dannylombardo.gpstracker.garage.Maintenance
import com.dannylombardo.gpstracker.garage.Recap
import com.dannylombardo.gpstracker.ui.GlassCard
import com.dannylombardo.gpstracker.ui.HeroCard
import com.dannylombardo.gpstracker.ui.LargeTitle
import com.dannylombardo.gpstracker.ui.SectionTitle
import com.dannylombardo.gpstracker.ui.formatConsumption
import com.dannylombardo.gpstracker.ui.formatDuration
import com.dannylombardo.gpstracker.ui.formatMoney
import com.dannylombardo.gpstracker.ui.formatSpeed
import com.dannylombardo.gpstracker.ui.kmNumber
import com.dannylombardo.gpstracker.ui.theme.apple
import com.dannylombardo.gpstracker.ui.theme.tabular
import java.time.YearMonth
import kotlin.math.roundToInt

/** Garage colours: carbon for the car card, a deep red for recaps. Same in light and dark. */
internal object GarageColors {
    val carbon = listOf(Color(0xFF2B2B31), Color(0xFF0E0E11))
    val racing = listOf(Color(0xFFE5251B), Color(0xFF8E0D0D))
    val live = listOf(Color(0xFF1FBF5A), Color(0xFF0C6B31))
    val night = listOf(Color(0xFF3A2C8C), Color(0xFF120E36))
    val sunset = listOf(Color(0xFFFF7A18), Color(0xFFAF002D))
    val ocean = listOf(Color(0xFF0FB5C9), Color(0xFF103E9C))
    val gold = listOf(Color(0xFFF7B733), Color(0xFFB9590B))
}

/** The Garage tab: your car, its upkeep and running costs, your driving, and your data. */
@Composable
internal fun GarageScreen(vm: GarageViewModel, listState: LazyListState, padding: PaddingValues) {
    val cars by vm.cars.collectAsStateWithLifecycle()
    val carId by vm.carId.collectAsStateWithLifecycle()
    val garage by vm.garage.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val monthSpent by vm.monthSpent.collectAsStateWithLifecycle()
    val score by vm.score.collectAsStateWithLifecycle()
    val bests by vm.bests.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val periods by vm.recapPeriods.collectAsStateWithLifecycle()

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
        item { LargeTitle("Garage", subtitle = garage?.spec?.title ?: garage?.car?.name) }

        live?.let { drive ->
            item(key = "live") {
                LiveBanner(
                    speedKmh = drive.speedMetersPerSecond?.let { (it * 3.6).roundToInt() },
                    km = drive.distanceMeters / 1000,
                    onClick = { vm.open(GaragePage.Live) },
                )
            }
        }

        if (cars.size > 1) {
            item(key = "cars") { CarPicker(cars, carId, vm::pickCar) }
        }

        garage?.let { g ->
            item(key = "car") {
                CarCard(g, onEdit = { vm.open(GaragePage.Spec) })
            }
        }

        item(key = "upkeep-title") {
            SectionTitle("Upkeep", action = "See All", onAction = { vm.open(GaragePage.Maintenance) })
        }
        item(key = "upkeep") {
            MaintenancePreview(garage?.maintenance.orEmpty(), onClick = { vm.open(GaragePage.Maintenance) })
        }

        item(key = "fuel-title") {
            SectionTitle("Fuel", action = "Plan", onAction = { vm.open(GaragePage.FuelPlanner) })
        }
        item(key = "fuel") {
            FuelPreview(
                budget = budget,
                monthSpent = monthSpent,
                rangeKm = garage?.range?.kmLeft,
                tankShare = garage?.range?.tankShare,
                consumption = garage?.economy?.averageLitresPer100Km,
                onClick = { vm.open(GaragePage.FuelPlanner) },
            )
        }

        item(key = "drive-title") {
            SectionTitle("Your driving", action = "Details", onAction = { vm.open(GaragePage.Score) })
        }
        item(key = "score") {
            ScorePreview(score, onClick = { vm.open(GaragePage.Score) })
        }
        item(key = "records") {
            RecordsStrip(bests, badges.count { it.unlocked }, badges.size, onClick = { vm.open(GaragePage.Records) })
        }

        item(key = "wrapped-title") { SectionTitle("Recaps") }
        item(key = "wrapped") {
            RecapStrip(periods.first.take(6), periods.second.take(3), onOpen = { vm.open(GaragePage.Wrapped(it)) })
        }

        item(key = "places") {
            val p = places
            GlassCard(onClick = { vm.open(GaragePage.Places) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Place, MaterialTheme.apple.teal)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Places & commute", style = MaterialTheme.typography.titleMedium)
                        val commute = p?.toWork
                        Text(
                            when {
                                commute != null -> "Home to work: ${formatDuration(commute.averageMillis)} on average"
                                p != null && p.places.isNotEmpty() -> "${p.places.size} regular spots found"
                                else -> "Your regular spots show up after a few drives"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item(key = "data") {
            GlassCard(onClick = { vm.open(GaragePage.Data) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Folder, MaterialTheme.apple.indigo)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Export & backup", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "CSV, PDF report, backup and restore",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun IconTile(icon: ImageVector, color: Color, size: Int = 36) {
    Box(
        Modifier.size(size.dp).background(color, RoundedCornerShape((size / 4).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size((size * 0.58).dp))
    }
}

/** A pulsing green banner while a drive is being recorded, opening the live dash. */
@Composable
private fun LiveBanner(speedKmh: Int?, km: Double, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val dot by pulse.animateFloat(0.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "dot")
    HeroCard(GarageColors.live, modifier = Modifier.clickable(onClick = onClick), watermark = Icons.Rounded.Speed) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).alpha(dot).background(Color.White, CircleShape))
            Spacer(Modifier.width(8.dp))
            GaugeLabel("Live · tap for the dash", color = Color.White.copy(alpha = 0.9f))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                speedKmh?.toString() ?: "–",
                style = MaterialTheme.typography.displayMedium.tabular(),
                color = Color.White,
            )
            Text(" km/h", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            Text("${kmNumber(km * 1000)} km", style = MaterialTheme.typography.titleLarge.tabular(), color = Color.White, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun CarPicker(cars: List<Car>, selected: Long?, onPick: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cars.forEach { car ->
            val on = car.id == selected
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.apple.fill)
                    .clickable { onPick(car.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.DirectionsCar,
                    contentDescription = null,
                    tint = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    car.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** The car's spec sheet card: name, odometer drums, and the headline specs. */
@Composable
private fun CarCard(g: CarGarage, onEdit: () -> Unit) {
    HeroCard(GarageColors.carbon, modifier = Modifier.clickable(onClick = onEdit), watermark = Icons.Rounded.DirectionsCar) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                GaugeLabel(g.spec?.title?.let { g.car.name } ?: "Your car", color = Color(0xFFFF4D40))
                Text(
                    g.spec?.title ?: g.car.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (g.spec == null) "Add specs" else "Specs", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GaugeLabel(if (g.spec?.odometerKm != null) "Odometer" else "Tracked by the app", color = Color.White.copy(alpha = 0.6f))
            OdometerDigits(g.odometerKm)
        }
        val spec = g.spec
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            spec?.horsepower?.let { DarkFigure("Power", "$it hp") }
            spec?.torqueNm?.let { DarkFigure("Torque", "$it Nm") }
            g.economy.averageLitresPer100Km?.let { DarkFigure("Average", formatConsumption(it)) }
            if (spec?.horsepower == null) DarkFigure("Drives", g.driveCount.toString())
            if (g.maintenanceSpent > 0) DarkFigure("Upkeep", formatMoney(g.maintenanceSpent))
        }
    }
}

@Composable
private fun DarkFigure(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium.tabular(), color = Color.White, maxLines = 1)
        GaugeLabel(label, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun MaintenancePreview(statuses: List<Maintenance.Status>, onClick: () -> Unit) {
    GlassCard(onClick = onClick) {
        if (statuses.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.Build, MaterialTheme.apple.orange)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Track oil, tires and more", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Counts down by the kilometres this car actually drives",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@GlassCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            statuses.take(3).forEach { s -> MaintenanceLine(s) }
        }
    }
}

@Composable
internal fun MaintenanceLine(s: Maintenance.Status) {
    val color = s.state.color()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.item.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(dueText(s), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = color)
        }
        if (s.progress != null) ProgressBar(s.progress, color, height = 6.dp)
    }
}

internal fun dueText(s: Maintenance.Status): String {
    val km = s.kmLeft
    val days = s.daysLeft
    val progress = s.progress ?: return "No interval"
    // Whichever limit is closer decides what's shown.
    val kmRules = km != null && s.item.intervalKm != null && s.kmSince / s.item.intervalKm >= progress - 1e-9
    return when {
        kmRules && km!! < 0 -> "${formatKmWhole(-km)} overdue"
        !kmRules && days != null && days < 0 -> "${-days} days overdue"
        s.state == Maintenance.State.DUE -> "Due now"
        kmRules -> "${formatKmWhole(km!!)} left"
        days != null && days > 60 -> "${days / 30} months left"
        days != null -> "$days days left"
        else -> ""
    }
}

@Composable
private fun FuelPreview(
    budget: com.dannylombardo.gpstracker.garage.FuelPlan.Budget?,
    monthSpent: Double,
    rangeKm: Double?,
    tankShare: Double?,
    consumption: Double?,
    onClick: () -> Unit,
) {
    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ringColor = when {
                budget == null -> MaterialTheme.apple.orange
                budget.progress >= 1 -> MaterialTheme.apple.red
                budget.overPace -> MaterialTheme.apple.orange
                else -> MaterialTheme.apple.green
            }
            ProgressRing(budget?.progress ?: 0.0, ringColor, size = 96.dp, thickness = 10.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        budget?.let { "${(it.progress * 100).roundToInt()}%" } ?: "–",
                        style = MaterialTheme.typography.titleLarge.tabular(),
                    )
                    GaugeLabel("budget")
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Figure("Spent this month", formatMoney(monthSpent))
                when {
                    budget != null -> Figure(
                        if (budget.left >= 0) "Left · ${budget.daysLeft} days to go" else "Over budget",
                        formatMoney(kotlin.math.abs(budget.left)),
                        color = if (budget.left >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.apple.red,
                    )
                    else -> Text(
                        "Set a monthly budget",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (rangeKm != null && tankShare != null) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocalGasStation, contentDescription = null, tint = MaterialTheme.apple.orange, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("About ${formatKmWhole(rangeKm)} to empty", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                consumption?.let {
                    Text(formatConsumption(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(tankShare, if (tankShare < 0.15) MaterialTheme.apple.red else MaterialTheme.apple.orange)
        }
    }
}

@Composable
private fun ScorePreview(score: ScoreSummary?, onClick: () -> Unit) {
    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val week = score?.weekAverage
            ProgressRing((week ?: 0) / 100.0, scoreColor(week), size = 96.dp, thickness = 10.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(week?.toString() ?: "–", style = MaterialTheme.typography.headlineSmall.tabular())
                    GaugeLabel("score")
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Driving score this week", style = MaterialTheme.typography.titleMedium)
                val last = score?.lastWeekAverage
                Text(
                    when {
                        week == null -> "Scores appear after drives of 1 km or more."
                        last == null -> "Smoothness, braking and speed, out of 100."
                        week > last -> "Up ${week - last} on last week. Nice and smooth."
                        week < last -> "Down ${last - week} on last week."
                        else -> "Same as last week."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (score != null && week != null) {
                    Text(
                        "${score.weekHardBrakes} hard brakes · ${score.weekHardAccels} hard launches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordsStrip(bests: com.dannylombardo.gpstracker.garage.Records.Bests?, unlocked: Int, total: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RecordTile(Icons.Rounded.EmojiEvents, MaterialTheme.apple.yellow, "Badges", "$unlocked/$total", onClick)
        RecordTile(Icons.Rounded.Speed, MaterialTheme.apple.red, "Top speed", bests?.topSpeed?.let { formatSpeed(it.value) } ?: "–", onClick)
        RecordTile(Icons.Rounded.Timer, MaterialTheme.colorScheme.primary, "0-100 est.", bests?.best0to100?.let { formatSeconds(it.value) } ?: "–", onClick)
        RecordTile(Icons.Rounded.DirectionsCar, MaterialTheme.apple.blue, "Longest drive", bests?.longestDrive?.let { "${kmNumber(it.value)} km" } ?: "–", onClick)
        RecordTile(Icons.Rounded.AutoAwesome, MaterialTheme.apple.orange, "Streak", bests?.let { "${it.currentStreakDays} days" } ?: "–", onClick)
    }
}

@Composable
private fun RecordTile(icon: ImageVector, color: Color, label: String, value: String, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.width(132.dp), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        IconTile(icon, color, size = 30)
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.titleMedium.tabular(), maxLines = 1)
        GaugeLabel(label)
    }
}

@Composable
private fun RecapStrip(months: List<YearMonth>, years: List<Int>, onOpen: (Recap.Period) -> Unit) {
    if (months.isEmpty()) {
        GlassCard {
            Text("Your first recap appears after your first drive.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val palettes = listOf(GarageColors.racing, GarageColors.night, GarageColors.ocean, GarageColors.sunset, GarageColors.gold)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val periods = years.map { Recap.Period.Year(it) } + months.map { Recap.Period.Month(it) }
        periods.forEachIndexed { i, period ->
            Box(
                Modifier
                    .width(if (period is Recap.Period.Year) 150.dp else 128.dp)
                    .height(170.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(palettes[i % palettes.size]))
                    .clickable { onOpen(period) }
                    .padding(14.dp),
            ) {
                Column(Modifier.align(Alignment.TopStart)) {
                    GaugeLabel(if (period is Recap.Period.Year) "Year wrapped" else "Month wrapped", color = Color.White.copy(alpha = 0.8f))
                }
                Text(
                    when (period) {
                        is Recap.Period.Year -> period.year.toString()
                        is Recap.Period.Month -> period.month.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) +
                            "\n" + period.month.year
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
    }
}
