package com.answercard.grader.camera

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

object ImageProxyBitmap {
    fun grayscaleBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes.first()
        val buffer = plane.buffer
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val luminance = buffer.unsignedByteAt(y * rowStride + x * pixelStride)
                bitmap.setPixel(x, y, Color.rgb(luminance, luminance, luminance))
            }
        }

        return BitmapTransforms.rotate(bitmap, image.imageInfo.rotationDegrees)
    }

    private fun ByteBuffer.unsignedByteAt(index: Int): Int = get(index).toInt() and 0xFF
}
