package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniProgramCellSamplerTest {
    @Test
    fun acceptsMinimumTenByEightSourceCellAndNormalizesToSixteenSquare() {
        val sample = MiniProgramCellSampler.sample(
            frame = gradientFrame(40, 30),
            cell = rectangularCell(5.0, 5.0, 10.0, 8.0),
        )

        assertNull(sample.failureReason)
        assertEquals(16, sample.rows)
        assertEquals(16, sample.columns)
        assertEquals(256, sample.grayValues.size)
        assertEquals(10.0, sample.metrics.width, 0.001)
        assertEquals(8.0, sample.metrics.height, 0.001)
        assertEquals(80.0, sample.metrics.area, 0.001)
    }

    @Test
    fun rejectsSourceCellNarrowerThanTenPixels() {
        val sample = MiniProgramCellSampler.sample(
            frame = gradientFrame(40, 30),
            cell = rectangularCell(5.0, 5.0, 9.9, 8.0),
        )

        assertNotNull(sample.failureReason)
        assertTrue(sample.failureReason.orEmpty().contains("source cell too small"))
    }

    @Test
    fun rejectsSkewedCellWhoseAreaIsBelowEightyPixels() {
        val cell = MiniProgramCell(
            row = 0,
            column = 0,
            leftTop = MiniProgramGridPoint(5.0, 5.0),
            rightTop = MiniProgramGridPoint(5.0, 15.0),
            leftBottom = MiniProgramGridPoint(9.0, 11.9282032303),
            rightBottom = MiniProgramGridPoint(9.0, 21.9282032303),
        )

        val sample = MiniProgramCellSampler.sample(gradientFrame(40, 30), cell)

        assertTrue(sample.metrics.width >= 10.0)
        assertTrue(sample.metrics.height >= 8.0)
        assertTrue(sample.metrics.area < 80.0)
        assertTrue(sample.failureReason.orEmpty().contains("source cell area"))
    }

    @Test
    fun acceptsCellOnOnePixelInterpolationMargin() {
        val sample = MiniProgramCellSampler.sample(
            frame = gradientFrame(40, 30),
            cell = rectangularCell(1.0, 1.0, 10.0, 8.0),
        )

        assertNull(sample.failureReason)
        assertTrue(sample.metrics.insideFrame)
    }

    @Test
    fun rejectsCellCrossingInterpolationMargin() {
        val sample = MiniProgramCellSampler.sample(
            frame = gradientFrame(40, 30),
            cell = rectangularCell(0.9, 1.0, 10.0, 8.0),
        )

        assertTrue(sample.failureReason.orEmpty().contains("interpolation margin"))
        assertEquals(false, sample.metrics.insideFrame)
    }

    @Test
    fun bilinearlySamplesGradientAtOutputCellCenters() {
        val sample = MiniProgramCellSampler.sample(
            frame = gradientFrame(40, 30),
            cell = rectangularCell(5.0, 5.0, 10.0, 8.0),
        )

        assertNull(sample.failureReason)
        assertEquals(32, sample.grayValues.first())
        assertEquals(78, sample.grayValues.last())
    }

    private fun gradientFrame(width: Int, height: Int): MiniProgramFrame =
        MiniProgramFrame(
            width = width,
            height = height,
            pixels = IntArray(width * height) { index ->
                val row = index / width
                val column = index % width
                row * 5 + column
            },
        )

    private fun rectangularCell(left: Double, top: Double, width: Double, height: Double) =
        MiniProgramCell(
            row = 0,
            column = 0,
            leftTop = MiniProgramGridPoint(top, left),
            rightTop = MiniProgramGridPoint(top, left + width),
            leftBottom = MiniProgramGridPoint(top + height, left),
            rightBottom = MiniProgramGridPoint(top + height, left + width),
        )
}
