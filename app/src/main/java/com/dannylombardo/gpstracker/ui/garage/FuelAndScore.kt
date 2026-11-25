package com.dannylombardo.gpstracker.ui.garage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.garage.DriveAnalysis
import com.dannylombardo.gpstracker.garage.FuelPlan
import com.dannylombardo.gpstracker.ui.AppSwitch
import com.dannylombardo.gpstracker.ui.GlassCard
import com.dannylombardo.gpstracker.ui.GroupedRow
import com.dannylombardo.gpstracker.ui.GroupedSection
import com.dannylombardo.gpstracker.ui.RowDivider
import com.dannylombardo.gpstracker.ui.SectionTitle
import com.dannylombardo.gpstracker.ui.TintedButton
import com.dannylombardo.gpstracker.ui.formatConsumption
import com.dannylombardo.gpstracker.ui.formatDuration
import com.dannylombardo.gpstracker.ui.formatMoney
import com.dannylombardo.gpstracker.ui.formatPricePerLitre
import com.dannylombardo.gpstracker.ui.formatTime
import com.dannylombardo.gpstracker.ui.kmNumber
import com.dannylombardo.gpstracker.ui.theme.apple
import com.dannylombardo.gpstracker.ui.theme.tabular
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Budget, range, your own price history, and the fuel reminders. */
@Composable
internal fun FuelPlannerScreen(vm: GarageViewModel, onBack: () -> Unit) {
    val budget by vm.budget.collectAsStateWithLifecycle()
    val amount by vm.budgetAmount.collectAsStateWithLifecycle()
    val spent by vm.monthSpent.collectAsStateWithLifecycle()
    val garage by vm.garage.collectAsStateWithLifecycle()
    val prices by vm.prices.collectAsStateWithLifecycle()
    val lowFuel by vm.lowFuelReminder.collectAsStateWithLifecycle()
    val budgetAlert by vm.budgetAlert.collectAsStateWithLifecycle()
    var budgetText by rememberSaveable { mutableStateOf(plainNumber(amount)) }

    GarageList(title = "Fuel plan", subtitle = "Budget, range and prices", onBack = onBack) {
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val b = budget
                    val color = when {
                        b == null -> MaterialTheme.apple.orange
                        b.progress >= 1 -> MaterialTheme.apple.red
                        b.overPace -> MaterialTheme.apple.orange
                        else -> MaterialTheme.apple.green
                    }
                    ProgressRing(b?.progress ?: 0.0, color, size = 140.dp, thickness = 14.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatMoney(spent), style = MaterialTheme.typography.titleLarge.tabular())
                            GaugeLabel(if (b != null) "of ${formatMoney(b.budget)}" else "this month")
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (b != null) {
                            Figure(if (b.left >= 0) "Left" else "Over", formatMoney(abs(b.left)), color = if (b.left >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.apple.red)
                            Figure("On pace for", formatMoney(b.projected), color = if (b.overPace) MaterialTheme.apple.orange else MaterialTheme.colorScheme.onSurface)
                            Figure("Days to go", b.daysLeft.toString())
                        } else {
                            Text("Set a budget below to see your pace for the month.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            GroupedSection(header = "Monthly budget", footer = "All cars together. Leave empty for no budget.") {
                InputRow(
                    "Budget",
                    budgetText,
                    {
                        budgetText = it
                        vm.setBudget(parseNumber(it)?.takeIf { v -> v > 0 })
                    },
                    "200",
                    unit = "/ month",
                    number = true,
                )
            }
        }
        garage?.let { g ->
            item { SectionTitle("Range", trailing = g.car.name) }
            item {
                GlassCard {
                    val r = g.range
                    if (r == null) {
                        Text(
                            when {
                                g.spec?.fuelTankLitres == null -> "Add the tank size on the spec sheet to see how far you can go."
                                g.economy.averageLitresPer100Km == null -> "Log two full fill-ups to learn this car's consumption."
                                else -> "Log a full tank to start the countdown."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${r.kmLeft.roundToInt()}", style = MaterialTheme.typography.displaySmall.tabular())
                            Text(" km to empty", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        FuelGaugeBar(r.tankShare)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "About ${String.format(Locale.getDefault(), "%.0f", r.litresLeft)} L left at ${formatConsumption(g.economy.averageLitresPer100Km!!)}. An estimate from your km since the last full tank.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        prices?.takeIf { it.history.size >= 2 }?.let { p ->
            item { SectionTitle("What you pay", trailing = p.average?.let { "avg ${formatPricePerLitre(it)}" }) }
            item {
                GlassCard {
                    PriceChart(p.history.map { it.pricePerLitre }, MaterialTheme.apple.orange, Modifier.fillMaxWidth().height(110.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        p.lowest?.let { Figure("Lowest", formatPricePerLitre(it.pricePerLitre), color = MaterialTheme.apple.green) }
                        p.highest?.let { Figure("Highest", formatPricePerLitre(it.pricePerLitre), color = MaterialTheme.apple.red) }
                        p.latestVsAverage?.let {
                            Figure("Last vs avg", (if (it >= 0) "+" else "") + "${(it * 100).roundToInt()}%", color = if (it > 0) MaterialTheme.apple.red else MaterialTheme.apple.green)
                        }
                    }
                    p.cheapestDay?.let { day ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "You usually pay least on ${day.getDisplayName(TextStyle.FULL, Locale.getDefault())}s" +
                                (p.cheapestDaySaving?.let { " (${formatPricePerLitre(it)} less than your dearest day)" } ?: "") + ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            GroupedSection(header = "Reminders") {
                GroupedRow(
                    title = "Low fuel",
                    subtitle = "After a drive, when the estimated range drops under 80 km",
                    trailing = { AppSwitch(lowFuel, vm::setLowFuelReminder) },
                )
                RowDivider(16.dp)
                GroupedRow(
                    title = "Budget alerts",
                    subtitle = "At 80% of the month's budget and when it's used up",
                    trailing = { AppSwitch(budgetAlert, vm::setBudgetAlert) },
                )
            }
        }
    }
}

@Composable
private fun FuelGaugeBar(share: Double) {
    val red = MaterialTheme.apple.red
    val amber = MaterialTheme.apple.orange
    val green = MaterialTheme.apple.green
    Column {
        ProgressBar(share, if (share < 0.15) red else if (share < 0.35) amber else green, height = 12.dp)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            GaugeLabel("E", Modifier.weight(1f))
            GaugeLabel("½", Modifier.weight(1f))
            GaugeLabel("F")
        }
    }
}

@Composable
internal fun PriceChart(values: List<Double>, color: Color, modifier: Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max().let { if (it - min < 0.01) min + 0.01 else it }
        val step = size.width / (values.size - 1)
        fun y(v: Double) = (size.height - ((v - min) / (max - min) * size.height * 0.85 + size.height * 0.075)).toFloat()
        for (i in 0..2) {
            val gy = size.height * i / 2
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
        }
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * step, y(v)) }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 4.dp.toPx(), Offset(size.width, y(values.last())))
    }
}

/** This week's driving score, what costs points, and every recent drive's score. */
@Composable
internal fun ScoreScreen(vm: GarageViewModel, onBack: () -> Unit, onOpenTrip: (Long) -> Unit) {
    val score by vm.score.collectAsStateWithLifecycle()
    val s = score
    GarageList(title = "Driving score", subtitle = "Smooth is fast", onBack = onBack) {
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing((s?.weekAverage ?: 0) / 100.0, scoreColor(s?.weekAverage), size = 140.dp, thickness = 14.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(s?.weekAverage?.toString() ?: "–", style = MaterialTheme.typography.displaySmall.tabular())
                            GaugeLabel("this week")
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Figure("Last week", s?.lastWeekAverage?.toString() ?: "–")
                        Figure("All time", s?.allTimeAverage?.toString() ?: "–")
                    }
                }
            }
        }
        if (s != null) {
            item {
                GroupedSection(header = "This week", footer = "Hard braking and launches are from GPS speed changes, so only the firm ones show up. Scores are weighted by distance.") {
                    GroupedRow(title = "Hard brakes", value = s.weekHardBrakes.toString())
                    RowDivider(16.dp)
                    GroupedRow(title = "Hard launches", value = s.weekHardAccels.toString())
                    RowDivider(16.dp)
                    GroupedRow(title = "Time over 120 km/h", value = "${s.weekMinutesOver120} min")
                    RowDivider(16.dp)
                    GroupedRow(title = "Driving at night", value = "${(s.weekNightShare * 100).roundToInt()}%")
                }
            }
            item {
                GlassCard {
                    GaugeLabel("How it works")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Every drive starts at 100. Points come off for hard braking (over ${DriveAnalysis.HARD_BRAKE} m/s²) and hard launches, " +
                            "counted per 100 km; for the share of time spent over 120 km/h; and a little for night driving. Look ahead, ease off early, and roll on smoothly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (s.recent.isNotEmpty()) item { SectionTitle("Recent drives") }
            items(s.recent, key = { it.trip.id }) { d ->
                GlassCard(onClick = { onOpenTrip(d.trip.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val sc = d.insight.score
                        ProgressRing((sc ?: 0) / 100.0, scoreColor(sc), size = 48.dp, thickness = 5.dp) {
                            Text(sc?.toString() ?: "–", style = MaterialTheme.typography.titleSmall.tabular())
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${shortDate(d.trip.startTime)} · ${formatTime(d.trip.startTime)}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(
                                    "${kmNumber(d.trip.distanceMeters)} km",
                                    formatDuration((d.trip.endTime ?: d.trip.startTime) - d.trip.startTime),
                                    d.insight.hardBrakes.takeIf { it > 0 }?.let { "$it hard brakes" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
