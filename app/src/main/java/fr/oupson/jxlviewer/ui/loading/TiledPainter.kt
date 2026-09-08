package fr.oupson.jxlviewer.ui.loading

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Paint
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
 * Draws a bitmap as a grid of [TILE_SIZE]x[TILE_SIZE] texture tiles instead of one
 * single huge texture. Android GPUs impose a hard limit on a single texture's
 * dimensions, so uploading a very large image in one draw can throw OutOfMemoryError;
 * splitting it into 2048px tiles keeps every upload well under that cap while the
 * total image stays visible.
 *
 * The source is kept at **full resolution** as long as it fits under the hard
 * [SAFE_DRAW_BYTES] Canvas budget, so the viewer can zoom to a true 1:1 of the
 * original image; only oversized sources are down-sampled once, bounding GPU memory.
 *
 * The tile map is pre-built on the calling (decoder/IO) thread in the constructor,
 * keeping the CPU-side tile copies off the main thread; Skia still uploads the
 * textures lazily at draw time.
 *
 * **8-bit (SDR) rendering:** each tile texture (content + bleed ring) is drawn
 * with exact float geometry (int-rect image draws under a float canvas transform),
 * so the whole image follows one continuous source->screen map: there is no
 * per-tile rounding phase, and the grid lines are seamless by construction.
 * BitmapShader local matrices are not used - this stack silently drops them on
 * the GPU display list (verified on-device).
 *
 * **F16 (HDR) rendering** keeps the integer bleed-overlap tiling: a shader paint
 * drops the F16 layer typing and the display stack would tone-map the buffer, and
 * the composite bitmap would lose HDR as well.
 */
class TiledPainter(
    private var img: Bitmap,
    // Whether this painter may recycle [img]. The loader passes the decoder's
    // live bitmap straight through (identity transform) while progressive
    // decoding still runs; recycling it would break the decoder's next
    // lockPixels. Copies (EXIF-rotated / animated frames) are owned instead.
    private val ownsImg: Boolean = true,
) : Painter() {
    override val intrinsicSize: Size = Size(img.width.toFloat(), img.height.toFloat())
    internal var filterQuality: FilterQuality = FilterQuality.High

    private var tileMap: TileMap? = null
    // Set once when the driver rejects a single-texture upload: the image is then
    // permanently tiled for the life of this painter.
    private var forceTiled = false
    private var failedTiles = 0

    // Paint for the 8-bit tile draws: high filter quality so the per-tile
    // resample is bilinear.
    private val tilePaint: Paint = Paint().apply { filterQuality = FilterQuality.High }

    init {
        // The painter is constructed on the decoder (IO) thread; pre-building the
        // tile map here keeps the CPU-side copies (Canvas tile split, oversized
        // down-scale) off the main thread. Failures are retried at draw time.
        try {
            tileMap = buildTileMap()
        } catch (t: Throwable) {
            tileMap = null
        }
    }

    private class TileMap(
        val tiles: List<Tile>,
        val width: Int,
        val height: Int,
        // Tiles are stored row-major (x first); cols x rows = tiles.size.
        val cols: Int,
        val rows: Int,
        // F16 (HDR) bitmaps must not go through the BitmapShader path: a shader
        // paint drops the F16 layer typing and the display stack tone-maps the
        // buffer (the README's route-A2 dead end). F16 keeps integer tiling.
        val f16: Boolean
    )

    private class Tile(
        val bitmap: Bitmap,
        // Top-left of the tile's *content*, in source image px.
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        // Texture outset present on each side (0 for edge tiles, [BLEED]
        // otherwise): the texture covers source rect
        // (left-leftBleed, top-topBleed .. left+width+rightBleed,
        //  top+height+bottomBleed) — the ring backs linear sampling at the
        // shared grid lines.
        val leftBleed: Int,
        val topBleed: Int,
        val rightBleed: Int,
        val bottomBleed: Int
    )

    /**
     * Optionally down-samples [img] only when it exceeds the hard [SAFE_DRAW_BYTES]
     * Canvas budget (full resolution otherwise, so 1:1 zoom stays possible), then
     * either keeps it as a single texture (when it is safely within the
     * per-texture budget) or splits it into tiles with a [BLEED]px texture-only
     * outset on each edge, recycling the (now redundant) source bitmap.
     */
    private fun buildTileMap(): TileMap {
        val existing = tileMap
        if (existing != null) return existing

        var source = img
        var ownsSource = ownsImg
        val bpp = if (source.config == Bitmap.Config.RGBA_F16) 8 else 4
        val srcBytes = source.width.toLong() * source.height * bpp
        var dstW = source.width
        var dstH = source.height
        // Hard byte budget: Canvas-based copies (createScaledBitmap, the F16
        // matrix draw, and the tile copies below) all throw for bitmaps above
        // ~197137920 bytes, so any source above SAFE_DRAW_BYTES must be scaled
        // down before tiling. Sources under the budget keep full resolution.
        if (srcBytes > SAFE_DRAW_BYTES) {
            val ratio = kotlin.math.sqrt(SAFE_DRAW_BYTES.toDouble() / srcBytes).toFloat()
            dstW = (source.width * ratio).toInt().coerceAtLeast(1)
            dstH = (source.height * ratio).toInt().coerceAtLeast(1)
        }
        if (dstW < source.width || dstH < source.height) {
            try {
                val scaled = if (srcBytes > SAFE_DRAW_BYTES) {
                    // Canvas cannot even READ a source above the limit, so scale
                    // it band-by-band through getPixels/setPixels (nearest
                    // neighbor; HDR above 1.0 is clamped - acceptable fallback).
                    bandScaledCopy(source, dstW, dstH)
                } else if (source.config == Bitmap.Config.RGBA_F16) {
                    val srcCs = source.colorSpace
                    if (srcCs != null) {
                        // HDR F16: scale inside the same color space so the space
                        // re-tagging validation is not triggered.
                        val btm = Bitmap.createBitmap(dstW, dstH, source.config!!, true, srcCs)
                        Canvas(btm).drawBitmap(source, null, Rect(0, 0, dstW, dstH), null)
                        btm
                    } else {
                        Bitmap.createScaledBitmap(source, dstW, dstH, true)
                    }
                } else {
                    Bitmap.createScaledBitmap(source, dstW, dstH, true)
                }
                // The scaled copy is always ours; the original source may still
                // belong to the decoder and must not be recycled in that case.
                if (ownsSource) source.recycle()
                ownsSource = true
                source = scaled
                img = scaled
            } catch (t: Throwable) {
                // Fall back to the original if the scaled copy failed.
            }
        }
        val w = source.width
        val h = source.height
        // Fast path: a single texture avoids tile seams entirely and costs one
        // drawImage. 64 MiB (4096^2 x 4B) is the minimum texture budget of any
        // OpenGL ES compliant GPU; the 4096px cap matches the smallest common
        // GL_MAX_TEXTURE_SIZE, so this path is safe on every Android device.
        val bytesPerPixel = if (source.config == Bitmap.Config.RGBA_F16) 8 else 4
        if (!forceTiled &&
            maxOf(w, h) <= MAX_SINGLE_TEXTURE_PX &&
            w.toLong() * h * bytesPerPixel <= MAX_SINGLE_TEXTURE_BYTES
        ) {
            val f16 = source.config == Bitmap.Config.RGBA_F16
            tileMap = TileMap(listOf(Tile(source, 0, 0, w, h, 0, 0, 0, 0)), w, h, 1, 1, f16)
            return tileMap!!
        }
        val cols = (w + TILE_SIZE - 1) / TILE_SIZE
        val rows = (h + TILE_SIZE - 1) / TILE_SIZE
        val tiles = ArrayList<Tile>(cols * rows)
        var y = 0
        while (y < h) {
            val h0 = min(TILE_SIZE, h - y)
            var x = 0
            while (x < w) {
                val w0 = min(TILE_SIZE, w - x)
                // Bleed exists only where a neighboring tile exists, so the
                // tile bitmap covers source rect (bx, by .. bx+bw, by+bh).
                val bx = if (x > 0) x - BLEED else 0
                val by = if (y > 0) y - BLEED else 0
                val bx1 = if (x + w0 < w) x + w0 + BLEED else w
                val by1 = if (y + h0 < h) y + h0 + BLEED else h
                val bw = bx1 - bx
                val bh = by1 - by
                // Preserves the source color space (important for HDR F16 bitmaps).
                val cs = source.colorSpace
                val tileBtm = if (cs != null) {
                    Bitmap.createBitmap(bw, bh, source.config!!, true, cs)
                } else {
                    Bitmap.createBitmap(bw, bh, source.config!!)
                }
                // Copies source region (bx, by .. bx1, by1) into the tile, so the
                // tile's origin holds source pixel (bx, by): the [BLEED]px ring of
                // neighbor pixels backs linear sampling at the shared grid lines.
                // Note: no prepareToDraw() here - it uploads the (still
                // zero-filled) bitmap to the GPU up front and that upload can fail
                // silently for F16/csc bitmaps on non-HDR displays, leaving
                // pure-black tiles. Skia uploads at draw time.
                Canvas(tileBtm).drawBitmap(source, Rect(bx, by, bx1, by1), Rect(0, 0, bw, bh), null)
                tiles.add(Tile(tileBtm, x, y, w0, h0, x - bx, y - by, bx1 - (x + w0), by1 - (y + h0)))
                x += w0
            }
            y += h0
        }
        if (ownsSource) source.recycle()
        img = tiles.first().bitmap
        val map = TileMap(tiles, w, h, cols, rows, source.config == Bitmap.Config.RGBA_F16)
        tileMap = map
        return map
    }


    /**
     * Nearest-neighbor down-scale via row-band getPixels/setPixels. Unlike every
     * Canvas-based copy, this works for sources above the ~197MB Canvas limit
     * (getPixels/setPixels have no size check). Quality: nearest neighbor;
     * HDR values above 1.0 clamp through the ARGB conversion. Only used for
     * pathologically large decodes where the alternative is a crash.
     */
    private fun bandScaledCopy(source: Bitmap, dstW: Int, dstH: Int): Bitmap {
        // getPixels/setPixels only round-trip ARGB ints, so:
        // - F16 sources (HDR *and* SDR-on-wide-gamut, since the decoder emits
        //   F16 whenever the app requested it) must be converted to ARGB_8888:
        //   the driver maps the values into the display range on the way out,
        //   avoiding the dark mid-tones of an F16->int->F16 re-encode. True
        //   HDR highlights above 1.0 clamp; acceptable for a >197MB fallback.
        // - the destination uses straight alpha (the decoder's default), so
        //   8-bit pixels round-trip losslessly.
        val dstConfig = if (source.config == Bitmap.Config.RGBA_F16) {
            Bitmap.Config.ARGB_8888
        } else {
            source.config!!
        }
        val dst = Bitmap.createBitmap(dstW, dstH, dstConfig)
        val sw = source.width
        val sh = source.height
        val bandH = 256
        val rowBuf = IntArray(sw)
        val dstBuf = IntArray(dstW * bandH)
        var y0 = 0
        while (y0 < dstH) {
            val bh = min(bandH, dstH - y0)
            for (dy in y0 until y0 + bh) {
                val sy = (dy.toLong() * sh / dstH).toInt().coerceIn(0, sh - 1)
                source.getPixels(rowBuf, 0, sw, 0, sy, sw, 1)
                val rowOut = (dy - y0) * dstW
                if (sw == dstW) {
                    System.arraycopy(rowBuf, 0, dstBuf, rowOut, sw)
                } else {
                    var o = rowOut
                    for (dx in 0 until dstW) {
                        val sx = (dx.toLong() * sw / dstW).toInt().coerceIn(0, sw - 1)
                        dstBuf[o++] = rowBuf[sx]
                    }
                }
            }
            dst.setPixels(dstBuf, 0, dstW, 0, y0, dstW, bh)
            y0 += bh
        }
        return dst
    }

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            canvas.withSave {
                try {
                    // Pre-built on the decoder thread by the constructor; the
                    // lazy build below only covers construction-time failures.
                    val map = tileMap ?: buildTileMap()
                    drawTiles(map, this.size.isSpecified)
                } catch (_: Throwable) {
                    // The driver rejected the upload (e.g. the single-texture fast
                    // path overshot this GPU's real budget), or the tile split hit
                    // allocation limits. Force tiled mode and retry once; if that
                    // fails too, leave the frame blank instead of crashing.
                    if (!forceTiled) {
                        forceTiled = true
                        tileMap = null
                        try {
                            val tiled = buildTileMap()
                            drawTiles(tiled, this.size.isSpecified)
                        } catch (_: Throwable) {
                            failedTiles = Int.MAX_VALUE
                        }
                    } else {
                        failedTiles = Int.MAX_VALUE
                    }
                }
            }
        }
    }

    private fun DrawScope.drawTiles(map: TileMap, sizeSpecified: Boolean) {
        if (sizeSpecified) {
            val canvasSize = this.size
            val sx = canvasSize.width / map.width.toFloat()
            val sy = canvasSize.height / map.height.toFloat()
            val cw = canvasSize.width.fastRoundToInt()
            val ch = canvasSize.height.fastRoundToInt()

            if (map.f16) {
                // HDR F16: keep the original integer bleed-overlap tiling. A shader
                // paint would drop the F16 layer typing and the display stack would
                // tone-map the buffer; the composite bitmap would lose HDR as well.
                drawTilesInteger(map, sx, sy, cw, ch)
            } else {
                // Each tile texture is placed with the canvas CTM (save /
                // translate / scale / restore): an ORIGIN-based float transform,
                // so every tile follows one continuous source->screen map and the
                // grid lines are seamless by construction, with the
                // full-resolution textures keeping 1:1 zoom native. (The
                // DrawScope-level withTransform applies its scale about the scope
                // center, which uniformly offsets every tile - verified
                // on-device; a platform BitmapShader local matrix is silently
                // dropped / rendered empty on the HWUI display list - also
                // verified.)
                drawIntoCanvas { canvas ->
                    for (tile in map.tiles) {
                        val x0f = (tile.left - tile.leftBleed) * sx
                        val y0f = (tile.top - tile.topBleed) * sy
                        val tw = tile.width + tile.leftBleed + tile.rightBleed
                        val th = tile.height + tile.topBleed + tile.bottomBleed
                        if (tw <= 0 || th <= 0) continue
                        try {
                            canvas.save()
                            canvas.translate(x0f, y0f)
                            canvas.scale(sx, sy)
                            canvas.drawImage(tile.bitmap.asImageBitmap(), Offset(0f, 0f), tilePaint)
                            canvas.restore()
                        } catch (t: Throwable) {
                            failedTiles++
                            if (failedTiles <= 5) {
                                Log.e(TAG, "tile draw failed at ($x0f, $y0f)", t)
                            }
                        }
                    }
                }
            }
        } else {
            // 1:1 canvas: integer mapping, texture placed at its source position
            // (bleed ring included; identical neighbors overdraw invisibly).
            for (tile in map.tiles) {
                drawTileSafe(
                    tile.bitmap,
                    IntOffset(tile.left - tile.leftBleed, tile.top - tile.topBleed),
                    IntSize(
                        tile.width + tile.leftBleed + tile.rightBleed,
                        tile.height + tile.topBleed + tile.bottomBleed
                    )
                )
            }
        }
    }

    /**
     * Integer bleed-overlap tiling: each tile texture (content + 1px bleed) is
     * stretched to its rounded screen rect. Used for F16 (HDR) bitmaps, which
     * must not go through the shader path (a shader paint drops the F16 layer
     * typing and the display stack tone-maps the buffer), and as the fallback
     * when the low-level canvas is unavailable. Adjacent
     * tiles overlap by the bleed, so no hairline gap can appear.
     */
    private fun DrawScope.drawTilesInteger(map: TileMap, sx: Float, sy: Float, cw: Int, ch: Int) {
        for (tile in map.tiles) {
            val x0 = ((tile.left - tile.leftBleed) * sx).fastRoundToInt().coerceAtLeast(0)
            val y0 = ((tile.top - tile.topBleed) * sy).fastRoundToInt().coerceAtLeast(0)
            val x1 = ((tile.left + tile.width + tile.rightBleed) * sx).fastRoundToInt()
                .coerceAtMost(cw)
            val y1 = ((tile.top + tile.height + tile.bottomBleed) * sy).fastRoundToInt()
                .coerceAtMost(ch)
            val dw = x1 - x0
            val dh = y1 - y0
            if (dw <= 0 || dh <= 0) continue
            drawTileSafe(tile.bitmap, IntOffset(x0, y0), IntSize(dw, dh))
        }
    }

    /**
     * Isolates per-tile upload/draw failures so one bad tile cannot take the
     * whole frame down, and is visible in logcat.
     */
    private fun DrawScope.drawTileSafe(btm: Bitmap, dstOffset: IntOffset, dstSize: IntSize) {
        try {
            drawImage(
                btm.asImageBitmap(),
                dstOffset = dstOffset,
                dstSize = dstSize,
                alpha = 1.0f,
                filterQuality = filterQuality
            )
        } catch (t: Throwable) {
            failedTiles++
            if (failedTiles <= 5) {
                Log.e(TAG, "tile draw failed at $dstOffset size=$dstSize", t)
            }
        }
    }

    private companion object {
        private const val TAG = "TiledPainter"

        /** Tile texture size in px; comfortably below the minimum max-texture size
         * of all Android GPUs (typically 4096+). */
        const val TILE_SIZE = 2048

        /** 1px texture outset ("bleed") copied from the neighbor on each tile edge. */
        // Texture outset on each side of a tile's nominal rect. The GPU resample
        // kernel when down-scaling a texture (which is what every sub-1x view is)
        // has support of roughly 3x the down-scale ratio (measured: ~8 source px
        // of edge artifact at a 2.7:1 ratio); the kernel samples OUTSIDE the
        // texture at its edges and clamps, painting a visible line at every grid
        // line. A wide bleed ring keeps the kernel inside real content, and the
        // adjacent tile over-draws the ring with identical data, so the line
        // cannot appear. 48 covers ratios up to ~16:1 (gallery thumbnails).
        const val BLEED = 48

        /** Largest dimension that may be uploaded as a single texture (the
         * smallest common GL_MAX_TEXTURE_SIZE). */
        const val MAX_SINGLE_TEXTURE_PX = 4096

        /** 64 MiB = 4096^2 x 4B: the minimum per-texture budget of any OpenGL
         * ES compliant GPU, so a bitmap within both this budget and the size
         * cap is a safe single upload on every Android device. (Larger
         * single uploads are not attempted: if a driver ever rejects one,
         * onDraw falls back to tiled mode via forceTiled.) */
        const val MAX_SINGLE_TEXTURE_BYTES = 64L * 1024 * 1024

        /** Canvas refuses to draw/copy bitmaps above ~197137920 bytes
         * ("trying to draw too large bitmap"); 190 MB keeps a margin below it.
         * Both the oversample cap and any oversized source must respect this. */
        const val SAFE_DRAW_BYTES = 190_000_000L
    }
}

