package com.answercard.grader.template

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateAddQuestionsTest {
    @Test
    fun appendsQuestionsFromStartNumberWithScoreAndOptions() {
        val template = TemplateState.default().addQuestions(
            AddQuestionRequest(
                startNumber = 16,
                count = 3,
                score = 5,
                optionCount = 3,
            ),
        )

        val added = template.questions.takeLast(3)
        assertEquals(listOf(16, 17, 18), added.map { it.number })
        assertEquals(listOf(5, 5, 5), added.map { it.score })
        assertEquals(listOf("A", "B", "C"), added.first().options)
    }

    @Test
    fun twoOptionAddedQuestionsUseTrueFalseLabels() {
        val template = TemplateState.default().addQuestions(
            AddQuestionRequest(
                startNumber = 21,
                count = 1,
                score = 2,
                optionCount = 2,
            ),
        )

        val added = template.questions.last()
        assertEquals(21, added.number)
        assertEquals(listOf("T", "F"), added.options)
        assertEquals("", added.answer)
    }

    @Test
    fun addQuestionRequestRejectsMultipleChoiceForNow() {
        val template = TemplateState.default().addQuestions(
            AddQuestionRequest(
                startNumber = 16,
                count = 1,
                score = 2,
                optionCount = 4,
                type = QuestionType.MULTIPLE,
            ),
        )

        assertEquals(QuestionType.SINGLE, template.questions.last().type)
    }

    @Test
    fun totalQuestionCountIsCappedAtSixtyForCurrentTemplateGeometry() {
        val template = TemplateState.default().addQuestions(
            AddQuestionRequest(
                startNumber = 16,
                count = 80,
                score = 1,
                optionCount = 4,
            ),
        )

        assertEquals(60, template.questions.size)
    }
}
