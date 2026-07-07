package com.answercard.grader.miniprogram

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.Rect
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateRenderer
import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidOmrFrameProcessorTest {
    @Test
    fun processorScansFilledProductionTemplateFrame() {
        val template = templateForProcessor()
        val frame = filledProductionTemplateBitmap(template, scale = 3f).toMiniProgramFrame()

        val result = AndroidOmrFrameProcessor().process(frame = frame, template = template)

        assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 0 }?.selectedLabels)
        assertEquals(listOf("B"), result.answerArea?.questions?.single { it.questionIndex == 1 }?.selectedLabels)
        assertEquals(listOf("C"), result.answerArea?.questions?.single { it.questionIndex == 5 }?.selectedLabels)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 15 }?.selectedLabels)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    private fun filledProductionTemplateBitmap(template: TemplateState, scale: Float): Bitmap {
        val bitmap = TemplateRenderer.render(template, scale = scale)
        markProductionAdmissionNumber(bitmap, template, "1234", scale)
        markProductionAnswer(bitmap, template, questionNumber = 1, optionLabel = "A", scale = scale)
        markProductionAnswer(bitmap, template, questionNumber = 2, optionLabel = "B", scale = scale)
        markProductionAnswer(bitmap, template, questionNumber = 6, optionLabel = "C", scale = scale)
        markProductionAnswer(bitmap, template, questionNumber = 11, optionLabel = "D", scale = scale)
        markProductionAnswer(bitmap, template, questionNumber = 16, optionLabel = "A", scale = scale)
        return bitmap
    }

    private fun markProductionAdmissionNumber(
        bitmap: Bitmap,
        template: TemplateState,
        digits: String,
        scale: Float,
    ) {
        val layout = TemplateGeometry.buildLayout(template)
        digits.forEachIndexed { digitIndex, char ->
            if (char.isDigit()) {
                markProductionRect(
                    bitmap = bitmap,
                    rect = TemplateGeometry.examIdDigitBox(layout, digitIndex, char.digitToInt()),
                    scale = scale,
                )
            }
        }
    }

    private fun markProductionAnswer(
        bitmap: Bitmap,
        template: TemplateState,
        questionNumber: Int,
        optionLabel: String,
        scale: Float,
    ) {
        val layout = TemplateGeometry.buildLayout(template)
        val rect = layout.options.single { it.question == questionNumber && it.option == optionLabel }.rect
        markProductionRect(bitmap = bitmap, rect = rect, scale = scale)
    }

    private fun markProductionRect(bitmap: Bitmap, rect: Rect, scale: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val insetX = rect.w * 0.18f
        val insetY = rect.h * 0.18f
        Canvas(bitmap).drawRect(
            (TemplateGeometry.PAGE_MARGIN + rect.x + insetX) * scale,
            (TemplateGeometry.PAGE_MARGIN + rect.y + insetY) * scale,
            (TemplateGeometry.PAGE_MARGIN + rect.x + rect.w - insetX) * scale,
            (TemplateGeometry.PAGE_MARGIN + rect.y + rect.h - insetY) * scale,
            paint,
        )
    }

    private fun templateForProcessor(): TemplateState =
        TemplateState(
            name = "stage 10C processor",
            questions = (1..16).map { number ->
                val answer = when (number) {
                    1 -> "A"
                    2 -> "B"
                    6 -> "C"
                    11 -> "D"
                    16 -> "A"
                    else -> "A"
                }
                val score = if (number in listOf(1, 2, 6, 11, 16)) 2 else 0
                QuestionSetting(number = number, answer = answer, score = score)
            },
        )

    private fun Bitmap.toMiniProgramFrame(): MiniProgramFrame {
        val pixels = IntArray(width * height)
        for (row in 0 until height) {
            for (column in 0 until width) {
                val color = getPixel(column, row)
                pixels[row * width + column] = (
                    Color.red(color) * 299 +
                        Color.green(color) * 587 +
                        Color.blue(color) * 114
                    ) / 1000
            }
        }
        return MiniProgramFrame(width = width, height = height, pixels = pixels)
    }
}
