package com.answercard.grader.miniprogram

import com.answercard.grader.template.CornerMarkerStyle
import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurvedCardScanTest {
    @Test
    fun codedCardWithHorizontalPaperBendScansExactly() {
        val result = AndroidOmrEngine.scan(
            frame = TestNonPlanarWarp.horizontalBend(renderedCard(CornerMarkerStyle.CODED), amplitude = 12.0),
            template = template(),
        )

        assertCorrectScan(result)
        assertTrue(result.debugInfo.joinToString(), result.debugInfo.contains("anchorPath=coded-marker"))
        assertRefinementActive(result)
    }

    @Test
    fun legacyBracketCardWithHorizontalPaperBendScansExactly() {
        val result = AndroidOmrEngine.scan(
            frame = TestNonPlanarWarp.horizontalBend(renderedCard(CornerMarkerStyle.L_BRACKET), amplitude = 12.0),
            template = template(),
            anchorMode = AnchorMode.LEGACY,
        )

        assertCorrectScan(result)
        assertTrue(result.debugInfo.joinToString(), result.debugInfo.contains("anchorPath=l-bracket"))
        assertRefinementActive(result)
    }

    @Test
    fun periodicEdgeAliasBeyondCorrectionBudgetNeverOutputsScore() {
        val result = AndroidOmrEngine.scan(
            frame = TestNonPlanarWarp.horizontalBend(renderedCard(CornerMarkerStyle.CODED), amplitude = 64.0),
            template = template(),
        )

        assertEquals(result.debugInfo.joinToString(), false, result.success)
        assertNull(result.score)
    }

    private fun renderedCard(markerStyle: CornerMarkerStyle): MiniProgramFrame {
        val renderer = DesktopTemplateCardRenderer(template(), scale = 3f, markerStyle = markerStyle)
        renderer.markAnswer(1, "A")
        renderer.markAnswer(2, "B")
        renderer.markAnswer(6, "C")
        renderer.markAnswer(11, "D")
        renderer.markAnswer(16, "A")
        renderer.markAdmissionNumber("1234")
        return renderer.frame()
    }

    private fun assertCorrectScan(result: AndroidOmrResult) {
        assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 0 }?.selectedLabels)
        assertEquals(listOf("B"), result.answerArea?.questions?.single { it.questionIndex == 1 }?.selectedLabels)
        assertEquals(listOf("C"), result.answerArea?.questions?.single { it.questionIndex == 5 }?.selectedLabels)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 15 }?.selectedLabels)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    private fun assertRefinementActive(result: AndroidOmrResult) {
        assertTrue(result.debugInfo.joinToString(), result.debugInfo.contains("edgeRefinement=active"))
        assertTrue(
            result.debugInfo.joinToString(),
            result.debugInfo.any { it.startsWith("edgeRefinementQuestionGroups=") && !it.endsWith("=0") },
        )
    }

    private fun template(): TemplateState = TemplateState(
        name = "curved card",
        questions = (1..16).map { number ->
            val answer = when (number) {
                1 -> "A"
                2 -> "B"
                6 -> "C"
                11 -> "D"
                16 -> "A"
                else -> "A"
            }
            QuestionSetting(
                number = number,
                answer = answer,
                score = if (number in listOf(1, 2, 6, 11, 16)) 2 else 0,
            )
        },
    )
}
