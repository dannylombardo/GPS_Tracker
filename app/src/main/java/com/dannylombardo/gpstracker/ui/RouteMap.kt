package com.dannylombardo.gpstracker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dannylombardo.gpstracker.data.SpeedBand
import com.dannylombardo.gpstracker.ui.theme.RouteColors
import com.dannylombardo.gpstracker.ui.theme.SpeedColors
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/** A piece of the route drawn in one colour. */
internal data class ColoredStretch(val color: Color, val points: List<LatLon>)

/** A spot on the route to point out, like where the car was at a moment picked on the slider. */
internal data class MapMarker(val position: LatLon, val color: Color, val label: String)

internal fun SpeedBand.color(): Color = when (this) {
    SpeedBand.CRAWLING -> SpeedColors.crawling
    SpeedBand.SLOW -> SpeedColors.slow
    SpeedBand.FAST -> SpeedColors.fast
    SpeedBand.FASTEST -> SpeedColors.fastest
}

/**
 * The drive's route over OpenStreetMap tiles (no API key). With [stretches] the
 * line is drawn in their colours (speed), otherwise in [routeColor]. Tiles are only fetched
 * while this map is on screen, and are cached in the app's own cache folder.
 * It pans and zooms; to keep a small preview still, cover it with something that
 * takes the touches.
 */
@Composable
internal fun RouteMap(
    points: List<LatLon>,
    routeColor: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
    stretches: List<ColoredStretch>? = null,
    marker: MapMarker? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            setMinZoomLevel(3.0)
        }
    }
    val markerOverlay = remember { MarkerOverlay(density.density) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
            if (map.tag != points) {
                map.tag = points
                val colored = stretches ?: listOf(ColoredStretch(routeColor, points))
                showRoute(map, points, colored, 4f * density.density)
                map.overlays.add(markerOverlay)
            }
            if (markerOverlay.marker != marker) {
                markerOverlay.marker = marker
                marker?.let { keepInView(map, GeoPoint(it.position.latitude, it.position.longitude)) }
                map.invalidate()
            }
        },
    )
}

private fun showRoute(map: MapView, points: List<LatLon>, stretches: List<ColoredStretch>, strokePx: Float) {
    map.overlays.clear()
    if (points.isEmpty()) return
    val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }

    val casing = Polyline(map).apply {
        setPoints(geoPoints)
        outlinePaint.color = android.graphics.Color.WHITE
        outlinePaint.strokeWidth = strokePx * 2.2f
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
        outlinePaint.isAntiAlias = true
    }
    map.overlays.add(casing)
    stretches.filter { it.points.size >= 2 }.forEach { stretch ->
        map.overlays.add(
            Polyline(map).apply {
                setPoints(stretch.points.map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.color = stretch.color.toArgb()
                outlinePaint.strokeWidth = strokePx
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.isAntiAlias = true
            },
        )
    }
    map.overlays.add(EndpointsOverlay(geoPoints.first(), geoPoints.last(), strokePx * 1.8f))

    val box = BoundingBox.fromGeoPointsSafe(geoPoints)
    val padding = (strokePx * 10).toInt()
    if (map.width > 0 && map.height > 0) {
        fitRoute(map, box, padding)
    } else {
        map.addOnFirstLayoutListener { _, _, _, _, _ -> fitRoute(map, box, padding) }
    }
    map.invalidate()
}

private fun fitRoute(map: MapView, box: BoundingBox, padding: Int) {
    // A drive around the block would otherwise zoom in to individual houses.
    val minSpan = 0.004
    val padded = if (box.latitudeSpan < minSpan && box.longitudeSpanWithDateLine < minSpan) {
        BoundingBox(
            box.centerLatitude + minSpan / 2,
            box.centerLongitude + minSpan / 2,
            box.centerLatitude - minSpan / 2,
            box.centerLongitude - minSpan / 2,
        )
    } else {
        box
    }
    map.zoomToBoundingBox(padded, false, padding)
}

/** Pans a zoomed-in map so the marker doesn't slide off the edge. */
private fun keepInView(map: MapView, where: GeoPoint) {
    if (map.width == 0 || map.height == 0) return
    if (!map.boundingBox.contains(where)) map.controller.animateTo(where)
}

/**
 * The picked spot: a dot in its speed colour with a white ring, and a bubble above it
 * with the speed. Nothing is drawn while [marker] is null.
 */
private class MarkerOverlay(private val density: Float) : Overlay() {
    var marker: MapMarker? = null

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(60, 0, 0, 0) }
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(235, 32, 33, 36) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 13f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val point = Point()
    private val rect = android.graphics.RectF()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val shown = marker ?: return
        mapView.projection.toPixels(GeoPoint(shown.position.latitude, shown.position.longitude), point)
        val x = point.x.toFloat()
        val y = point.y.toFloat()
        canvas.drawCircle(x, y + density, 11f * density, this.shadow)
        canvas.drawCircle(x, y, 10f * density, ring)
        fill.color = shown.color.toArgb()
        canvas.drawCircle(x, y, 7f * density, fill)

        val padX = 8f * density
        val height = 24f * density
        val width = text.measureText(shown.label) + padX * 2
        val bottom = y - 16f * density
        rect.set(x - width / 2, bottom - height, x + width / 2, bottom)
        canvas.drawRoundRect(rect, height / 2, height / 2, bubble)
        val baseline = rect.centerY() - (text.descent() + text.ascent()) / 2
        canvas.drawText(shown.label, x, baseline, text)
    }
}

/** Green dot where the drive started, red where it ended. */
private class EndpointsOverlay(
    private val start: GeoPoint,
    private val end: GeoPoint,
    private val radiusPx: Float,
) : Overlay() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    private val point = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        dot(canvas, mapView, start, RouteColors.start.toArgb())
        dot(canvas, mapView, end, RouteColors.end.toArgb())
    }

    private fun dot(canvas: Canvas, mapView: MapView, where: GeoPoint, color: Int) {
        mapView.projection.toPixels(where, point)
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radiusPx * 1.45f, ring)
        fill.color = color
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radiusPx, fill)
    }
}

/** osmdroid's tile cache defaults to shared storage; keep it in the app's cache instead. */
private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    if (config.userAgentValue == context.packageName) return
    config.userAgentValue = context.packageName
    val base = File(context.cacheDir, "osmdroid")
    config.osmdroidBasePath = base
    config.osmdroidTileCache = File(base, "tiles")
}
