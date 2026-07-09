package com.answercard.grader.ui

import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanTemplateOverlayTest {
    @Test
    fun buildsTemplateRectsForAnswerAndAdmissionMarks() {
        val template = TemplateState.default()
        val display = ScanDisplayResult(
            isRecognized = false,
            examId = "5",
            scoreText = "0/2",
            failureReason = "score warnings: Q1 incorrect",
            friendlyMessage = null,
            debugInfo = emptyList(),
            answerMarks = listOf(
                ScanAnswerMark(
                    questionIndex = 0,
                    optionIndex = 0,
                    optionLabel = "A",
                    state = ScanAnswerMarkState.MARKED_WRONG,
                ),
                ScanAnswerMark(
                    questionIndex = 0,
                    optionIndex = 1,
                    optionLabel = "B",
                    state = ScanAnswerMarkState.MISSED_CORRECT,
                ),
            ),
            admissionMarks = listOf(
                ScanAdmissionMark(
                    digitIndex = 0,
                    numberValue = 5,
                    state = ScanAdmissionMarkState.SELECTED,
                ),
            ),
        )

        val overlay = ScanTemplateOverlay.from(template, display)
        val layout = TemplateGeometry.buildLayout(template)
        val q1A = TemplateGeometry.renderedRect(layout.options.single { it.question == 1 && it.option == "A" }.rect)
        val q1B = TemplateGeometry.renderedRect(layout.options.single { it.question == 1 && it.option == "B" }.rect)
        val digit5 = TemplateGeometry.renderedRect(TemplateGeometry.examIdDigitBox(layout, column = 0, digit = 5))

        assertEquals(TemplateGeometry.renderedWidth(layout), overlay.renderedWidth)
        assertEquals(TemplateGeometry.renderedHeight(layout), overlay.renderedHeight)
        assertEquals(q1A, overlay.answerRects[0].rect)
        assertEquals(ScanAnswerMarkState.MARKED_WRONG, overlay.answerRects[0].state)
        assertEquals(q1B, overlay.answerRects[1].rect)
        assertEquals(ScanAnswerMarkState.MISSED_CORRECT, overlay.answerRects[1].state)
        assertEquals(digit5, overlay.admissionRects.single().rect)
        assertEquals(ScanAdmissionMarkState.SELECTED, overlay.admissionRects.single().state)
    }

    @Test
    fun buildsEmptyOverlayForNullResult() {
        val template = TemplateState.default()

        val overlay = ScanTemplateOverlay.from(template, result = null)

        assertEquals(0, overlay.answerRects.size)
        assertEquals(0, overlay.admissionRects.size)
    }

    @Test
    fun suppressesAdmissionMarksForHeaderlessTemplates() {
        val template = TemplateState.default().withShowHeader(false)
        val display = ScanDisplayResult(
            isRecognized = true,
            examId = "5",
            scoreText = "2/2",
            failureReason = null,
            friendlyMessage = null,
            debugInfo = emptyList(),
            admissionMarks = listOf(
                ScanAdmissionMark(
                    digitIndex = 0,
                    numberValue = 5,
                    state = ScanAdmissionMarkState.SELECTED,
                ),
            ),
        )

        val overlay = ScanTemplateOverlay.from(template, display)

        assertEquals(0, overlay.admissionRects.size)
    }
}
