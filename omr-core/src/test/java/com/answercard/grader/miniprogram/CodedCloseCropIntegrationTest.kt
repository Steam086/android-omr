package com.answercard.grader.miniprogram

import com.answercard.grader.template.CornerMarkerStyle
import com.answercard.grader.template.CornerMarkerId
import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodedCloseCropIntegrationTest {
    @Test
    fun scoresWhenOnlyInferredBottomRightMarkerLeavesFrameButRequiredCellsRemainVisible() {
        val template = template()
        val frame = renderedFilledCard(template)

        val result = AndroidOmrEngine.scan(shearBottomRightOutside(frame, 110, template), template)

        assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertTrue(result.debugInfo.contains("codedMarkerInferred=RD"))
        assertTrue(result.debugInfo.contains("anchorBorderInsetRequired=false"))
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun rejectsWhenDeeperCloseCropCutsRequiredCells() {
        val template = template(questionCount = 60)
        val frame = renderedFilledCard(template)

        val result = AndroidOmrEngine.scan(shearBottomRightOutside(frame, 600), template)

        assertFalse(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertEquals(
            result.debugInfo.joinToString(),
            ScanRejectionReason.RETAKE_CARD_CLIPPED,
            result.rejectionReason,
        )
        assertNull(result.score)
    }

    @Test
    fun legacyCardKeepsStrictBorderPolicyForEquivalentCrop() {
        val template = template()
        val renderer = DesktopTemplateCardRenderer(
            template = template,
            scale = 3f,
            markerStyle = CornerMarkerStyle.SOLID_SQUARE,
        )
        renderer.eraseCornerMarker(CornerMarkerId.RD)

        val result = AndroidOmrEngine.scan(
            frame = shearBottomRightOutside(renderer.frame(), 110),
            template = template,
            anchorMode = AnchorMode.LEGACY,
        )

        assertFalse(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertNull(result.score)
    }

    private fun renderedFilledCard(template: TemplateState): MiniProgramFrame {
        val renderer = DesktopTemplateCardRenderer(template, scale = 3f)
        renderer.markAdmissionNumber("1234")
        renderer.markAnswer(1, "A")
        renderer.markAnswer(2, "B")
        renderer.markAnswer(6, "C")
        renderer.markAnswer(11, "D")
        renderer.markAnswer(16, "A")
        return renderer.frame()
    }

    private fun shearBottomRightOutside(
        frame: MiniProgramFrame,
        maximumShift: Int,
        codedTemplate: TemplateState? = null,
    ): MiniProgramFrame {
        val pixels = IntArray(frame.width * frame.height) { 255 }
        for (row in 0 until frame.height) {
            val shift = maximumShift * row / (frame.height - 1).coerceAtLeast(1)
            for (column in shift until frame.width) {
                pixels[row * frame.width + column] = frame[row, column - shift]
            }
        }
        if (codedTemplate != null) {
            val layout = TemplateGeometry.buildLayout(codedTemplate)
            listOf(CornerMarkerId.LU, CornerMarkerId.RU, CornerMarkerId.LD).forEach { id ->
                val rect = TemplateGeometry.cornerMarkerRect(layout, id)
                val left = (rect.x * RENDER_SCALE).roundToInt() - 2
                val top = (rect.y * RENDER_SCALE).roundToInt() - 2
                val right = ((rect.x + rect.w) * RENDER_SCALE).roundToInt() + 2
                val bottom = ((rect.y + rect.h) * RENDER_SCALE).roundToInt() + 2
                val centerRow = (top + bottom) / 2
                val shift = maximumShift * centerRow / (frame.height - 1).coerceAtLeast(1)
                val minimumShift = maximumShift * top.coerceAtLeast(0) / (frame.height - 1).coerceAtLeast(1)
                val maximumMarkerShift = maximumShift * bottom.coerceAtMost(frame.height - 1) /
                    (frame.height - 1).coerceAtLeast(1)
                for (row in top.coerceAtLeast(0) until bottom.coerceAtMost(frame.height)) {
                    for (
                        column in (left + minimumShift - 2).coerceAtLeast(0) until
                            (right + maximumMarkerShift + 2).coerceAtMost(frame.width)
                    ) {
                        pixels[row * frame.width + column] = 255
                    }
                }
                for (row in top.coerceAtLeast(0) until bottom.coerceAtMost(frame.height)) {
                    for (column in left.coerceAtLeast(0) until right.coerceAtMost(frame.width)) {
                        val targetColumn = column + shift
                        if (targetColumn in 0 until frame.width) {
                            pixels[row * frame.width + targetColumn] = frame[row, column]
                        }
                    }
                }
            }
        }
        return MiniProgramFrame(frame.width, frame.height, pixels)
    }

    private fun template(questionCount: Int = 16): TemplateState =
        TemplateState(
            name = "coded close crop",
            questions = (1..questionCount).map { number ->
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
                    score = if (number in setOf(1, 2, 6, 11, 16)) 2 else 0,
                )
            },
        )

    private companion object {
        const val RENDER_SCALE = 3f
    }
}
