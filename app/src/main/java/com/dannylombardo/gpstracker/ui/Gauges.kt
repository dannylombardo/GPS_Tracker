package com.dannylombardo.gpstracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.dannylombardo.gpstracker.ui.theme.tabular
import kotlin.math.cos
import kotlin.math.sin

/** A coloured stretch of a gauge's dial, from one value to another, like a tach's redline. */
internal class GaugeZone(val from: Float, val to: Float, val color: Color)

/** Where the dial starts (bottom left) and how far round it goes, in degrees. */
private const val START_ANGLE = 135f
private const val SWEEP = 270f

/**
 * A car-style analogue gauge: a 270° dial with numbered ticks, coloured zones round the
 * rim, a needle that swings up to [value] with a little overshoot, and an optional
 * [marker] tick (an average, say). [valueText] and [unit] sit in the middle like a
 * digital readout. [content] picks the ink: the theme's text colour, or white on a
 * coloured card.
 */
@Composable
internal fun Gauge(
    value: Float,
    max: Float,
    valueText: String,
    unit: String,
    modifier: Modifier = Modifier,
    zones: List<GaugeZone> = emptyList(),
    marker: Float? = null,
    majorStep: Float = max / 6,
    label: String? = null,
    content: Color = MaterialTheme.colorScheme.onSurface,
    needle: Color = MaterialTheme.colorScheme.primary,
    track: Color = content.copy(alpha = 0.12f),
) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(value, max) {
        sweep.animateTo(
            (value / max).coerceIn(0f, 1.04f),
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow),
        )
    }
    val measurer = rememberTextMeasurer()
    val tickStyle = MaterialTheme.typography.labelSmall.tabular().copy(color = content.copy(alpha = 0.7f))
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.045f
            val radius = size.minDimension / 2 - stroke
            val center = Offset(size.width / 2, size.height / 2)
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)

            // The rim, then the coloured zones laid over it.
            drawArc(track, START_ANGLE, SWEEP, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            zones.forEach { zone ->
                val from = START_ANGLE + SWEEP * (zone.from / max).coerceIn(0f, 1f)
                val to = START_ANGLE + SWEEP * (zone.to / max).coerceIn(0f, 1f)
                drawArc(zone.color, from, to - from, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
            }

            // Ticks: a long numbered one at each major step, short ones in between.
            val minorStep = majorStep / 2
            var tick = 0f
            while (tick <= max + 0.001f) {
                val major = (tick / majorStep).let { kotlin.math.abs(it - Math.round(it)) < 0.01f }
                val angle = Math.toRadians((START_ANGLE + SWEEP * tick / max).toDouble())
                val outer = radius - stroke * 1.2f
                val inner = outer - if (major) stroke * 1.8f else stroke * 0.9f
                drawLine(
                    content.copy(alpha = if (major) 0.8f else 0.35f),
                    Offset(center.x + outer * cos(angle).toFloat(), center.y + outer * sin(angle).toFloat()),
                    Offset(center.x + inner * cos(angle).toFloat(), center.y + inner * sin(angle).toFloat()),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                if (major) {
                    drawTickLabel(measurer, tick, tickStyle, center, inner - stroke * 1.4f, angle)
                }
                tick += minorStep
            }

            // The average (or other) marker: a small triangle on the rim.
            marker?.let { m ->
                val angle = Math.toRadians((START_ANGLE + SWEEP * (m / max).coerceIn(0f, 1f)).toDouble())
                val r = radius + stroke * 0.2f
                drawCircle(content, radius = stroke * 0.45f, center = Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat()))
            }

            // The needle, with a hub.
            rotate(START_ANGLE + SWEEP * sweep.value, pivot = center) {
                drawLine(
                    needle,
                    Offset(center.x - radius * 0.12f, center.y),
                    Offset(center.x + radius * 0.78f, center.y),
                    strokeWidth = stroke * 0.55f,
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(needle, radius = stroke * 1.1f, center = center)
            drawCircle(content.copy(alpha = 0.9f), radius = stroke * 0.45f, center = center)
        }
        // The digital readout, low in the dial like a dashboard's.
        Column(
            Modifier.fillMaxWidth().padding(top = 0.dp).align(Alignment.BottomCenter).padding(bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(valueText, style = MaterialTheme.typography.headlineMedium.tabular(), color = content)
            Text(unit.uppercase(), style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f))
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.85f))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTickLabel(
    measurer: TextMeasurer,
    value: Float,
    style: androidx.compose.ui.text.TextStyle,
    center: Offset,
    radius: Float,
    angle: Double,
) {
    val text = if (value >= 10 || value == 0f) value.toInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", value)
    val layout = measurer.measure(text, style)
    val x = center.x + radius * cos(angle).toFloat() - layout.size.width / 2f
    val y = center.y + radius * sin(angle).toFloat() - layout.size.height / 2f
    drawText(layout, topLeft = Offset(x, y))
}

/** A round top for a gauge's scale: the next multiple of [step] at or above [value], at least [floor]. */
internal fun gaugeMax(value: Double, step: Double, floor: Double): Float {
    val top = kotlin.math.ceil(value / step) * step
    return maxOf(top, floor).toFloat()
}
