package com.answercard.grader.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.answercard.grader.template.TemplateGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PerspectiveWarpTest {
    @Test
    fun normalizesGeneratedCardToTemplateSize() {
        val layout = TemplateGeometry.buildLayout()
        val bitmap = Bitmap.createBitmap(
            (layout.width * 3f).roundToInt(),
            (layout.height * 3f).roundToInt(),
            Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(Color.WHITE)
        drawCorners(bitmap, scale = 3f)
        val corners = requireNotNull(CornerDetector.detect(bitmap))

        val normalized = PerspectiveWarp.normalize(bitmap, corners, layout, scale = 3f)

        assertEquals((layout.width * 3f).roundToInt(), normalized.width)
        assertEquals((layout.height * 3f).roundToInt(), normalized.height)
        assertNotNull(CornerDetector.detect(normalized))
    }

    private fun drawCorners(bitmap: Bitmap, scale: Float) {
        val size = (TemplateGeometry.CORNER_BRACKET_SIZE * scale).roundToInt()
        val thick = (TemplateGeometry.CORNER_BRACKET_THICKNESS * scale).roundToInt()
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
