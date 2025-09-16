@file:Suppress("ktlint:standard:import-ordering")

package fr.oupson.jxlviewer.util

import android.graphics.Matrix
//noinspection ExifInterface
import android.media.ExifInterface

// TODO: Test this function
fun getMatrixForExifOrientation(orientation: Int, width: Int, height: Int): Matrix {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)

        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)

        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)

        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)

        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)

        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postScale(-1f, 1f)
            matrix.postRotate(90f)
        }

        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postScale(-1f, 1f)
            matrix.postRotate(270f)
        }
    }
    return matrix
}
