package com.dannylombardo.gpstracker.ui.garage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dannylombardo.gpstracker.garage.Maintenance
import com.dannylombardo.gpstracker.ui.theme.apple
import com.dannylombardo.gpstracker.ui.theme.tabular
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Small caps label, like the writing on a gauge face. */
@Composable
internal fun GaugeLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = modifier,
    )
}

/**
 * A ring that fills to [progress] (0 to 1, a little past 1 shows as full and red),
 * with whatever's in [content] in the middle. Sweeps in when first shown.
 */
@Composable
internal fun ProgressRing(
    progress: Double,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    thickness: Dp = 12.dp,
    content: @Composable () -> Unit = {},
) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        shown.animateTo(progress.toFloat().coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing))
    }
    val track = MaterialTheme.apple.fill
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                Brush.sweepGradient(listOf(color.copy(alpha = 0.7f), color, color)),
                -90f,
                360f * shown.value,
                false,
                Offset(inset, inset),
                arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/** A slim rounded bar filled to [progress]. */
@Composable
internal fun ProgressBar(progress: Double, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(progress) { shown.animateTo(progress.toFloat().coerceIn(0f, 1f), tween(700)) }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.apple.fill, CircleShape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(shown.value.coerceAtLeast(0.02f))
                .height(height)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color)), CircleShape),
        )
    }
}

/** Green, amber or red for how close a maintenance job is. */
@Composable
internal fun Maintenance.State.color(): Color = when (this) {
    Maintenance.State.OK -> MaterialTheme.apple.green
    Maintenance.State.SOON -> MaterialTheme.apple.orange
    Maintenance.State.DUE -> MaterialTheme.apple.red
}

/** Score colour: green when smooth, amber in the middle, red when rough. */
@Composable
internal fun scoreColor(score: Int?): Color = when {
    score == null -> MaterialTheme.apple.tertiaryLabel
    score >= 85 -> MaterialTheme.apple.green
    score >= 65 -> MaterialTheme.apple.orange
    else -> MaterialTheme.apple.red
}

/**
 * The odometer: white digits in little dark drums, like a mechanical one.
 */
@Composable
internal fun OdometerDigits(km: Double, modifier: Modifier = Modifier, digits: Int = 6) {
    val text = String.format(Locale.US, "%0${digits}d", km.toLong().coerceIn(0, 9_999_999))
    val tenth = ((km * 10).toLong() % 10).toInt()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        text.forEach { ch -> Drum(ch.toString(), Color(0xFF111114), Color.White) }
        Drum(tenth.toString(), Color(0xFFE8E8EC), Color(0xFF111114))
        Text(
            "km",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun Drum(digit: String, background: Color, color: Color) {
    Box(
        Modifier
            .width(22.dp)
            .height(32.dp)
            .background(
                Brush.verticalGradient(listOf(background.copy(alpha = 0.75f), background, background.copy(alpha = 0.75f))),
                RoundedCornerShape(5.dp),
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            digit,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 19.sp),
            color = color,
        )
    }
}

/**
 * A sports-car speedometer: a 270° dial with ticks every 20 km/h, a red line above
 * [redlineKmh], and a needle. Draws in whatever colours it's given.
 */
@Composable
internal fun Speedometer(
    kmh: Float,
    maxKmh: Float,
    modifier: Modifier = Modifier,
    accent: Color,
    face: Color,
    redlineKmh: Float = maxKmh * 0.8f,
    content: @Composable () -> Unit = {},
) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(kmh) { shown.animateTo(kmh.coerceIn(0f, maxKmh), tween(600, easing = FastOutSlowInEasing)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val start = 135f
            val sweep = 270f
            val stroke = size.minDimension * 0.045f
            val inset = stroke * 2
            val arc = Size(size.minDimension - inset * 2, size.minDimension - inset * 2)
            val topLeft = Offset((size.width - arc.width) / 2, (size.height - arc.height) / 2)
            val center = Offset(size.width / 2, size.height / 2)
            val radius = arc.width / 2
            drawArc(face.copy(alpha = 0.14f), start, sweep, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            val redStart = start + sweep * (redlineKmh / maxKmh)
            drawArc(accent.copy(alpha = 0.35f), redStart, start + sweep - redStart, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            val value = shown.value / maxKmh
            drawArc(
                Brush.sweepGradient(listOf(accent.copy(alpha = 0.4f), accent), center),
                start,
                sweep * value,
                false,
                topLeft,
                arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // Ticks every 10 km/h, longer every 20.
            var tick = 0f
            while (tick <= maxKmh + 0.1f) {
                val angle = Math.toRadians((start + sweep * tick / maxKmh).toDouble())
                val major = (tick % 20f) == 0f
                val outer = radius - stroke * 1.4f
                val inner = outer - if (major) stroke * 1.6f else stroke * 0.8f
                drawLine(
                    if (tick >= redlineKmh) accent else face.copy(alpha = if (major) 0.8f else 0.4f),
                    Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                    Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                    strokeWidth = if (major) stroke * 0.35f else stroke * 0.2f,
                    cap = StrokeCap.Round,
                )
                tick += 10f
            }
            // The needle.
            val needleAngle = Math.toRadians((start + sweep * value).toDouble())
            val tip = Offset(center.x + cos(needleAngle).toFloat() * (radius - stroke * 3.4f), center.y + sin(needleAngle).toFloat() * (radius - stroke * 3.4f))
            val back = Offset(center.x - cos(needleAngle).toFloat() * stroke * 1.5f, center.y - sin(needleAngle).toFloat() * stroke * 1.5f)
            drawLine(accent, back, tip, strokeWidth = stroke * 0.4f, cap = StrokeCap.Round)
            drawCircle(face, stroke * 0.9f, center)
            drawCircle(accent, stroke * 0.45f, center)
        }
        content()
    }
}

/** A little line of speeds (m/s), oldest on the left, for the live screen. */
@Composable
internal fun SpeedTrace(speeds: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (speeds.size < 2) return@Canvas
        val max = (speeds.maxOrNull() ?: 1f).coerceAtLeast(8f)
        val step = size.width / (speeds.size - 1)
        val path = Path()
        speeds.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - v / max * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** A grouped-list row with a label on the left and a right-aligned input. */
@Composable
internal fun InputRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    unit: String? = null,
    number: Boolean = false,
) {
    val textStyle = MaterialTheme.typography.bodyLarge.tabular().copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty()) {
                Text(placeholder, style = textStyle.copy(color = MaterialTheme.apple.tertiaryLabel))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (unit != null) {
            Text(
                unit,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** A labelled figure for the inside of cards. */
@Composable
internal fun Figure(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium.tabular(), color = color, maxLines = 1)
        GaugeLabel(label)
    }
}

internal fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

internal fun plainNumber(value: Double?): String =
    value?.toBigDecimal()?.stripTrailingZeros()?.toPlainString() ?: ""

internal fun formatSeconds(millis: Long): String = String.format(Locale.getDefault(), "%.1f s", millis / 1000.0)

internal fun formatKmWhole(km: Double): String = String.format(Locale.getDefault(), "%,.0f km", km)

internal fun shortDate(millis: Long): String =
    com.dannylombardo.gpstracker.ui.formatShortDate(com.dannylombardo.gpstracker.ui.localDateOf(millis))
