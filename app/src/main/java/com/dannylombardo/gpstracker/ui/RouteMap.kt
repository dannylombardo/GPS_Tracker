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
import com.dannylombardo.gpstracker.ui.theme.RouteColors
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

/**
 * The drive's route over OpenStreetMap tiles (no API key). Tiles are only fetched
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
                showRoute(map, points, routeColor, 4f * density.density)
            }
        },
    )
}

private fun showRoute(map: MapView, points: List<LatLon>, color: Color, strokePx: Float) {
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
    val line = Polyline(map).apply {
        setPoints(geoPoints)
        outlinePaint.color = color.toArgb()
        outlinePaint.strokeWidth = strokePx
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.strokeJoin = Paint.Join.ROUND
        outlinePaint.isAntiAlias = true
    }
    map.overlays.add(casing)
    map.overlays.add(line)
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
