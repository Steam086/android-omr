package com.answercard.grader.template

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateJsonTest {
    @Test
    fun roundTripPreservesAnswersAndScores() {
        val template = TemplateState.default()
            .withAnswer(question = 2, answer = "B")
            .withScore(question = 2, score = 5)
            .withAnswer(question = 15, answer = "D")

        val restored = TemplateJson.fromJson(TemplateJson.toJson(template))

        assertEquals(template, restored)
    }

    @Test
    fun invalidJsonFallsBackToDefaultTemplate() {
        val restored = TemplateJson.fromJson("{bad")

        assertEquals(TemplateState.default(), restored)
    }

    @Test
    fun questionSelectionIsNotPersisted() {
        val template = TemplateState.default().toggleQuestionSelection(2)

        val restored = TemplateJson.fromJson(TemplateJson.toJson(template))

        assertEquals(false, restored.questions.single { it.number == 2 }.selected)
    }
}
