package fr.oupson.jxlviewer.ui.screen

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import fr.oupson.jxlviewer.BuildConfig
import fr.oupson.jxlviewer.R
import fr.oupson.jxlviewer.ui.loading.JxlLoader
import fr.oupson.jxlviewer.ui.loading.rememberJxlLoader
import fr.oupson.jxlviewer.ui.model.ViewerViewModel
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIN_ZOOM_SCALE = 1f

// The zoom cap is measured relative to the original image resolution: the
// image may be magnified up to 5x its native pixel size (a fit-scale of S
// magnifies the original by S x fit, so S_max = ZOOM_CAP_ORIGINAL_MULT / fit).
private const val ZOOM_CAP_ORIGINAL_MULT = 5f

// Hard ceiling on the fit-scale for extreme panoramas (5/fit would otherwise
// blow up); 128x fit already covers 25k+ px-wide images on a phone screen.
private const val HARD_MAX_ZOOM_SCALE = 128f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ViewerScreen(imageUri: Uri) {
    // Fullscreen viewer: hide the status and navigation bars while this screen
    // is composed (swipe brings them back transiently); restore on exit.
    val view = LocalView.current
    val activity = LocalActivity.current
    val window = activity?.window
    DisposableEffect(Unit) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    val viewerViewModel = hiltViewModel<ViewerViewModel, ViewerViewModel.Factory>(creationCallback = { factory ->
        factory.create(imageUri)
    })

    val wideGamut = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val activity = LocalActivity.current
        val isWideGamutSupported = remember {
            if (activity != null) {
                ContextCompat.getDisplayOrDefault(activity).isWideColorGamut
            } else {
                false
            }
        }

        // The HDR window mode is set once in MainActivity for the whole app.
        // Do NOT toggle window.colorMode here: a runtime color-mode switch
        // makes SurfaceFlinger drop adaptive-refresh displays into a ~15Hz
        // compatibility state, which makes panning huge images janky.
        isWideGamutSupported
    } else {
        false
    }

    val name by viewerViewModel.nameFlow.collectAsState()

    Box {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            val bitmapConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && wideGamut) {
                Bitmap.Config.RGBA_F16
            } else {
                Bitmap.Config.ARGB_8888
            }
            val painter = rememberJxlLoader(
                imageUri, decodePreview = JxlLoader.DecodePreview.WithFullImage, animated = true, config = bitmapConfig
            )
            val state by painter.state().collectAsState()

            // Pan/zoom state lives in a holder object: the ViewerScreen body
            // never reads scale/offset during composition, so per-gesture
            // updates only recompose the small PanZoomImage subtree instead of
            // the whole screen.
            val panZoom = remember { PanZoomState() }

            when (val s = state) {
                JxlLoader.JxlState.Empty -> {}
                is JxlLoader.JxlState.Error -> {
                    Image(
                        painterResource(R.drawable.broken_image),
                        contentDescription = stringResource(R.string.error_failed_to_load_file),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                is JxlLoader.JxlState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }

                is JxlLoader.JxlState.Loaded -> {
                    PanZoomContent(panZoom = panZoom, painter = s.painter, name = name)
                }

                // The preview uses the SAME pan/zoom box and state: the user can
                // zoom/pan the low-res preview immediately, and the transform is
                // carried over unchanged when the full-resolution load arrives
                // (progressive decoding keeps the aspect ratio, so the fit box is
                // identical).
                is JxlLoader.JxlState.Preview -> {
                    PanZoomContent(panZoom = panZoom, painter = s.painter, name = name)
                }
            }
        }
    }
}

/**
 * Holds the pan/zoom transform of the viewer image. The [scale]/[offset] state
 * is only *read* inside [PanZoomImage], so gesture updates re-compose that tiny
 * subtree instead of the whole ViewerScreen (which would re-run the toolbars,
 * dialogs and app bar on every touch event).
 */
private class PanZoomState {
    val scale = mutableFloatStateOf(MIN_ZOOM_SCALE)
    val offset = mutableStateOf(Offset.Zero)

    // Updated by the composable (main thread only): the gesture viewport and
    // the current painter's intrinsic image size in px.
    @Volatile
    var viewportSize: IntSize = IntSize.Zero
    @Volatile
    var imageIntrinsic: IntSize = IntSize.Zero

    /**
     * Pinch-zoom anchored at [centroid] (viewport px). The render transform is
     * `screen = viewportCenter + T + s*q` (q = fit-space offset from center),
     * so the content point q* = (P - T)/s under the pinch centroid P stays
     * under P after zooming to z*s exactly when T' = z*T + P*(1 - z).
     * graphicsLayer alone scales about the viewport center, which would drag
     * content away when pinching at an edge; this correction keeps the
     * position under the fingers stable.
     */
    fun onPinch(z: Float, centroid: Offset) {
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return
        val s = scale.floatValue
        val s2 = (s * z).coerceIn(MIN_ZOOM_SCALE, maxZoom())
        val zActual = s2 / s
        if (zActual == 1f) return
        val T = offset.value
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        val P = centroid - center
        val T2 = Offset(
            T.x * zActual + P.x * (1f - zActual),
            T.y * zActual + P.y * (1f - zActual),
        )
        scale.floatValue = s2
        offset.value = T2.clamped(viewportSize, imageIntrinsic, s2)
    }

    fun onDrag(delta: Offset) {
        if (delta == Offset.Zero) return
        offset.value = (offset.value + delta)
            .clamped(viewportSize, imageIntrinsic, scale.floatValue)
    }

    /**
     * Max fit-scale for the current image in the viewport, capped at
     * [ZOOM_CAP_ORIGINAL_MULT]x the *original* image resolution: at fit-scale
     * S the original is magnified by S x fit, so the cap is
     * ZOOM_CAP_ORIGINAL_MULT / fit. The result is floored at 1 so that small
     * images (which ContentScale.Fit already magnifies beyond the cap) keep a
     * valid [1, max] range, and hard-capped at [HARD_MAX_ZOOM_SCALE].
     */
    private fun maxZoom(): Float {
        val container = viewportSize
        val imageIntrinsic = imageIntrinsic
        if (imageIntrinsic.width <= 0 || imageIntrinsic.height <= 0 ||
            container.width <= 0 || container.height <= 0
        ) return HARD_MAX_ZOOM_SCALE
        val fit = min(
            (container.width / imageIntrinsic.width.toFloat()).coerceAtLeast(0f),
            (container.height / imageIntrinsic.height.toFloat()).coerceAtLeast(0f),
        )
        if (fit <= 0f) return HARD_MAX_ZOOM_SCALE
        return (ZOOM_CAP_ORIGINAL_MULT / fit).coerceIn(MIN_ZOOM_SCALE, HARD_MAX_ZOOM_SCALE)
    }
}

/**
 * Panning is only allowed along an axis once the zoomed image overflows the
 * container on that axis; along a fitted axis the image stays centered.
 */
private fun Offset.clamped(container: IntSize, imageIntrinsic: IntSize, scale: Float): Offset {
    if (imageIntrinsic.width <= 0 || imageIntrinsic.height <= 0) return Offset.Zero
    val fit = min(
        (container.width / imageIntrinsic.width.toFloat()).coerceAtLeast(0f),
        (container.height / imageIntrinsic.height.toFloat()).coerceAtLeast(0f),
    )
    val scaledW = imageIntrinsic.width * fit * scale
    val scaledH = imageIntrinsic.height * fit * scale
    val maxX = (scaledW - container.width).coerceAtLeast(0f) / 2f
    val maxY = (scaledH - container.height).coerceAtLeast(0f) / 2f
    return Offset(x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
}

@Composable
private fun PanZoomImage(panZoom: PanZoomState, painter: Painter, name: String?) {
    val scale = panZoom.scale.floatValue
    val offset = panZoom.offset.value
    Image(
        painter,
        contentDescription = if (name != null) {
            stringResource(R.string.description_a_preview_of, requireNotNull(name))
        } else {
            stringResource(R.string.description_a_preview_no_name)
        },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y
            ),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun PanZoomContent(
    panZoom: PanZoomState,
    painter: Painter,
    name: String?
) {
    LaunchedEffect(painter) {
        val sz = painter.intrinsicSize
        if (sz.width > 0f && sz.height > 0f) {
            panZoom.imageIntrinsic = IntSize(sz.width.roundToInt(), sz.height.roundToInt())
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { panZoom.viewportSize = it }
            .pointerInput(panZoom) {
                // transformable only reports the pinch zoom factor and the
                // centroid DELTA, not the centroid's absolute position, which
                // is needed to anchor the zoom under the fingers; so track the
                // active pointers ourselves.
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    first.consume()
                    val active = HashMap<PointerId, Offset>()
                    active[first.id] = first.position
                    var prevCentroid: Offset = first.position
                    var prevPinchDist = 0f
                    var prevCount = 1
                    while (true) {
                        val event = awaitPointerEvent()
                        for (c in event.changes) {
                            if (c.pressed) active[c.id] = c.position else active.remove(c.id)
                            c.consume()
                        }
                        if (active.isEmpty()) break
                        val pointers = active.values.toList()
                        val centroid = if (pointers.size >= 2) {
                            Offset(
                                (pointers[0].x + pointers[1].x) / 2f,
                                (pointers[0].y + pointers[1].y) / 2f
                            )
                        } else {
                            pointers[0]
                        }
                        val dist = if (pointers.size >= 2) {
                            hypot(
                                pointers[1].x - pointers[0].x,
                                pointers[1].y - pointers[0].y
                            )
                        } else {
                            0f
                        }
                        if (pointers.size != prevCount) {
                            // Finger count changed: re-anchor so a leftover
                            // finger after a pinch cannot produce a jump.
                            prevCount = pointers.size
                            prevCentroid = centroid
                            prevPinchDist = dist
                            continue
                        }
                        if (pointers.size >= 2) {
                            if (prevPinchDist > 1f) {
                                panZoom.onPinch(dist / prevPinchDist, centroid)
                            }
                            prevPinchDist = dist
                        } else {
                            panZoom.onDrag(centroid - prevCentroid)
                        }
                        prevCentroid = centroid
                    }
                }
            }
    ) {
        PanZoomImage(panZoom = panZoom, painter = painter, name = name)
    }
}

