package fr.oupson.jxlviewer.ui.loading

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastRoundToInt
import kotlin.math.min

/**
 * Keeps the texture at a high resolution (original size, capped at OVERSAMPLE x the
 * canvas) instead of down-sampling to the canvas size on every draw.
 *
 * The previous behavior scaled the bitmap down to the canvas size, so the GPU zoom
 * in ViewerScreen sampled an already-downsampled image and enlarging never brought
 * back detail. With a full-resolution texture:
 * - at 100% zoom the GPU bilinearly down-samples the texture (crisp),
 * - within the OVERSAMPLE factor the user sees 1:1 real pixels (sharp),
 * - textures beyond the cap are down-sampled once, bounding GPU memory.
 */
class ResizePainter(
    val img: Bitmap,
) : Painter() {
    override val intrinsicSize: Size = Size(img.width.toFloat(), img.height.toFloat())
    internal var filterQuality: FilterQuality = FilterQuality.High

    private companion object {
        // 5x the screen canvas covers 4K camera originals (>= ~11880 px wide on a
        // 1080p-class screen), so typical photos are uploaded at native resolution.
        const val OVERSAMPLE = 5f
    }

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            canvas.withSave {
                if (this.size.isSpecified) {
                    val canvasSize = this@onDraw.size
                    val capW = (canvasSize.width * OVERSAMPLE).fastRoundToInt()
                    val capH = (canvasSize.height * OVERSAMPLE).fastRoundToInt()
                    val dstW = min(img.width, capW)
                    val dstH = min(img.height, capH)
                    val btm: Bitmap = if (dstW >= img.width && dstH >= img.height) {
                        // Original image fits within the oversample cap: use it as-is.
                        img
                    } else {
                        try {
                            val srcCs = img.colorSpace
                            if (img.config == Bitmap.Config.RGBA_F16 && srcCs != null) {
                                // HDR F16: scale inside the same color space so the
                                // space re-tagging validation is not triggered.
                                val scaled = Bitmap.createBitmap(dstW, dstH, requireNotNull(img.config), true, srcCs)
                                Canvas(scaled).drawBitmap(img, null, Rect(0, 0, dstW, dstH), null)
                                scaled
                            } else {
                                Bitmap.createScaledBitmap(img, dstW, dstH, true)
                            }
                        } catch (t: Throwable) {
                            // Fall back to drawing the original if the scaled copy failed.
                            img
                        }
                    }
                    // Fill the ContentScale.Fit box 1:1; enlarging is done by the
                    // graphicsLayer GPU sampling of this texture.
                    drawImage(
                        btm.asImageBitmap(),
                        srcSize = IntSize(btm.width, btm.height),
                        dstSize = IntSize(
                            canvasSize.width.fastRoundToInt(),
                            canvasSize.height.fastRoundToInt(),
                        ),
                        alpha = 1.0f,
                        filterQuality = filterQuality,
                    )
                } else {
                    drawImage(
                        img.asImageBitmap(),
                        IntOffset.Zero,
                        IntSize(img.width, img.height),
                        dstSize = IntSize(
                            this@onDraw.size.width.fastRoundToInt(),
                            this@onDraw.size.height.fastRoundToInt(),
                        ),
                        alpha = 1.0f,
                        filterQuality = filterQuality,
                    )
                }
            }
        }
    }
}
