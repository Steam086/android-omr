package com.answercard.grader.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreSpeechTextTest {
    @Test
    fun includesExamIdWhenPresent() {
        val text = ScoreSpeechText.build(totalScore = 26, maxScore = 30, examId = "1234")

        assertEquals("考号1234，得分26分，满分30分", text)
    }

    @Test
    fun omitsExamIdWhenMissing() {
        val text = ScoreSpeechText.build(totalScore = 18, maxScore = 30, examId = null)

        assertEquals("得分18分，满分30分", text)
    }
}
