package fr.oupson.jxlviewer.ui.loading

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastRoundToInt

class JxlPainter(override val intrinsicSize: Size, firstFrame: Frame) :
    Painter(),
    RememberObserver {
    private var tick by mutableIntStateOf(0)

    private val frames: ArrayList<Frame> = ArrayList<Frame>()

    private var frameIndex: Int = 0

    private val handler = Handler(Looper.getMainLooper())

    private var lastDraw = SystemClock.uptimeMillis()

    private var isForgotten = false
    private var running = false
    private var looping = false

    init {
        frames.add(firstFrame)
    }

    data class Frame(val duration: Int, val image: ImageBitmap)

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            // why ?
            tick
            canvas.withSave {
                val image = frames[frameIndex].image
                drawImage(
                    image,
                    dstSize = IntSize(this@onDraw.size.width.fastRoundToInt(), this@onDraw.size.height.fastRoundToInt())
                )
            }
        }
    }

    private fun drawNext() {
        running = true
        frameIndex = (frameIndex + 1) % frames.size
        tick += 1
        handler.postAtTime({
            if ((looping || frameIndex + 1 < frames.size) && running) {
                lastDraw = SystemClock.uptimeMillis()
                drawNext()
            } else {
                running = false
            }
        }, lastDraw + frames[frameIndex].duration)
    }

    internal fun appendFrame(frame: Frame) {
        handler.post {
            frames.add(frame)
            if (frameIndex + 1 == frames.size - 1) {
                if (!running && !isForgotten) {
                    drawNext()
                }
            }
        }
    }

    fun start() {
        if (!isForgotten) {
            handler.post {
                looping = true
                if (!running) {
                    lastDraw = SystemClock.uptimeMillis()
                    drawNext()
                }
            }
        }
    }

    override fun onRemembered() {
        start()
        isForgotten = false
    }

    override fun onForgotten() {
        isForgotten = true
        handler.post {
            running = false
        }
    }

    override fun onAbandoned() = onForgotten()
}
