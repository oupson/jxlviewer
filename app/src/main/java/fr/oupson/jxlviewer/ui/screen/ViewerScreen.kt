package fr.oupson.jxlviewer.ui.screen

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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

            var containerSize by remember { mutableStateOf(IntSize.Zero) }
            var intrinsicPx by remember { mutableStateOf(IntSize.Zero) }
            // Pan/zoom state lives in a holder object: the ViewerScreen body
            // never reads scale/offset during composition, so per-gesture
            // updates only recompose the small PanZoomImage subtree instead of
            // the whole screen (toolbar, app bar, dialogs, ...).
            val panZoom = remember { PanZoomState() }
            // Panning is only allowed along an axis once the zoomed image overflows
            // the container on that axis; along a fitted axis the image stays
            // centered. containerSize = the full-screen Box, intrinsicPx = the
            // decoded image in px, scale = the current zoom factor (1 = fit).
            val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
                panZoom.onGesture(zoomChange, offsetChange, containerSize, intrinsicPx)
            }

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
                    PanZoomContent(
                        panZoom = panZoom, painter = s.painter, name = name,
                        onContainerSize = { containerSize = it },
                        onIntrinsicPx = { intrinsicPx = it },
                        transformableState = transformableState
                    )
                }

                // The preview uses the SAME pan/zoom box and state: the user can
                // zoom/pan the low-res preview immediately, and the transform is
                // carried over unchanged when the full-resolution load arrives
                // (progressive decoding keeps the aspect ratio, so the fit box is
                // identical).
                is JxlLoader.JxlState.Preview -> {
                    PanZoomContent(
                        panZoom = panZoom, painter = s.painter, name = name,
                        onContainerSize = { containerSize = it },
                        onIntrinsicPx = { intrinsicPx = it },
                        transformableState = transformableState
                    )
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

    fun onGesture(
        zoomChange: Float,
        offsetChange: Offset,
        container: IntSize,
        imageIntrinsic: IntSize
    ) {
        // TEMP diagnostic (quiet for A/B)
        if (zoomChange != 1f) {
            val maxZoom = maxZoomForOriginal(container, imageIntrinsic)
            scale.floatValue = (scale.floatValue * zoomChange).coerceIn(MIN_ZOOM_SCALE, maxZoom)
        }
        if (offsetChange != Offset.Zero) {
            offset.value = (offset.value + offsetChange).clamped(container, imageIntrinsic, scale.floatValue)
        }
    }

    /**
     * Max fit-scale for [imageIntrinsic] in [container], capped at
     * [ZOOM_CAP_ORIGINAL_MULT]x the *original* image resolution: at fit-scale
     * S the original is magnified by S x fit, so the cap is
     * ZOOM_CAP_ORIGINAL_MULT / fit. The result is floored at 1 so that small
     * images (which ContentScale.Fit already magnifies beyond the cap) keep a
     * valid [1, max] range, and hard-capped at [HARD_MAX_ZOOM_SCALE].
     */
    private fun maxZoomForOriginal(container: IntSize, imageIntrinsic: IntSize): Float {
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
    name: String?,
    onContainerSize: (IntSize) -> Unit,
    onIntrinsicPx: (IntSize) -> Unit,
    transformableState: TransformableState
) {
    LaunchedEffect(painter) {
        val sz = painter.intrinsicSize
        if (sz.width > 0f && sz.height > 0f) {
            onIntrinsicPx(IntSize(sz.width.roundToInt(), sz.height.roundToInt()))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { onContainerSize(it) }
            .transformable(state = transformableState)
    ) {
        PanZoomImage(panZoom = panZoom, painter = painter, name = name)
    }
}

