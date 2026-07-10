package com.answercard.grader.miniprogram

import com.answercard.grader.template.CornerMarkerId
import com.answercard.grader.template.CornerMarkerStyle
import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodedMarkerCardScanTest {
    @Test
    fun defaultTemplateScansThroughCodedMarkerPath() {
        val renderer = renderedCard()

        val result = AndroidOmrEngine.scan(renderer.frame(), template())

        assertCorrectScan(result)
        assertTrue(result.debugInfo.contains("anchorPath=coded-marker"))
        assertTrue(result.debugInfo.any { it == "codedMarkerIds=LU,RU,LD,RD" })
    }

    @Test
    fun perspectiveCardWithOneOccludedMarkerStillScans() {
        val renderer = renderedCard()
        renderer.eraseCornerMarker(CornerMarkerId.RD)
        val warped = TestPerspectiveWarp.warp(
            frame = renderer.frame(),
            luShift = 10 to 6,
            ruShift = -20 to 4,
            ldShift = 6 to -4,
            rdShift = -14 to -10,
        )

        val result = AndroidOmrEngine.scan(warped, template())

        assertCorrectScan(result)
        assertTrue(result.debugInfo.contains("anchorPath=coded-marker"))
        assertTrue(result.debugInfo.any { it == "codedMarkerInferred=RD" })
    }

    @Test
    fun twoVisibleCodedMarkersFailWithoutLegacyFallback() {
        val renderer = renderedCard()
        renderer.eraseCornerMarker(CornerMarkerId.LD)
        renderer.eraseCornerMarker(CornerMarkerId.RD)

        val result = AndroidOmrEngine.scan(renderer.frame(), template())

        assertEquals(false, result.success)
        assertEquals(ScanRejectionReason.RETAKE_CODED_MARKERS, result.rejectionReason)
        assertNull(result.score)
        assertTrue(result.debugInfo.contains("anchorPath=coded-marker-rejected"))
        assertTrue(result.debugInfo.any { it == "codedMarkerIds=LU,RU" })
    }

    @Test
    fun codedOnlyModeNeverFallsBackToLegacyMarkers() {
        val renderer = DesktopTemplateCardRenderer(
            template(),
            scale = 3f,
            markerStyle = CornerMarkerStyle.SOLID_SQUARE,
        )
        renderer.markAnswer(1, "A")

        val result = AndroidOmrEngine.scan(renderer.frame(), template(), AnchorMode.CODED_ONLY)

        assertEquals(false, result.success)
        assertEquals(ScanRejectionReason.RETAKE_CODED_MARKERS, result.rejectionReason)
        assertNull(result.score)
        assertTrue(result.debugInfo.none { it == "anchorPath=solid-marker" || it == "anchorPath=l-bracket" })
    }

    private fun renderedCard(): DesktopTemplateCardRenderer =
        DesktopTemplateCardRenderer(template(), scale = 3f).also { renderer ->
            renderer.markAnswer(1, "A")
            renderer.markAnswer(2, "B")
            renderer.markAnswer(6, "C")
            renderer.markAnswer(11, "D")
            renderer.markAnswer(16, "A")
            renderer.markAdmissionNumber("1234")
        }

    private fun assertCorrectScan(result: AndroidOmrResult) {
        assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    private fun template(): TemplateState = TemplateState(
        name = "coded marker scan",
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
