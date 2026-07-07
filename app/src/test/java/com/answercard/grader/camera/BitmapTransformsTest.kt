package com.answercard.grader.camera

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BitmapTransformsTest {
    @Test
    fun rotatesBitmapClockwiseByNinetyDegrees() {
        val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixel(0, 0, Color.BLACK)
        bitmap.setPixel(1, 2, Color.RED)

        val rotated = BitmapTransforms.rotate(bitmap, 90)

        assertEquals(3, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(Color.BLACK, rotated.getPixel(2, 0))
        assertEquals(Color.RED, rotated.getPixel(0, 1))
    }

    @Test
    fun returnsSameBitmapWhenRotationIsZero() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        assertEquals(bitmap, BitmapTransforms.rotate(bitmap, 0))
    }
}
