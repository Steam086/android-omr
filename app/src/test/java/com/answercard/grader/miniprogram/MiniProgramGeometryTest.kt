package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniProgramGeometryTest {
    @Test
    fun centerMeanUsesMiddleTwentyPercentOfFrame() {
        val pixels = IntArray(100 * 100) { 240 }
        for (y in 40 until 60) {
            for (x in 40 until 60) {
                pixels[y * 100 + x] = 100
            }
        }
        val frame = MiniProgramFrame(width = 100, height = 100, pixels = pixels)

        assertEquals(100.0, MiniProgramGeometry.centerMean(frame), 0.001)
    }

    @Test
    fun thresholdAppliesMiniProgramDefaultOffset() {
        val frame = MiniProgramFrame(width = 10, height = 10, pixels = IntArray(100) { 180 })

        assertEquals(150, MiniProgramGeometry.threshold(frame))
        assertEquals(170, MiniProgramGeometry.threshold(frame, thresholdOffset = -10))
    }

    @Test
    fun isQuadAcceptsBalancedMiniProgramCornerGeometry() {
        val result = MiniProgramGeometry.isQuad(
            lu = MiniProgramPoint(row = 10, column = 20),
            ld = MiniProgramPoint(row = 210, column = 22),
            ru = MiniProgramPoint(row = 12, column = 320),
            rd = MiniProgramPoint(row = 212, column = 318),
        )

        assertTrue(result.accepted)
        assertTrue(result.diagonalRelativeDeviation < 0.1)
        assertTrue(result.upDownRelativeDeviation + result.leftRightRelativeDeviation < 0.2)
    }

    @Test
    fun isQuadRejectsSkewedCornerGeometry() {
        val result = MiniProgramGeometry.isQuad(
            lu = MiniProgramPoint(row = 10, column = 20),
            ld = MiniProgramPoint(row = 260, column = 80),
            ru = MiniProgramPoint(row = 12, column = 320),
            rd = MiniProgramPoint(row = 170, column = 318),
        )

        assertFalse(result.accepted)
    }
}
