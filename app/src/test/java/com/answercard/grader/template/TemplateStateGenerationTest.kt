package com.answercard.grader.template

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateStateGenerationTest {
    @Test
    fun changesQuestionCountWhileKeepingExistingAnswersAndScores() {
        val template = TemplateState.default()
            .withAnswer(question = 2, answer = "D")
            .withScore(question = 2, score = 5)

        val expanded = template.withQuestionCount(16)

        assertEquals(16, expanded.questions.size)
        assertEquals("D", expanded.questions.single { it.number == 2 }.answer)
        assertEquals(5, expanded.questions.single { it.number == 2 }.score)
        assertEquals("A", expanded.questions.single { it.number == 16 }.answer)
        assertEquals(2, expanded.questions.single { it.number == 16 }.score)
    }

    @Test
    fun generatedLayoutUsesTemplateQuestionCount() {
        val template = TemplateState.default().withQuestionCount(16)

        val layout = TemplateGeometry.buildLayout(template.questions.size)

        assertEquals(16, layout.questionGuides.size)
        assertEquals(314f, layout.height, 0.001f)
    }
}
