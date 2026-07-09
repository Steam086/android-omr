package com.answercard.grader.miniprogram

import com.answercard.grader.template.CornerMarkerStyle
import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolidMarkerCardScanTest {
    private fun template(): TemplateState =
        TemplateState(
            name = "solid marker scan",
            questions = (1..16).map { number ->
                val answer = when (number) {
                    1 -> "A"
                    2 -> "B"
                    6 -> "C"
                    11 -> "D"
                    16 -> "A"
                    else -> "A"
                }
                val score = if (number in listOf(1, 2, 6, 11, 16)) 2 else 0
                QuestionSetting(number = number, answer = answer, score = score)
            },
        )

    private fun renderedFrame(): MiniProgramFrame {
        val renderer = DesktopTemplateCardRenderer(template(), scale = 3f, markerStyle = CornerMarkerStyle.SOLID_SQUARE)
        renderer.markAnswer(1, "A")
        renderer.markAnswer(2, "B")
        renderer.markAnswer(6, "C")
        renderer.markAnswer(11, "D")
        renderer.markAnswer(16, "A")
        renderer.markAdmissionNumber("1234")
        return renderer.frame()
    }

    @Test
    fun flatSquareCardScansViaSolidMarkerPath() {
        val result = AndroidOmrEngine.scan(renderedFrame(), template())

        assertTrue(result.debugInfo.joinToString(), result.success)
        assertTrue(result.debugInfo.contains("anchorPath=solid-marker"))
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun warpedSquareCardScansCorrectly() {
        val warped = TestPerspectiveWarp.warp(
            frame = renderedFrame(),
            // pull corners inward asymmetrically (angled shot); inward keeps markers off the frame border
            luShift = 10 to 6,
            ruShift = -20 to 4,
            ldShift = 6 to -4,
            rdShift = -14 to -10,
        )
        val result = AndroidOmrEngine.scan(warped, template())

        assertTrue(result.debugInfo.joinToString(), result.success)
        assertTrue(result.debugInfo.contains("anchorPath=solid-marker"))
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun warpedHeaderlessSquareCardScansCorrectly() {
        val template = TemplateState(
            name = "headerless warp",
            questions = (1..15).map { number ->
                QuestionSetting(number = number, answer = "A", score = if (number == 1) 2 else 0)
            },
        ).withShowHeader(false)
        val renderer = DesktopTemplateCardRenderer(template, scale = 3f)
        renderer.markAnswer(1, "A")
        val warped = TestPerspectiveWarp.warp(
            frame = renderer.frame(),
            luShift = 10 to 6,
            ruShift = -20 to 4,
            ldShift = 6 to -4,
            rdShift = -14 to -10,
        )
        val result = AndroidOmrEngine.scan(warped, template)

        assertTrue(result.debugInfo.joinToString(), result.success)
        assertTrue(result.debugInfo.contains("anchorPath=coded-marker"))
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 0 }?.selectedLabels)
        assertEquals(2.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun lBracketCardStillScansViaFallback() {
        val renderer = DesktopTemplateCardRenderer(template(), scale = 3f, markerStyle = CornerMarkerStyle.L_BRACKET)
        renderer.markAnswer(1, "A")
        renderer.markAdmissionNumber("1234")
        val result = AndroidOmrEngine.scan(renderer.frame(), template())

        assertTrue(result.debugInfo.joinToString(), result.success)
        assertTrue(result.debugInfo.contains("anchorPath=l-bracket"))
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(2.0, result.score?.totalScore ?: -1.0, 0.0)
    }
}

/** Warps a frame so that its four corners move by the given (dx, dy) pixel shifts. */
object TestPerspectiveWarp {
    fun warp(
        frame: MiniProgramFrame,
        luShift: Pair<Int, Int>,
        ruShift: Pair<Int, Int>,
        ldShift: Pair<Int, Int>,
        rdShift: Pair<Int, Int>,
    ): MiniProgramFrame {
        val w = frame.width - 1.0
        val h = frame.height - 1.0
        val sourceCorners = listOf(
            PerspectivePoint(0.0, 0.0),
            PerspectivePoint(w, 0.0),
            PerspectivePoint(w, h),
            PerspectivePoint(0.0, h),
        )
        val targetCorners = listOf(
            PerspectivePoint(0.0 + luShift.first, 0.0 + luShift.second),
            PerspectivePoint(w + ruShift.first, 0.0 + ruShift.second),
            PerspectivePoint(w + rdShift.first, h + rdShift.second),
            PerspectivePoint(0.0 + ldShift.first, h + ldShift.second),
        )
        // For each destination pixel, look up the source pixel (inverse warp).
        val mapping = PerspectiveMapping.fromCorrespondences(targetCorners, sourceCorners)!!
        val pixels = IntArray(frame.width * frame.height)
        for (row in 0 until frame.height) {
            for (column in 0 until frame.width) {
                val source = mapping.map(PerspectivePoint(column.toDouble(), row.toDouble()))
                val sr = Math.round(source.y).toInt()
                val sc = Math.round(source.x).toInt()
                pixels[row * frame.width + column] =
                    if (sr in 0 until frame.height && sc in 0 until frame.width) frame.pixels[sr * frame.width + sc] else 255
            }
        }
        return MiniProgramFrame(width = frame.width, height = frame.height, pixels = pixels)
    }
}
