package com.answercard.grader.template

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateSummaryTest {
    @Test
    fun detailReflectsCurrentTotalScore() {
        val template = TemplateState.default().withScore(question = 1, score = 5)

        assertEquals("15题 · 33分", TemplateSummary.detail(template))
    }
}
