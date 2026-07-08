package com.answercard.grader.miniprogram

import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplatePoint
import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the mark-to-cell matching decision in [AndroidSolidMarkDetector].
 *
 * Rendered coordinates for question 11 (questionIndex 10) in this 16-question template:
 * option C rect x=467.33..485.33, option D rect x=488.83..506.83, y=164.0..176.5.
 * Borderline points model inversion error on cleanly-boxed marks, so the matching
 * functions are exercised directly instead of going through the full scan pipeline.
 */
class AndroidSolidMarkDetectorMatchingTest {
    private val template: TemplateState =
        TemplateState(
            name = "matching",
            questions = (1..16).map { number ->
                QuestionSetting(number = number, answer = "A", score = 0)
            },
        )
    private val cardLayout = TemplateGeometry.buildLayout(template)
    private val questionIndexByNumber = template.questions
        .mapIndexed { index, question -> question.number to index }
        .toMap()

    @Test
    fun gapPointNearerToDMatchesD() {
        // In the C-D gap, 3.07 units right of C's edge but only 0.43 left of D's edge.
        val match = AndroidSolidMarkDetector.matchQuestionCell(
            cardLayout = cardLayout,
            questionIndexByNumber = questionIndexByNumber,
            sourcePoint = TemplatePoint(488.4f, 170f),
        )

        assertEquals(AndroidPaperQuestionCellKey(10, 3), match?.key)
    }

    @Test
    fun pointBeyondHalfGapToleranceMatchesNothing() {
        // 2.17 units right of D's edge: beyond the half-gap tolerance of 1.75.
        val match = AndroidSolidMarkDetector.matchQuestionCell(
            cardLayout = cardLayout,
            questionIndexByNumber = questionIndexByNumber,
            sourcePoint = TemplatePoint(509.0f, 170f),
        )

        assertNull(match)
    }

    @Test
    fun pointBeyondVerticalHalfGapMatchesNothing() {
        // Q15 (questionIndex 14, bottom row of the band) D rect spans y=238.0..250.5.
        // 3.7 units below its bottom edge: beyond the vertical half-gap tolerance of 3.0,
        // and inside the band gap so no lower row window claims it either.
        // (Between adjacent rows the half-gap windows tile the space exactly, so a
        // between-rows point always matches the nearest row; only band edges have dead zones.)
        val match = AndroidSolidMarkDetector.matchQuestionCell(
            cardLayout = cardLayout,
            questionIndexByNumber = questionIndexByNumber,
            sourcePoint = TemplatePoint(497.8f, 254.2f),
        )

        assertNull(match)
    }

    @Test
    fun admissionDigitCenterMatchesItsCell() {
        val rect = TemplateGeometry.renderedRect(
            TemplateGeometry.examIdDigitBox(layout = cardLayout, column = 0, digit = 5),
        )
        val match = AndroidSolidMarkDetector.matchAdmissionNumberCell(
            cardLayout = cardLayout,
            admissionNumberDigits = template.examIdDigits,
            sourcePoint = TemplatePoint(rect.x + rect.w / 2f, rect.y + rect.h / 2f),
        )

        assertEquals(AndroidPaperAdmissionNumberCellKey(0, 5), match?.key)
    }
}
