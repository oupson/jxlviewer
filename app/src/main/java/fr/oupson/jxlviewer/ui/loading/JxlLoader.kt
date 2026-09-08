package fr.oupson.jxlviewer.ui.loading

import android.content.ContentResolver
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import fr.oupson.jxlviewer.util.getMatrixForExifOrientation
import fr.oupson.libjxl.JxlDecoder
import java.io.FileNotFoundException
import java.net.URL
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class JxlLoader internal constructor(
    private val jxlDecoder: JxlDecoder,
    private val assetManager: AssetManager,
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    private val decodePreview: DecodePreview = DecodePreview.WithFullImage,
    private val animated: Boolean = true
) : RememberObserver {
    private val scope = CoroutineScope(Dispatchers.IO + CoroutineName(TAG))

    private var _state: MutableStateFlow<JxlState> = MutableStateFlow(JxlState.Empty)
    fun state(): StateFlow<JxlState> = _state.asStateFlow()

    private var job: Job? = null

    private fun getLoadingJob() = scope.launch {
        try {
            _state.emit(JxlState.Loading)
            var intrinsicSize: Size? = null
            var haveAnimation: Boolean = false
            var jxlPainter: JxlPainter? = null
            var transformMatrix = Matrix()
            val options = JxlDecoder.Options().setFormat(config).setDecodeProgressive(decodePreview != DecodePreview.Disabled)
            val callback = object : JxlDecoder.Callback {
                override fun onHeaderDecoded(
                    width: Int, height: Int, intrinsicWidth: Int, intrinsicHeight: Int, isAnimated: Boolean, orientation: Int
                ): Boolean {
                    intrinsicSize = Size(width.toFloat(), height.toFloat())
                    haveAnimation = isAnimated && this@JxlLoader.animated

                    if (this@JxlLoader.decodePreview != DecodePreview.WithoutFullImage) {
                        transformMatrix = getMatrixForExifOrientation(
                            orientation, width, height
                        )
                    }
                    return true
                }

                override fun onProgressiveFrame(btm: Bitmap): Boolean {
                    if (isActive) {
                        // Identity transform: reuse the decoder's bitmap directly
                        // (as DecodePreview.WithoutFullImage always did) instead of
                        // paying a full-size copy per progressive flush — a 96 MB+
                        // memcpy for each step of a high-res image. The decoder
                        // still owns the bitmap, so TiledPainter must not recycle
                        // it (ownsImg = false); a non-identity transform returns a
                        // fresh bitmap the painter owns.
                        val identity = transformMatrix.isIdentity
                        val displayBitmap = if (identity) {
                            btm
                        } else {
                            btm.copyBitmap(transformMatrix)
                        }
                        _state.tryEmit(
                            JxlState.Preview(
                                TiledPainter(
                                    displayBitmap,
                                    ownsImg = !identity
                                )
                            )
                        )
                        return this@JxlLoader.decodePreview == DecodePreview.WithFullImage
                    } else {
                        return false
                    }
                }

                override fun onFrameDecoded(duration: Int, btm: Bitmap): Boolean {
                    if (isActive) {
                        // Terminal frame of a non-animated image with an identity
                        // transform: the decoder is done with this bitmap, so use it
                        // directly (skip the full-size copy) — from here on the
                        // painter owns it. Animated frames and EXIF-rotated frames
                        // keep the copy: the decoder reuses/keeps its bitmap.
                        val displayBitmap = if (transformMatrix.isIdentity && !haveAnimation) {
                            btm
                        } else {
                            btm.copyBitmap(transformMatrix)
                        }
                        return if (haveAnimation) {
                            if (jxlPainter == null) {
                                jxlPainter = JxlPainter(intrinsicSize!!, JxlPainter.Frame(duration, displayBitmap.asImageBitmap()))
                                _state.tryEmit(JxlState.Loaded(jxlPainter))
                            } else {
                                jxlPainter.appendFrame(JxlPainter.Frame(duration, displayBitmap.asImageBitmap()))
                            }
                            true
                        } else {
                            // Tiled upload: a single GPU texture is size-capped, so a
                            // large full-resolution image must be drawn as 2048px
                            // tiles (with 1px bleed) or the texture upload OOMs.
                            _state.tryEmit(JxlState.Loaded(TiledPainter(displayBitmap)))
                            false
                        }
                    } else {
                        return false
                    }
                }
            }

            // TODO: handle null
            when (uri.scheme) {
                "http", "https" -> {
                    URL(uri.toString()).openConnection().inputStream.use {
                        jxlDecoder.decodeImage(
                            it, options, callback
                        )
                    }
                }

                "asset" -> {
                    assetManager.open(requireNotNull(uri.path).removePrefix("/"), AssetManager.ACCESS_STREAMING).use { inputStream ->
                        jxlDecoder.decodeImage(
                            inputStream, options, callback
                        )
                    }
                }

                else -> {
                    try {
                        contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                            jxlDecoder.decodeImage(
                                fd, options, callback
                            )
                        }
                    } catch (_: FileNotFoundException) {
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            jxlDecoder.decodeImage(
                                inputStream, options, callback
                            )
                        }
                    }
                }
            }

            if (isActive) {
                jxlPainter?.start()
            }
        } catch (e: Exception) {
            _state.emit(JxlState.Error(e))
        }
    }

    /**
     * Using HARDWARE may be a good idea, but we can't cause the bitmap only exist in the callback, and uploading is asynchronous.
     */
    private fun Bitmap.copyBitmap(transformMatrix: Matrix): Bitmap {
        val btm = if (transformMatrix.isIdentity) {
            this.copy(requireNotNull(this.config), true)
        } else {
            Bitmap.createBitmap(this, 0, 0, this.width, this.height, transformMatrix, true)
        }
        btm.prepareToDraw()
        return requireNotNull(btm)
    }

    override fun onRemembered() {
        job = getLoadingJob()
        when (val state = _state.value) {
            is JxlState.Loaded -> (state.painter as? RememberObserver)?.onRemembered()
            else -> {}
        }
    }

    override fun onForgotten() {
        job?.cancel()
        job = null
        when (val state = _state.value) {
            is JxlState.Loaded -> (state.painter as? RememberObserver)?.onForgotten()
            else -> {}
        }
    }

    override fun onAbandoned() {
        job?.cancel()
        job = null
        when (val state = _state.value) {
            is JxlState.Loaded -> (state.painter as? RememberObserver)?.onAbandoned()
            else -> {}
        }
    }

    sealed interface JxlState {
        data object Empty : JxlState

        data object Loading : JxlState

        data class Preview(val painter: Painter) : JxlState

        data class Loaded(val painter: Painter) : JxlState

        data class Error(val error: Throwable) : JxlState
    }

    enum class DecodePreview {
        WithFullImage, WithoutFullImage, Disabled
    }

    companion object {
        private const val TAG = "JxlLoader"
    }
}

val LocalDecoder = compositionLocalOf { JxlDecoder() }

@Composable
fun rememberJxlLoader(
    uri: Uri,
    config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    decodePreview: JxlLoader.DecodePreview = JxlLoader.DecodePreview.WithFullImage,
    animated: Boolean = true
): JxlLoader {
    val context = LocalContext.current
    val decoder = LocalDecoder.current
    return remember(uri, config, decodePreview, animated) {
        JxlLoader(
            decoder, context.assets, context.contentResolver, uri, config, decodePreview, animated
        )
    }
}
