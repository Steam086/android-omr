package com.answercard.grader.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.answercard.grader.template.CardLayout
import com.answercard.grader.template.TemplateGeometry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExamIdReaderTest {
    @Test
    fun readsFourFilledExamIdDigits() {
        val layout = TemplateGeometry.buildLayout()
        val bitmap = blankBitmap(layout, scale = 3f)
        fillDigit(bitmap, layout, column = 0, digit = 1, scale = 3f)
        fillDigit(bitmap, layout, column = 1, digit = 2, scale = 3f)
        fillDigit(bitmap, layout, column = 2, digit = 3, scale = 3f)
        fillDigit(bitmap, layout, column = 3, digit = 4, scale = 3f)

        assertEquals("1234", ExamIdReader.readExamId(bitmap, layout, scale = 3f))
    }

    @Test
    fun returnsNullWhenAnyExamIdColumnIsBlank() {
        val layout = TemplateGeometry.buildLayout()
        val bitmap = blankBitmap(layout, scale = 3f)
        fillDigit(bitmap, layout, column = 0, digit = 1, scale = 3f)
        fillDigit(bitmap, layout, column = 1, digit = 2, scale = 3f)
        fillDigit(bitmap, layout, column = 3, digit = 4, scale = 3f)

        assertEquals(null, ExamIdReader.readExamId(bitmap, layout, scale = 3f))
    }

    private fun blankBitmap(layout: CardLayout, scale: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(
            (layout.width * scale).roundToInt(),
            (layout.height * scale).roundToInt(),
            Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(Color.WHITE)
        return bitmap
    }

    private fun fillDigit(bitmap: Bitmap, layout: CardLayout, column: Int, digit: Int, scale: Float) {
        val box = TemplateGeometry.examIdDigitBox(layout, column, digit)
        val left = ((box.x + 2f) * scale).toInt()
        val top = ((box.y + 2f) * scale).toInt()
        val right = ((box.x + box.w - 2f) * scale).toInt()
        val bottom = ((box.y + box.h - 2f) * scale).toInt()
        for (y in top until bottom) {
            for (x in left until right) {
                bitmap.setPixel(x, y, Color.BLACK)
            }
        }
    }
}
