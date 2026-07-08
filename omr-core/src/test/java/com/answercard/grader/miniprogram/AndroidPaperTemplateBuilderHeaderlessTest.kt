package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPaperTemplateBuilderHeaderlessTest {
    @Test
    fun zeroAdmissionDigitsBuildsAnswerOnlyGrid() {
        val layout = AndroidPaperTemplateBuilder.build(
            questionOptionCounts = List(16) { 4 },
            admissionNumberDigits = 0,
        )

        assertEquals(10, layout.gridRows)
        assertEquals(0, layout.admissionNumberDigits)
        assertTrue(layout.admissionNumberMappings.isEmpty())
        assertEquals(0, layout.answerArea.startRow)
        assertEquals(10, layout.answerArea.endRow)
        assertEquals(0, layout.admissionNumberArea.endRow)
        val q1A = layout.questionMappings.single { it.questionIndex == 0 && it.optionIndex == 0 }
        assertEquals(0, q1A.row)
        assertEquals(0, q1A.column)
        val q16A = layout.questionMappings.single { it.questionIndex == 15 && it.optionIndex == 0 }
        assertEquals(5, q16A.row)
    }

    @Test
    fun fourAdmissionDigitsKeepLegacyAnswerStartRow() {
        val layout = AndroidPaperTemplateBuilder.build(
            questionOptionCounts = List(16) { 4 },
            admissionNumberDigits = 4,
        )

        assertEquals(15, layout.gridRows)
        assertEquals(5, layout.answerArea.startRow)
        assertEquals(40, layout.admissionNumberMappings.size)
    }
}
