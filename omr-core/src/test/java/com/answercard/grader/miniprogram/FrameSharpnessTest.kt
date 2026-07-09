package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameSharpnessTest {
    @Test
    fun uniformFrameHasZeroVariance() {
        val frame = MiniProgramFrame(width = 5, height = 5, pixels = IntArray(25) { 128 })
        assertEquals(0.0, FrameSharpness.laplacianVariance(frame), 1e-9)
    }

    @Test
    fun sharpEdgeScoresHigherThanBlurredEdge() {
        val sharp = columnFrame(intArrayOf(0, 0, 0, 255, 255, 255))
        val blurred = columnFrame(intArrayOf(0, 0, 85, 170, 255, 255))
        val sharpScore = FrameSharpness.laplacianVariance(sharp)
        val blurredScore = FrameSharpness.laplacianVariance(blurred)
        assertTrue("sharp=$sharpScore blurred=$blurredScore", sharpScore > blurredScore)
        assertTrue("blurred edge still carries some detail", blurredScore > 0.0)
    }

    @Test
    fun frameSmallerThanKernelHasZeroVariance() {
        val frame = MiniProgramFrame(width = 2, height = 2, pixels = intArrayOf(0, 255, 255, 0))
        assertEquals(0.0, FrameSharpness.laplacianVariance(frame), 1e-9)
    }

    private fun columnFrame(columns: IntArray): MiniProgramFrame {
        val height = 3
        val width = columns.size
        val pixels = IntArray(width * height) { index -> columns[index % width] }
        return MiniProgramFrame(width = width, height = height, pixels = pixels)
    }
}
