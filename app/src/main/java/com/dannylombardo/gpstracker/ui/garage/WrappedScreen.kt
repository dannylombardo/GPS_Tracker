package com.dannylombardo.gpstracker.ui.garage

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.garage.Recap
import com.dannylombardo.gpstracker.ui.formatConsumption
import com.dannylombardo.gpstracker.ui.formatMoney
import com.dannylombardo.gpstracker.ui.formatSpeed
import com.dannylombardo.gpstracker.ui.theme.tabular
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private class Story(val colors: List<Color>, val content: @Composable () -> Unit)

/** A month or year of driving as swipeable story cards, tap left or right to move. */
@Composable
internal fun WrappedScreen(vm: GarageViewModel, period: Recap.Period, onBack: () -> Unit) {
    val flow = remember(period) { vm.recap(period) }
    val recap by flow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val r = recap
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0E))) {
        if (r == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            return@Box
        }
        val stories = remember(r) { stories(r) }
        val pager = rememberPagerState { stories.size }
        val scope = rememberCoroutineScope()
        HorizontalPager(pager, Modifier.fillMaxSize()) { index ->
            val story = stories[index]
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(story.colors))
                    .pointerInput(index) {
                        detectTapGestures { offset ->
                            scope.launch {
                                if (offset.x < size.width / 3) {
                                    pager.animateScrollToPage((index - 1).coerceAtLeast(0))
                                } else {
                                    pager.animateScrollToPage((index + 1).coerceAtMost(stories.size - 1))
                                }
                            }
                        }
                    }
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 90.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                story.content()
            }
        }
        // Story progress bars, close and share along the top.
        Column(Modifier.statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(stories.size) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i <= pager.currentPage) 0.95f else 0.3f)),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${period.title} · Wrapped",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                RoundButton(Icons.Rounded.IosShare, "Share") {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(r))
                    context.startActivity(Intent.createChooser(send, "Share your recap"))
                }
                Spacer(Modifier.width(8.dp))
                RoundButton(Icons.Rounded.Close, "Close", onBack)
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.25f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

private fun km(meters: Double) = String.format(Locale.getDefault(), "%,.0f", meters / 1000)

private fun hours(millis: Long): String {
    val h = millis / 3_600_000.0
    return if (h >= 10) String.format(Locale.getDefault(), "%.0f", h) else String.format(Locale.getDefault(), "%.1f", h)
}

private fun shareText(r: Recap): String = buildString {
    append("My ${r.period.title} behind the wheel: ${km(r.distanceMeters)} km over ${r.driveCount} drives")
    append(", ${hours(r.drivingMillis)} hours of seat time")
    r.topSpeed?.let { append(", top speed ${formatSpeed(it)}") }
    r.averageScore?.let { append(", driving score $it/100") }
    append(".")
}

private fun stories(r: Recap): List<Story> {
    val isYear = r.period is Recap.Period.Year
    val list = mutableListOf<Story>()
    list += Story(GarageColors.racing) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Kicker("Your ${r.period.title}")
            Huge("Wrapped.")
            Body(if (r.driveCount == 0) "No drives this time. Next one's yours." else "Buckle up. Here's how your ${if (isYear) "year" else "month"} on the road went.")
        }
    }
    if (r.driveCount == 0) return list
    list += Story(GarageColors.night) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Kicker("You drove")
            CountUp(r.distanceMeters / 1000, suffix = " km")
            val laps = r.earthLaps
            Body(
                if (laps >= 1) String.format(Locale.getDefault(), "That's %.1f times around the Earth.", laps)
                else "That's ${(laps * 100).roundToInt().coerceAtLeast(1)}% of the way around the Earth.",
            )
            r.changeVsPrevious?.let {
                val pct = (it * 100).roundToInt()
                Body(
                    when {
                        pct > 0 -> "$pct% more than the ${if (isYear) "year" else "month"} before."
                        pct < 0 -> "${-pct}% less than the ${if (isYear) "year" else "month"} before."
                        else -> "Exactly the same as before. Consistent."
                    },
                )
            }
        }
    }
    list += Story(GarageColors.ocean) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Kicker("Seat time")
            Huge("${hours(r.drivingMillis)} h")
            Body("Across ${r.driveCount} drives on ${r.activeDays} different days.")
            r.averageSpeed?.let { Body("Averaging ${formatSpeed(it)}, stops and all.") }
            Buckets(r.buckets, if (isYear) "Jan" to "Dec" else "1st" to "${r.buckets.size}th")
        }
    }
    if (r.topSpeed != null || r.longestDriveMeters != null) {
        list += Story(GarageColors.sunset) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                r.topSpeed?.let {
                    Kicker("Top speed")
                    Huge(formatSpeed(it))
                    r.topSpeedTime?.let { t -> Body("Set on ${shortDate(t)}.") }
                    Spacer(Modifier.height(18.dp))
                }
                r.longestDriveMeters?.let {
                    Kicker("Longest drive")
                    Big("${km(it)} km")
                    r.longestDriveTime?.let { t -> Body("On ${shortDate(t)}. Road trip energy.") }
                }
            }
        }
    }
    list += Story(GarageColors.night) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Kicker("Your habits")
            r.busiestDay?.let { Big("${it.getDisplayName(TextStyle.FULL, Locale.getDefault())}s") }
            Body("were your biggest driving day.")
            r.favouriteHour?.let { Body("You hit the road most around ${it}:00.") }
            if (r.nightDrives > 0) Body("${r.nightDrives} drives after dark. Night rider.")
            r.topPlace?.let { Body("Favourite spot that isn't home or work: $it.") }
        }
    }
    if (r.fillUps > 0) {
        list += Story(GarageColors.gold) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kicker("At the pump")
                Huge(formatMoney(r.moneySpent))
                Body(String.format(Locale.getDefault(), "%.0f litres over %d fill-ups.", r.litres, r.fillUps))
                r.litresPer100Km?.let { Body("Averaging ${formatConsumption(it)}.") }
            }
        }
    }
    r.averageScore?.let { score ->
        list += Story(GarageColors.live) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kicker("Driving score")
                CountUp(score.toDouble(), suffix = "/100", decimals = 0)
                Body(
                    when {
                        score >= 90 -> "Silky smooth. Your passengers thank you."
                        score >= 75 -> "Solid and smooth, with the odd spirited moment."
                        else -> "Plenty of spirit. Easing off earlier would lift this."
                    },
                )
                Body("${r.hardBrakes} hard brakes in total.")
            }
        }
    }
    list += Story(GarageColors.carbon) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kicker("${r.period.title} in one look")
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat("km", km(r.distanceMeters))
                Stat("drives", r.driveCount.toString())
                Stat("hours", hours(r.drivingMillis))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                r.topSpeed?.let { Stat("top", formatSpeed(it)) }
                if (r.fillUps > 0) Stat("on fuel", formatMoney(r.moneySpent))
                r.averageScore?.let { Stat("score", it.toString()) }
            }
            Body("Tap share to show it off.")
        }
    }
    return list
}

@Composable
private fun Kicker(text: String) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold), color = Color.White.copy(alpha = 0.8f))

@Composable
private fun Huge(text: String) =
    Text(text, style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, lineHeight = 66.sp).tabular(), color = Color.White)

@Composable
private fun Big(text: String) = Text(text, style = MaterialTheme.typography.displaySmall.tabular(), color = Color.White)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), color = Color.White.copy(alpha = 0.92f))

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall.tabular(), color = Color.White)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp), color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun CountUp(target: Double, suffix: String, decimals: Int = 0) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(target) { shown.animateTo(target.toFloat(), tween(1_400, easing = FastOutSlowInEasing)) }
    Huge(String.format(Locale.getDefault(), "%,.${decimals}f", shown.value) + suffix)
}

@Composable
private fun Buckets(values: List<Double>, labels: Pair<String, String>) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            val gap = size.width / values.size * 0.25f
            val w = size.width / values.size - gap
            values.forEachIndexed { i, v ->
                val h = (v / max * size.height).toFloat().coerceAtLeast(if (v > 0) 4f else 2f)
                drawRoundRect(
                    Color.White.copy(alpha = if (v > 0) 0.9f else 0.25f),
                    Offset(i * (w + gap), size.height - h),
                    Size(w, h),
                    CornerRadius(w / 3, w / 3),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(labels.first, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            Text(labels.second, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
    }
}
