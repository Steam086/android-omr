package com.answercard.grader.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TemplateEditOperationsTest {
    @Test
    fun editQuestionUpdatesNumberScoreAndOptions() {
        val template = TemplateState.default()
            .editQuestion(
                originalNumber = 3,
                request = EditQuestionRequest(
                    number = 30,
                    score = 6,
                    optionCount = 2,
                ),
            )

        val edited = template.questions.single { it.number == 30 }
        assertEquals(6, edited.score)
        assertEquals(listOf("T", "F"), edited.options)
        assertEquals("", edited.answer)
        assertFalse(template.questions.any { it.number == 3 })
    }

    @Test
    fun deleteQuestionRemovesOnlyTheTargetQuestion() {
        val template = TemplateState.default().deleteQuestion(5)

        assertEquals(14, template.questions.size)
        assertFalse(template.questions.any { it.number == 5 })
        assertEquals("A", template.questions.single { it.number == 6 }.answer)
    }

    @Test
    fun editQuestionPreservesMultipleChoiceAndNormalizesAnswer() {
        val template = TemplateState.default()
            .withAnswer(3, "A")
            .editQuestion(
                originalNumber = 3,
                request = EditQuestionRequest(
                    number = 30,
                    score = 2,
                    optionCount = 4,
                    type = QuestionType.MULTIPLE,
                ),
            )
            .toggleAnswer(30, "C")

        val edited = template.questions.single { it.number == 30 }
        assertEquals(QuestionType.MULTIPLE, edited.type)
        assertEquals("AC", edited.answer)
    }

    @Test
    fun toggleQuestionSelectionMarksQuestionsForBatchEdit() {
        val template = TemplateState.default()
            .toggleQuestionSelection(2)
            .toggleQuestionSelection(4)

        assertEquals(listOf(2, 4), template.questions.filter { it.selected }.map { it.number })
    }

    @Test
    fun batchEditSelectedQuestionsUpdatesScoreAndOptionsThenClearsSelection() {
        val template = TemplateState.default()
            .toggleQuestionSelection(2)
            .toggleQuestionSelection(4)
            .batchEditSelectedQuestions(score = 3, optionCount = 3)

        val edited = template.questions.filter { it.number in setOf(2, 4) }
        assertEquals(listOf(3, 3), edited.map { it.score })
        assertEquals(listOf("A", "B", "C"), edited.first().options)
        assertFalse(edited.any { it.selected })
    }

    @Test
    fun batchEditSelectedQuestionsCanSetMultipleChoice() {
        val template = TemplateState.default()
            .toggleQuestionSelection(2)
            .toggleQuestionSelection(4)
            .batchEditSelectedQuestions(score = 3, optionCount = 4, type = QuestionType.MULTIPLE)

        val edited = template.questions.filter { it.number in setOf(2, 4) }
        assertEquals(listOf(QuestionType.MULTIPLE, QuestionType.MULTIPLE), edited.map { it.type })
        assertFalse(edited.any { it.selected })
    }

    @Test
    fun examIdDigitsAreClampedToSupportedRange() {
        assertEquals(1, TemplateState.default().withExamIdDigits(0).examIdDigits)
        assertEquals(12, TemplateState.default().withExamIdDigits(20).examIdDigits)
    }
}
