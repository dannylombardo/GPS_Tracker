package com.dannylombardo.gpstracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dannylombardo.gpstracker.data.EveryoneWeek
import com.dannylombardo.gpstracker.ui.theme.apple
import com.dannylombardo.gpstracker.ui.theme.tabular
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Everything driven this week by anyone, in any car: the total, how it splits between you
 * and someone else, a bar per day and each car's share. Your own totals in the card above
 * still leave other people's drives out; this is the whole picture.
 */
@Composable
internal fun EveryoneCard(everyone: EveryoneWeek, showCars: Boolean, todayIndex: Int?) {
    val you = MaterialTheme.colorScheme.primary
    val others = MaterialTheme.apple.orange
    GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Total driven, everyone",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CountingKm(everyone.totalMeters, style = MaterialTheme.typography.displaySmall)
                }
                Text(
                    if (everyone.driveCount == 1) "1 drive" else "${everyone.driveCount} drives",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            SplitBar(everyone.mineMeters, everyone.othersMeters, you, others, height = 12.dp)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend("You", formatKm(everyone.mineMeters), everyone.mineCount, you, Modifier.weight(1f))
                Legend("Someone else", formatKm(everyone.othersMeters), everyone.othersCount, others, Modifier.weight(1f))
            }

            EveryoneDays(everyone.dailyMeters, todayIndex, you)

            if (showCars && everyone.byCar.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Text("By car", style = MaterialTheme.typography.titleSmall)
                    everyone.byCar.forEach { share ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    share.car.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(formatKm(share.totalMeters), style = MaterialTheme.typography.titleSmall.tabular())
                            }
                            SplitBar(share.mineMeters, share.othersMeters, you, others, height = 6.dp)
                        }
                    }
                }
            }
        }
    }
}

/** A capsule split into your share and someone else's, growing in from the left. */
@Composable
private fun SplitBar(mine: Double, others: Double, you: Color, other: Color, height: Dp) {
    val total = mine + others
    val growth = remember { Animatable(0f) }
    LaunchedEffect(mine, others) {
        growth.snapTo(0f)
        growth.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.apple.fill),
    ) {
        if (total > 0) {
            Row(Modifier.fillMaxWidth(growth.value).fillMaxHeight()) {
                if (mine > 0) Box(Modifier.weight(mine.toFloat()).fillMaxHeight().background(you))
                if (mine > 0 && others > 0) Spacer(Modifier.width(2.dp))
                if (others > 0) Box(Modifier.weight(others.toFloat()).fillMaxHeight().background(other))
            }
        }
    }
}

@Composable
private fun Legend(label: String, km: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(km, style = MaterialTheme.typography.titleMedium.tabular())
            Text(
                "$label · ${if (count == 1) "1 drive" else "$count drives"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A small bar per day of everyone's kilometres, Monday first, today in the accent colour. */
@Composable
private fun EveryoneDays(daily: List<Double>, todayIndex: Int?, accent: Color) {
    val max = daily.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val days = remember { DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, Locale.getDefault()) } }
    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        daily.forEachIndexed { index, meters ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(((meters / max).toFloat()).coerceIn(0.06f, 1f))
                            .background(
                                when {
                                    meters <= 0 -> MaterialTheme.apple.fill
                                    index == todayIndex -> accent
                                    else -> accent.copy(alpha = 0.45f)
                                },
                                RoundedCornerShape(5.dp),
                            ),
                    )
                }
                Text(
                    days[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == todayIndex) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
