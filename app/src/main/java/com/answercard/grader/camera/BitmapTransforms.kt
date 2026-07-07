package com.answercard.grader.camera

import android.graphics.Bitmap

object BitmapTransforms {
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return bitmap

        return when (normalized) {
            90 -> rotate90(bitmap)
            180 -> rotate180(bitmap)
            270 -> rotate270(bitmap)
            else -> bitmap
        }
    }

    private fun rotate90(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.height, bitmap.width, bitmap.config ?: Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                output.setPixel(bitmap.height - 1 - y, x, bitmap.getPixel(x, y))
            }
        }
        return output
    }

    private fun rotate180(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                output.setPixel(bitmap.width - 1 - x, bitmap.height - 1 - y, bitmap.getPixel(x, y))
            }
        }
        return output
    }

    private fun rotate270(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.height, bitmap.width, bitmap.config ?: Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                output.setPixel(y, bitmap.width - 1 - x, bitmap.getPixel(x, y))
            }
        }
        return output
    }
}
