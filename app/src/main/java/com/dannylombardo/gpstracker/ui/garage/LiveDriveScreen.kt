package com.dannylombardo.gpstracker.ui.garage

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannylombardo.gpstracker.ui.theme.tabular
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private val DashBlack = Color(0xFF07070A)
private val DashRed = Color(0xFFFF2A1F)
private val DashText = Color(0xFFF4F4F6)
private val DashMuted = Color(0xFF8B8B93)

/**
 * The live dash while a drive is recording: a big speedometer, the trip meter, time,
 * average and top speed, and the last two minutes of speed. Always dark, like a car at night.
 */
@Composable
internal fun LiveDriveScreen(vm: GarageViewModel, onBack: () -> Unit) {
    val live by vm.live.collectAsStateWithLifecycle()
    val keepOn by vm.keepScreenOn.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(keepOn, live != null) {
        view.keepScreenOn = keepOn && live != null
        onDispose { view.keepScreenOn = false }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DashBlack)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(DashRed.copy(alpha = 0.22f), Color.Transparent), Offset(size.width / 2, size.height * 0.3f), size.width),
                    size.width,
                    Offset(size.width / 2, size.height * 0.3f),
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", tint = DashText)
                }
                Spacer(Modifier.weight(1f))
                LivePill(live != null)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (keepOn) DashRed.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                        .clickable { vm.setKeepScreenOn(!keepOn) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.LightMode, contentDescription = if (keepOn) "Screen stays on" else "Screen can sleep", tint = if (keepOn) DashRed else DashMuted, modifier = Modifier.size(20.dp))
                }
            }

            val drive = live
            val kmh = drive?.speedMetersPerSecond?.let { (it * 3.6).toFloat() } ?: 0f
            val topKmh = (drive?.topSpeedMetersPerSecond ?: 0.0) * 3.6
            val dialMax = when {
                topKmh > 180 -> 260f
                topKmh > 140 -> 220f
                else -> 180f
            }
            Spacer(Modifier.height(12.dp))
            Speedometer(
                kmh = kmh,
                maxKmh = dialMax,
                accent = DashRed,
                face = DashText,
                redlineKmh = dialMax * 0.75f,
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).aspectRatio(1f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (drive?.speedMetersPerSecond != null) kmh.roundToInt().toString() else "–",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp, fontWeight = FontWeight.Bold).tabular(),
                        color = DashText,
                    )
                    Text("KM/H", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp), color = DashMuted)
                }
            }

            if (drive == null) {
                Spacer(Modifier.height(16.dp))
                Text("No drive in progress", style = MaterialTheme.typography.titleLarge, color = DashText)
                Text(
                    "Drives start on their own when you get going. You can also start one now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DashMuted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
                )
                DashButton("Start a drive", Icons.Rounded.PlayArrow, Color(0xFF1FBF5A), onClick = vm::startDrive)
                return@Column
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashTile("Trip", String.format(Locale.getDefault(), "%.1f", drive.distanceMeters / 1000), "km", Modifier.weight(1f))
                DashTile("Time", clock(now - drive.startTime), null, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashTile("Average", drive.averageSpeedMetersPerSecond?.let { (it * 3.6).roundToInt().toString() } ?: "–", "km/h", Modifier.weight(1f))
                DashTile("Top", if (topKmh > 0) topKmh.roundToInt().toString() else "–", "km/h", Modifier.weight(1f), valueColor = DashRed)
            }
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .padding(14.dp),
            ) {
                Text("LAST 2 MINUTES", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp), color = DashMuted)
                Spacer(Modifier.height(8.dp))
                SpeedTrace(drive.recentSpeeds.map { it.second }, DashRed, Modifier.fillMaxWidth().height(72.dp))
            }
            Spacer(Modifier.height(18.dp))
            DashButton("End drive", Icons.Rounded.Stop, DashRed, onClick = vm::endDrive)
            Text(
                "Glance, don't stare. Keep your eyes on the road.",
                style = MaterialTheme.typography.bodySmall,
                color = DashMuted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun clock(millis: Long): String {
    val s = (millis / 1000).coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60)
}

@Composable
private fun LivePill(on: Boolean) {
    val pulse = rememberInfiniteTransition(label = "live")
    val a by pulse.animateFloat(0.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).alpha(if (on) a else 0.4f).background(if (on) DashRed else DashMuted, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(if (on) "LIVE" else "PARKED", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp), color = DashText)
    }
}

@Composable
private fun DashTile(label: String, value: String, unit: String?, modifier: Modifier, valueColor: Color = DashText) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp), color = DashMuted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineMedium.tabular(), color = valueColor)
            if (unit != null) {
                Text(" $unit", style = MaterialTheme.typography.bodyMedium, color = DashMuted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun DashButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}
