package com.answercard.grader.grading

import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertEquals
import org.junit.Test

class GraderTest {
    @Test
    fun defaultTemplateHasFifteenQuestionsAndThirtyPoints() {
        val template = TemplateState.default()

        assertEquals(15, template.questions.size)
        assertEquals(30, template.totalScore)
        assertEquals("A", template.questions.first().answer)
        assertEquals(2, template.questions.first().score)
    }

    @Test
    fun scoresOnlyCorrectRecognizedAnswers() {
        val template = TemplateState.default()
            .withAnswer(question = 2, answer = "B")
            .withScore(question = 2, score = 3)

        val result = Grader.grade(
            template = template,
            recognizedAnswers = mapOf(1 to "A", 2 to "A", 3 to "A"),
        )

        assertEquals(4, result.totalScore)
        assertEquals(31, result.maxScore)
        assertEquals(false, result.items.single { it.question == 2 }.isCorrect)
    }

    @Test
    fun updatesOneQuestionWithoutChangingNeighbors() {
        val template = TemplateState.default()
            .withAnswer(question = 5, answer = "D")
            .withScore(question = 5, score = 4)

        assertEquals("A", template.questions.single { it.number == 4 }.answer)
        assertEquals("D", template.questions.single { it.number == 5 }.answer)
        assertEquals("A", template.questions.single { it.number == 6 }.answer)
        assertEquals(4, template.questions.single { it.number == 5 }.score)
    }
}
