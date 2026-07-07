package com.answercard.grader.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.answercard.grader.camera.BitmapTransforms
import com.answercard.grader.template.CardLayout
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OmrScannerTest {
    @Test
    fun scansDetectedCardAnswersExamIdAndScore() {
        val layout = TemplateGeometry.buildLayout()
        val bitmap = blankBitmap(layout, scale = 3f)
        drawCorners(bitmap, layout, scale = 3f)
        for (question in (1..15).filter { it != 2 }) {
            fillOption(bitmap, layout, question = question, option = "A", scale = 3f)
        }
        fillOption(bitmap, layout, question = 2, option = "D", scale = 3f)
        fillDigit(bitmap, layout, column = 0, digit = 1, scale = 3f)
        fillDigit(bitmap, layout, column = 1, digit = 2, scale = 3f)
        fillDigit(bitmap, layout, column = 2, digit = 3, scale = 3f)
        fillDigit(bitmap, layout, column = 3, digit = 4, scale = 3f)
        val template = TemplateState.default().withAnswer(question = 2, answer = "D")

        val result = OmrScanner.scan(bitmap, template, layout, scale = 3f)

        requireNotNull(result)
        assertEquals("1234", result.examId)
        assertEquals("A", result.answers[1])
        assertEquals("D", result.answers[2])
        assertEquals(30, result.grade.totalScore)
    }

    @Test
    fun scansMixedOptionCountTemplate() {
        val template = TemplateState.default()
            .withQuestionOptions(question = 1, optionCount = 2)
            .withAnswer(question = 1, answer = "F")
            .withQuestionOptions(question = 2, optionCount = 3)
            .withAnswer(question = 2, answer = "C")
        val layout = TemplateGeometry.buildLayout(template)
        val bitmap = blankBitmap(layout, scale = 3f)
        drawCorners(bitmap, layout, scale = 3f)
        fillOption(bitmap, layout, question = 1, option = "F", scale = 3f)
        fillOption(bitmap, layout, question = 2, option = "C", scale = 3f)
        for (question in 3..15) {
            fillOption(bitmap, layout, question = question, option = "A", scale = 3f)
        }
        fillDigit(bitmap, layout, column = 0, digit = 1, scale = 3f)
        fillDigit(bitmap, layout, column = 1, digit = 2, scale = 3f)
        fillDigit(bitmap, layout, column = 2, digit = 3, scale = 3f)
        fillDigit(bitmap, layout, column = 3, digit = 4, scale = 3f)

        val result = OmrScanner.scan(bitmap, template, layout, scale = 3f)

        requireNotNull(result)
        assertEquals("F", result.answers[1])
        assertEquals("C", result.answers[2])
        assertEquals(30, result.grade.totalScore)
    }


    @Test
    fun returnsNullWhenCornersAreMissing() {
        val layout = TemplateGeometry.buildLayout()
        val bitmap = blankBitmap(layout, scale = 3f)

        assertNull(OmrScanner.scan(bitmap, TemplateState.default(), layout, scale = 3f))
    }

    @Test
    fun rejectsPlainOuterBorderWithoutCornerBrackets() {
        val layout = TemplateGeometry.buildLayout()
        val bitmap = blankBitmap(layout, scale = 3f)
        drawOuterBorder(bitmap, scale = 3f)
        for (question in 1..15) {
            drawOptionBorder(bitmap, layout, question = question, option = "A", scale = 3f)
        }
        fillOption(bitmap, layout, question = 1, option = "A", scale = 3f)

        assertNull(OmrScanner.scan(bitmap, TemplateState.default(), layout, scale = 3f))
    }

    @Test
    fun rejectsCardWhenOneCornerBracketIsMissing() {
        val layout = TemplateGeometry.buildLayout()
        val bitmap = blankBitmap(layout, scale = 3f)
        drawCorners(bitmap, layout, scale = 3f, missing = "topRight")
        for (question in 1..15) {
            fillOption(bitmap, layout, question = question, option = "A", scale = 3f)
        }

        assertNull(OmrScanner.scan(bitmap, TemplateState.default(), layout, scale = 3f))
    }

    @Test
    fun scansCardWhenItIsCenteredInsideCameraFrame() {
        val layout = TemplateGeometry.buildLayout()
        val card = blankBitmap(layout, scale = 3f)
        drawCorners(card, layout, scale = 3f)
        drawOuterBorder(card, scale = 3f)
        for (question in 1..15) {
            fillOption(card, layout, question = question, option = "A", scale = 3f)
        }
        fillDigit(card, layout, column = 0, digit = 5, scale = 3f)
        fillDigit(card, layout, column = 1, digit = 6, scale = 3f)
        fillDigit(card, layout, column = 2, digit = 7, scale = 3f)
        fillDigit(card, layout, column = 3, digit = 8, scale = 3f)
        val frame = Bitmap.createBitmap(card.width + 900, card.height + 720, Bitmap.Config.ARGB_8888)
        frame.eraseColor(Color.WHITE)
        val offsetX = 450
        val offsetY = 360
        for (y in 0 until card.height) {
            for (x in 0 until card.width) {
                frame.setPixel(x + offsetX, y + offsetY, card.getPixel(x, y))
            }
        }

        val result = OmrScanner.scan(frame, TemplateState.default(), layout, scale = 3f)

        assertNotNull(result)
        assertEquals("5678", result?.examId)
        assertEquals(30, result?.grade?.totalScore)
    }

    @Test
    fun scansCardWhenItIsRotatedSidewaysInsideCameraFrame() {
        val layout = TemplateGeometry.buildLayout()
        val card = blankBitmap(layout, scale = 3f)
        drawCorners(card, layout, scale = 3f)
        drawOuterBorder(card, scale = 3f)
        for (question in 1..15) {
            fillOption(card, layout, question = question, option = "A", scale = 3f)
        }
        fillDigit(card, layout, column = 0, digit = 2, scale = 3f)
        fillDigit(card, layout, column = 1, digit = 4, scale = 3f)
        fillDigit(card, layout, column = 2, digit = 6, scale = 3f)
        fillDigit(card, layout, column = 3, digit = 8, scale = 3f)

        val rotatedCard = BitmapTransforms.rotate(card, 90)
        val frame = Bitmap.createBitmap(rotatedCard.width + 700, rotatedCard.height + 520, Bitmap.Config.ARGB_8888)
        frame.eraseColor(Color.WHITE)
        val offsetX = 350
        val offsetY = 260
        for (y in 0 until rotatedCard.height) {
            for (x in 0 until rotatedCard.width) {
                frame.setPixel(x + offsetX, y + offsetY, rotatedCard.getPixel(x, y))
            }
        }

        val result = OmrScanner.scan(frame, TemplateState.default(), layout, scale = 3f)

        assertNotNull(result)
        assertEquals("2468", result?.examId)
        assertEquals(30, result?.grade?.totalScore)
    }

    @Test
    fun scansCardWhenCameraFrameContainsDarkMonitorChrome() {
        val layout = TemplateGeometry.buildLayout()
        val card = blankBitmap(layout, scale = 3f)
        drawCorners(card, layout, scale = 3f)
        drawOuterBorder(card, scale = 3f)
        for (question in 1..15) {
            fillOption(card, layout, question = question, option = "A", scale = 3f)
        }
        fillDigit(card, layout, column = 0, digit = 1, scale = 3f)
        fillDigit(card, layout, column = 1, digit = 1, scale = 3f)
        fillDigit(card, layout, column = 2, digit = 1, scale = 3f)
        fillDigit(card, layout, column = 3, digit = 1, scale = 3f)

        val frame = Bitmap.createBitmap(card.width + 600, card.height + 780, Bitmap.Config.ARGB_8888)
        frame.eraseColor(Color.WHITE)
        fillRect(frame, 0, 0, frame.width, 180)
        fillRect(frame, 0, frame.height - 180, frame.width, frame.height)
        fillRect(frame, 0, 300, frame.width, 360)
        val offsetX = 300
        val offsetY = 400
        for (y in 0 until card.height) {
            for (x in 0 until card.width) {
                frame.setPixel(x + offsetX, y + offsetY, card.getPixel(x, y))
            }
        }

        val result = OmrScanner.scan(frame, TemplateState.default(), layout, scale = 3f)

        assertNotNull(result)
        assertEquals("1111", result?.examId)
        assertEquals(30, result?.grade?.totalScore)
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

    private fun drawCorners(bitmap: Bitmap, layout: CardLayout, scale: Float, missing: String? = null) {
        val size = (TemplateGeometry.CORNER_BRACKET_SIZE * scale).roundToInt()
        val thick = (TemplateGeometry.CORNER_BRACKET_THICKNESS * scale).roundToInt()
        val width = bitmap.width
        val height = bitmap.height
        if (missing != "topLeft") {
            fillRect(bitmap, 0, 0, size, thick)
            fillRect(bitmap, 0, 0, thick, size)
        }
        if (missing != "topRight") {
            fillRect(bitmap, width - size, 0, width, thick)
            fillRect(bitmap, width - thick, 0, width, size)
        }
        if (missing != "bottomLeft") {
            fillRect(bitmap, 0, height - thick, size, height)
            fillRect(bitmap, 0, height - size, thick, height)
        }
        if (missing != "bottomRight") {
            fillRect(bitmap, width - size, height - thick, width, height)
            fillRect(bitmap, width - thick, height - size, width, height)
        }
    }

    private fun drawOuterBorder(bitmap: Bitmap, scale: Float) {
        val thick = scale.roundToInt().coerceAtLeast(1)
        fillRect(bitmap, 0, 0, bitmap.width, thick)
        fillRect(bitmap, 0, bitmap.height - thick, bitmap.width, bitmap.height)
        fillRect(bitmap, 0, 0, thick, bitmap.height)
        fillRect(bitmap, bitmap.width - thick, 0, bitmap.width, bitmap.height)
    }

    private fun fillOption(bitmap: Bitmap, layout: CardLayout, question: Int, option: String, scale: Float) {
        val box = layout.options.first { it.question == question && it.option == option }.rect
        fillRect(
            bitmap,
            ((box.x + 2f) * scale).toInt(),
            ((box.y + 2f) * scale).toInt(),
            ((box.x + box.w - 2f) * scale).toInt(),
            ((box.y + box.h - 2f) * scale).toInt(),
        )
    }

    private fun drawOptionBorder(bitmap: Bitmap, layout: CardLayout, question: Int, option: String, scale: Float) {
        val box = layout.options.first { it.question == question && it.option == option }.rect
        val left = (box.x * scale).toInt()
        val top = (box.y * scale).toInt()
        val right = ((box.x + box.w) * scale).toInt()
        val bottom = ((box.y + box.h) * scale).toInt()
        val thick = 1
        fillRect(bitmap, left, top, right, top + thick)
        fillRect(bitmap, left, bottom - thick, right, bottom)
        fillRect(bitmap, left, top, left + thick, bottom)
        fillRect(bitmap, right - thick, top, right, bottom)
    }

    private fun fillDigit(bitmap: Bitmap, layout: CardLayout, column: Int, digit: Int, scale: Float) {
        val box = TemplateGeometry.examIdDigitBox(layout, column, digit)
        fillRect(
            bitmap,
            ((box.x + 2f) * scale).toInt(),
            ((box.y + 2f) * scale).toInt(),
            ((box.x + box.w - 2f) * scale).toInt(),
            ((box.y + box.h - 2f) * scale).toInt(),
        )
    }

    private fun fillRect(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int) {
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) {
            for (x in left.coerceAtLeast(0) until right.coerceAtMost(bitmap.width)) {
                bitmap.setPixel(x, y, Color.BLACK)
            }
        }
    }
}
