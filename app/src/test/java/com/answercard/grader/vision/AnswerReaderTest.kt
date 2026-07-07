package com.answercard.grader.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.answercard.grader.template.CardLayout
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AnswerReaderTest {
    @Test
    fun readsFilledSingleChoiceAnswersAndLeavesBlankQuestionsEmpty() {
        val layout = TemplateGeometry.buildLayout()
        val bitmap = blankBitmap(layout, scale = 3f)
        fillOption(bitmap, layout, question = 1, option = "A", scale = 3f)
        fillOption(bitmap, layout, question = 2, option = "D", scale = 3f)

        val answers = AnswerReader.readAnswers(bitmap, layout, scale = 3f)

        assertEquals("A", answers[1])
        assertEquals("D", answers[2])
        assertEquals(null, answers[3])
    }

    @Test
    fun leavesAllQuestionsBlankWhenThereAreNoFilledMarks() {
        val layout = TemplateGeometry.buildLayout()
        val bitmap = blankBitmap(layout, scale = 3f)

        val answers = AnswerReader.readAnswers(bitmap, layout, scale = 3f)

        assertEquals(null, answers[1])
        assertEquals(null, answers[2])
    }

    @Test
    fun readsTrueFalseQuestionFromTemplateLayout() {
        val template = TemplateState.default().withQuestionOptions(question = 1, optionCount = 2)
        val layout = TemplateGeometry.buildLayout(template)
        val bitmap = blankBitmap(layout, scale = 3f)
        fillOption(bitmap, layout, question = 1, option = "F", scale = 3f)

        val answers = AnswerReader.readAnswers(bitmap, layout, scale = 3f)

        assertEquals("F", answers[1])
        assertFalse(layout.options.any { it.question == 1 && it.option == "C" })
        assertFalse(layout.options.any { it.question == 1 && it.option == "D" })
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

    private fun fillOption(
        bitmap: android.graphics.Bitmap,
        layout: CardLayout,
        question: Int,
        option: String,
        scale: Float,
    ) {
        val box = layout.options.first { it.question == question && it.option == option }.rect
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
