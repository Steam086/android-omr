package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameQualityEvaluatorTest {
    private val evaluator = FrameQualityEvaluator(FrameQualityThresholds.PRE_OMR)

    @Test
    fun acceptsCrispHighContrastFrame() {
        val frame = frame(160, 120) { row, column ->
            if ((row / 4 + column / 4) % 2 == 0) 20 else 235
        }

        val decision = evaluator.evaluate(frame)

        assertTrue(decision.debugInfo("frame").joinToString(), decision.accepted)
        assertTrue(decision.metrics.normalizedLaplacianVariance > 10.0)
    }

    @Test
    fun rejectsSmoothButWellExposedFrameAsBlurred() {
        val frame = frame(320, 240) { _, column -> column * 255 / 319 }

        val decision = evaluator.evaluate(frame)

        assertFalse(decision.accepted)
        assertEquals(ScanRejectionReason.RETAKE_BLUR, decision.rejectionReason)
    }

    @Test
    fun rejectsStronglyOverexposedFrame() {
        val frame = frame(320, 240) { row, column ->
            if (row in 100..115 && column in 140..179) 30 else 255
        }

        val decision = evaluator.evaluate(frame)

        assertFalse(decision.accepted)
        assertEquals(ScanRejectionReason.RETAKE_EXPOSURE, decision.rejectionReason)
        assertTrue(decision.metrics.highlightClipRatio > 0.985)
    }

    @Test
    fun cardRegionCanRejectBadCardEvenWhenBackgroundIsSharp() {
        val pixels = IntArray(240 * 180) { index -> if ((index / 240 + index % 240) % 2 == 0) 0 else 255 }
        for (row in 30..150) {
            for (column in 40..200) {
                pixels[row * 240 + column] = 80 + (column - 40) * 80 / 160
            }
        }
        val frame = MiniProgramFrame(240, 180, pixels)
        val anchors = anchors(lu = 40 to 30, ru = 200 to 30, ld = 40 to 150, rd = 200 to 150)

        val decision = FrameQualityEvaluator(FrameQualityThresholds.CARD_ROI).evaluate(frame, anchors)

        assertFalse(decision.accepted)
        assertEquals(ScanRejectionReason.RETAKE_BLUR, decision.rejectionReason)
    }

    private fun frame(width: Int, height: Int, value: (Int, Int) -> Int): MiniProgramFrame =
        MiniProgramFrame(
            width = width,
            height = height,
            pixels = IntArray(width * height) { index -> value(index / width, index % width) },
        )

    private fun anchors(
        lu: Pair<Int, Int>,
        ru: Pair<Int, Int>,
        ld: Pair<Int, Int>,
        rd: Pair<Int, Int>,
    ): MiniProgramAnchors {
        fun candidate(kind: MiniProgramCornerKind, value: Pair<Int, Int>) =
            MiniProgramCornerCandidate(kind, MiniProgramPoint(row = value.second, column = value.first), 20)
        val luCandidate = candidate(MiniProgramCornerKind.LU, lu)
        val ruCandidate = candidate(MiniProgramCornerKind.RU, ru)
        val ldCandidate = candidate(MiniProgramCornerKind.LD, ld)
        val rdCandidate = candidate(MiniProgramCornerKind.RD, rd)
        return MiniProgramAnchors(
            luCandidate,
            ldCandidate,
            ruCandidate,
            rdCandidate,
            MiniProgramGeometry.isQuad(luCandidate.point, ldCandidate.point, ruCandidate.point, rdCandidate.point),
        )
    }
}
