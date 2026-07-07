package com.answercard.grader.template

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateGeometryTest {
    private fun assertNear(expected: Float, actual: Float) {
        assertEquals(expected, actual, 0.001f)
    }

    @Test
    fun defaultLayoutMatchesDesktopCardSizeAndOptionCount() {
        val layout = TemplateGeometry.buildLayout()

        assertNear(540f, layout.width)
        assertNear(216f, layout.height)
        assertEquals(60, layout.options.size)
        assertEquals(15, layout.questionGuides.size)
    }

    @Test
    fun questionOneOptionCoordinatesMatchDesktopPngCoordinates() {
        val layout = TemplateGeometry.buildLayout(15)
        val optionA = layout.options.single { it.question == 1 && it.option == "A" }
        val number = layout.questionGuides.single { it.question == 1 }.numberRect

        assertNear(51f, optionA.rect.x)
        assertNear(124f, optionA.rect.y)
        assertNear(18f, optionA.rect.w)
        assertNear(12.5f, optionA.rect.h)
        assertNear(26f, number.x)
        assertNear(123f, number.y)
    }

    @Test
    fun questionFifteenAndSixteenFollowDesktopBandFormula() {
        val fifteen = TemplateGeometry.buildLayout(15)
            .options.single { it.question == 15 && it.option == "D" }
        assertNear(448.83334f, fifteen.rect.x)
        assertNear(198f, fifteen.rect.y)

        val sixteen = TemplateGeometry.buildLayout(16)
            .options.single { it.question == 16 && it.option == "A" }
        assertNear(51f, sixteen.rect.x)
        assertNear(222f, sixteen.rect.y)
    }

    @Test
    fun examIdDigitBoxesMatchDesktopSpacing() {
        val layout = TemplateGeometry.buildLayout(15)
        val row0Digit0 = TemplateGeometry.examIdDigitBox(layout, column = 0, digit = 0)
        val row3Digit9 = TemplateGeometry.examIdDigitBox(layout, column = 3, digit = 9)

        assertNear(252f, row0Digit0.x)
        assertNear(17.5f, row0Digit0.y)
        assertNear(14f, row0Digit0.w)
        assertNear(10f, row0Digit0.h)
        assertNear(450f, row3Digit9.x)
        assertNear(71.5f, row3Digit9.y)
    }

    @Test
    fun questionCountIsClampedLikeDesktop() {
        assertEquals(4, TemplateGeometry.buildLayout(0).options.size)
        assertEquals(240, TemplateGeometry.buildLayout(99).options.size)
    }

    @Test
    fun cornerBracketConstantsMatchDesktop() {
        assertNear(34f, TemplateGeometry.CORNER_BRACKET_SIZE)
        assertNear(8f, TemplateGeometry.CORNER_BRACKET_THICKNESS)
    }

    @Test
    fun templateLayoutUsesEachQuestionsOptionLabels() {
        val template = TemplateState.default()
            .withQuestionOptions(question = 1, optionCount = 2)
            .withQuestionOptions(question = 2, optionCount = 3)

        val layout = TemplateGeometry.buildLayout(template)

        assertEquals(listOf("T", "F"), layout.options.filter { it.question == 1 }.map { it.option })
        assertEquals(listOf("A", "B", "C"), layout.options.filter { it.question == 2 }.map { it.option })
        assertEquals(listOf("A", "B", "C", "D"), layout.options.filter { it.question == 3 }.map { it.option })
        assertEquals(57, layout.options.size)
    }

    @Test
    fun defaultTemplateLayoutMatchesQuestionCountLayout() {
        val byCount = TemplateGeometry.buildLayout(15)
        val byTemplate = TemplateGeometry.buildLayout(TemplateState.default())

        assertEquals(byCount, byTemplate)
    }
}
