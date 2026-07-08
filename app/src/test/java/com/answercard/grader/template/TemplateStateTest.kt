package com.answercard.grader.template

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateStateTest {
    @Test
    fun twoOptionQuestionUsesTrueFalseLabels() {
        val template = TemplateState.default()
            .withQuestionOptions(question = 1, optionCount = 2)

        assertEquals(listOf("T", "F"), template.questions.first().options)
        assertEquals(2, template.questions.first().optionCount)
    }

    @Test
    fun fourOptionQuestionUsesLetterLabels() {
        val template = TemplateState.default()
            .withQuestionOptions(question = 1, optionCount = 4)

        assertEquals(listOf("A", "B", "C", "D"), template.questions.first().options)
        assertEquals(4, template.questions.first().optionCount)
    }

    @Test
    fun clickingSelectedAnswerAgainClearsAnswerAndScore() {
        val template = TemplateState.default()
            .withAnswer(question = 1, answer = "C")
            .toggleAnswer(question = 1, answer = "C")

        assertEquals("", template.questions.first().answer)
        assertEquals(0, template.questions.first().score)
    }

    @Test
    fun examIdDigitsCanBeChanged() {
        val template = TemplateState.default().withExamIdDigits(6)

        assertEquals(6, template.examIdDigits)
    }

    @Test
    fun headerIsShownByDefaultAndCanBeToggled() {
        val template = TemplateState.default()

        assertEquals(true, template.showHeader)
        assertEquals(false, template.withShowHeader(false).showHeader)
        assertEquals(true, template.withShowHeader(false).withShowHeader(true).showHeader)
    }
}
