package com.answercard.grader.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.answercard.grader.template.TemplateGeometry
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CornerDetectorTest {
    @Test
    fun detectsFourPositionCornersInGeneratedCard() {
        val scale = 3f
        val bitmap = Bitmap.createBitmap(
            (TemplateGeometry.CARD_WIDTH * scale).toInt(),
            ((TemplateGeometry.HEADER_HEIGHT + TemplateGeometry.HEADER_DIVIDER_GAP +
                TemplateGeometry.QUESTION_BAND_HEIGHT + TemplateGeometry.CARD_MARGIN_BOTTOM) * scale).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(Color.WHITE)
        drawCorners(bitmap, scale)

        val corners = CornerDetector.detect(bitmap)

        requireNotNull(corners)
        assertTrue("topLeft=$corners", corners.topLeft.x < 20)
        assertTrue("topLeft=$corners", corners.topLeft.y < 20)
        assertTrue("topRight=$corners", corners.topRight.x > bitmap.width - 160)
        assertTrue("topRight=$corners", corners.topRight.y < 20)
        assertTrue("bottomLeft=$corners", corners.bottomLeft.x < 20)
        assertTrue("bottomLeft=$corners", corners.bottomLeft.y > bitmap.height - 160)
        assertTrue("bottomRight=$corners", corners.bottomRight.x > bitmap.width - 160)
        assertTrue("bottomRight=$corners", corners.bottomRight.y > bitmap.height - 160)
    }

    @Test
    fun returnsNullWhenCardCornersAreMissing() {
        val blank = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888)
        blank.eraseColor(Color.WHITE)

        assertNull(CornerDetector.detect(blank))
    }

    private fun drawCorners(bitmap: Bitmap, scale: Float) {
        val size = (TemplateGeometry.CORNER_BRACKET_SIZE * scale).toInt()
        val thick = (TemplateGeometry.CORNER_BRACKET_THICKNESS * scale).toInt()
        val width = bitmap.width
        val height = bitmap.height
        fillRect(bitmap, 0, 0, size, thick)
        fillRect(bitmap, 0, 0, thick, size)
        fillRect(bitmap, width - size, 0, width, thick)
        fillRect(bitmap, width - thick, 0, width, size)
        fillRect(bitmap, 0, height - thick, size, height)
        fillRect(bitmap, 0, height - size, thick, height)
        fillRect(bitmap, width - size, height - thick, width, height)
        fillRect(bitmap, width - thick, height - size, width, height)
    }

    private fun fillRect(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int) {
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) {
            for (x in left.coerceAtLeast(0) until right.coerceAtMost(bitmap.width)) {
                bitmap.setPixel(x, y, Color.BLACK)
            }
        }
    }
}
