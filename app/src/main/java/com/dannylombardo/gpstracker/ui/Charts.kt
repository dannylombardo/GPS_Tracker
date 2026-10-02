package com.dannylombardo.gpstracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.dannylombardo.gpstracker.data.RouteProfile
import com.dannylombardo.gpstracker.ui.theme.RouteColors
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max

/** Kilometres per day, Monday to Sunday, as rounded bars that grow in. */
@Composable
internal fun WeekBars(
    dailyMeters: List<Double>,
    todayIndex: Int?,
    barColor: Color,
    trackColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    val max = dailyMeters.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val days = remember { DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, Locale.getDefault()) } }
    val growth = remember { Animatable(0f) }
    LaunchedEffect(dailyMeters) {
        growth.snapTo(0f)
        growth.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    Row(
        modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        dailyMeters.forEachIndexed { index, meters ->
            val isToday = index == todayIndex
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    if (meters > 0) String.format(Locale.getDefault(), "%.0f", meters / 1000) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                )
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .width(20.dp)
                        .weight(1f, fill = true)
                        .background(trackColor, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val fraction = (meters / max).toFloat() * growth.value
                    if (meters > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceIn(0.08f, 1f))
                                .background(barColor, RoundedCornerShape(8.dp)),
                        )
                    }
                }
                Text(
                    days[index],
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) barColor else labelColor,
                )
            }
        }
    }
}

/** Speed over the drive, with faint lines every 20 or 40 km/h and the top speed marked. */
@Composable
internal fun SpeedChart(profile: RouteProfile, modifier: Modifier = Modifier) {
    val samples = profile.speeds
    if (samples.size < 2) return
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()

    val topKmh = (profile.maxSpeedMetersPerSecond ?: 0.0) * 3.6
    val stepKmh = if (topKmh > 120) 40.0 else 20.0
    val maxKmh = max(stepKmh * 2, ceil(topKmh / stepKmh) * stepKmh)
    val startTime = samples.first().time
    val span = (samples.last().time - startTime).coerceAtLeast(1).toFloat()

    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        val chartTop = 8.dp.toPx()
        val chartHeight = size.height - chartTop
        fun x(time: Long) = (time - startTime) / span * size.width
        fun y(metersPerSecond: Double) = chartTop + chartHeight * (1 - (metersPerSecond * 3.6 / maxKmh).toFloat())

        var gridKmh = 0.0
        while (gridKmh <= maxKmh) {
            val gy = y(gridKmh / 3.6)
            drawLine(
                grid,
                Offset(0f, gy),
                Offset(size.width, gy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (gridKmh == 0.0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
            )
            if (gridKmh > 0) {
                val label = measurer.measure("${gridKmh.toInt()}", labelStyle)
                drawText(label, topLeft = Offset(0f, gy - label.size.height - 2.dp.toPx()))
            }
            gridKmh += stepKmh
        }

        val path = Path()
        samples.forEachIndexed { index, sample ->
            val point = Offset(x(sample.time), y(sample.metersPerSecond))
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), line.copy(alpha = 0f))))
        drawPath(
            path,
            line,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        samples.maxByOrNull { it.metersPerSecond }?.let { top ->
            val center = Offset(x(top.time), y(top.metersPerSecond))
            drawCircle(line, radius = 6.dp.toPx(), center = center)
            drawCircle(Color.White, radius = 3.dp.toPx(), center = center)
        }
    }
}

/** The route's outline on its own, without a map, for a drive's row in the list. */
@Composable
internal fun RouteThumbnail(points: List<LatLon>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val meanLat = Math.toRadians(points.sumOf { it.latitude } / points.size)
        val xs = points.map { it.longitude * cos(meanLat) }
        val ys = points.map { -it.latitude }
        val minX = xs.min()
        val minY = ys.min()
        val spanX = (xs.max() - minX).coerceAtLeast(1e-9)
        val spanY = (ys.max() - minY).coerceAtLeast(1e-9)
        val inset = 6.dp.toPx()
        val scale = minOf((size.width - 2 * inset) / spanX, (size.height - 2 * inset) / spanY)
        val offsetX = (size.width - spanX * scale) / 2
        val offsetY = (size.height - spanY * scale) / 2
        fun at(i: Int) = Offset(
            (offsetX + (xs[i] - minX) * scale).toFloat(),
            (offsetY + (ys[i] - minY) * scale).toFloat(),
        )

        val path = Path()
        points.indices.forEach { i -> at(i).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(RouteColors.start, radius = 3.dp.toPx(), center = at(0))
        drawCircle(RouteColors.end, radius = 3.dp.toPx(), center = at(points.lastIndex))
    }
}

/** A plain coordinate, so route previews don't hold on to whole database rows. */
data class LatLon(val latitude: Double, val longitude: Double)
