package fr.oupson.jxlviewer.ui.loading

import android.graphics.Bitmap
import android.util.Log
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
import androidx.core.graphics.scale
import kotlin.math.min

/**
 * Dirty fix for the "to big bitmap"
 */
class ResizePainter(
    val img: Bitmap,
) : Painter() {
    override val intrinsicSize: Size = Size(img.width.toFloat(), img.height.toFloat())
    internal var filterQuality: FilterQuality = FilterQuality.Low

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            canvas.withSave {
                if (this.size.isSpecified) {
                    val canvasSize = this@onDraw.size
                    val ratio = min((canvasSize.width / img.width), (canvasSize.height / img.height))
                    val btm = img.scale((img.width * ratio).fastRoundToInt(), (img.height * ratio).fastRoundToInt())
                    drawImage(
                        btm.asImageBitmap(),
                        srcSize = IntSize(btm.width, btm.height),
                        dstSize = IntSize(
                            btm.width,
                            btm.height,
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
