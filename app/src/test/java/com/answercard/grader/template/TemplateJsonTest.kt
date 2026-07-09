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

    @Test
    fun roundTripsMultipleChoiceAnswer() {
        val template = TemplateState(
            name = "多选试卷",
            questions = listOf(
                QuestionSetting(
                    number = 1,
                    answer = "CA",
                    score = 4,
                    type = QuestionType.MULTIPLE,
                ),
            ),
        )

        val restored = TemplateJson.fromJson(TemplateJson.toJson(template))
        val question = restored.questions.single()

        assertEquals(QuestionType.MULTIPLE, question.type)
        assertEquals("AC", question.answer)
    }

    @Test
    fun roundTripPreservesShowHeader() {
        val template = TemplateState.default().withShowHeader(false)

        val restored = TemplateJson.fromJson(TemplateJson.toJson(template))

        assertEquals(template, restored)
        assertEquals(false, restored.showHeader)
    }

    @Test
    fun legacyJsonWithoutShowHeaderDefaultsToHeaderShown() {
        val legacyRoot = org.json.JSONObject(TemplateJson.toJson(TemplateState.default()))
        legacyRoot.remove("showHeader")

        val restored = TemplateJson.fromJson(legacyRoot.toString())

        assertEquals(true, restored.showHeader)
    }
}
