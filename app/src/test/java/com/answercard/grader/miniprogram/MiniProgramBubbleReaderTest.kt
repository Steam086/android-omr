package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniProgramBubbleReaderTest {
    @Test
    fun whiteCellIsNotMarked() {
        val frame = frame(width = 80, height = 80, value = 230)
        val result = MiniProgramBubbleReader.read(frame, cell(row = 10, column = 10, size = 20))

        assertFalse(result.isMarked)
        assertEquals(0, result.centralBlackCount)
        assertEquals(0, result.totalBlackCount)
        assertNull(result.failureReason)
    }

    @Test
    fun centralBlackBlockIsMarked() {
        val frame = frame(width = 80, height = 80, value = 230)
        fillRect(frame, row = 16, column = 16, height = 12, width = 12, value = 20)

        val result = MiniProgramBubbleReader.read(frame, cell(row = 10, column = 10, size = 24))

        assertTrue(result.isMarked)
        assertTrue(result.centralBlackCount > 0)
        assertTrue(result.totalBlackCount >= result.centralBlackCount)
        assertEquals(0, result.noisePixelsRemoved)
        assertTrue(result.largestComponentArea > 0)
        assertTrue(result.debugMatrix.any { row -> row.contains('0') })
    }

    @Test
    fun uniformlyDarkCellIsMarkedWhenAdaptiveThresholdCollapses() {
        val frame = frame(width = 80, height = 80, value = 230)
        fillRect(frame, row = 10, column = 10, height = 24, width = 24, value = 20)

        val result = MiniProgramBubbleReader.read(frame, cell(row = 10, column = 10, size = 24))

        assertTrue(result.centralMeanGray <= 20.0)
        assertTrue(result.isMarked)
    }

    @Test
    fun narrowLineDoesNotMarkCellEvenWhenCentralAreaHasBlackPixels() {
        val frame = frame(width = 80, height = 80, value = 230)
        fillRect(frame, row = 10, column = 20, height = 24, width = 3, value = 20)

        val result = MiniProgramBubbleReader.read(frame, cell(row = 10, column = 10, size = 24))

        assertFalse(result.isMarked)
        assertTrue(result.centralBlackCount > 0)
        assertTrue(result.solidBoundsWidth < 6)
        assertTrue(result.centralMeanGray < 230.0)
    }

    @Test
    fun solidBlockReportsContainCountAndBounds() {
        val frame = frame(width = 80, height = 80, value = 230)
        fillRect(frame, row = 16, column = 16, height = 12, width = 12, value = 20)

        val result = MiniProgramBubbleReader.read(frame, cell(row = 10, column = 10, size = 24))

        assertTrue(result.isMarked)
        assertTrue(result.containCount >= 4)
        assertTrue(result.solidBoundsWidth >= 6)
        assertTrue(result.solidBoundsHeight >= 6)
        assertTrue(result.centralMeanGray < 230.0)
    }

    @Test
    fun centralSmallNoiseIsRemovedBeforeMarkedDecision() {
        val frame = frame(width = 80, height = 80, value = 230)
        frame.pixels[20 * frame.width + 20] = 20

        val result = MiniProgramBubbleReader.read(frame, cell(row = 10, column = 10, size = 24))

        assertFalse(result.isMarked)
        assertEquals(0, result.centralBlackCount)
        assertTrue(result.noisePixelsRemoved > 0)
    }

    @Test
    fun cleanedEdgeLineDoesNotMarkCell() {
        val frame = frame(width = 80, height = 80, value = 230)
        fillRect(frame, row = 10, column = 10, height = 24, width = 3, value = 20)

        val result = MiniProgramBubbleReader.read(
            frame = frame,
            cell = cell(row = 10, column = 10, size = 24),
            edgeCleanDirections = setOf(MiniProgramEdgeCleanDirection.LEFT),
        )

        assertFalse(result.isMarked)
        assertEquals(setOf(MiniProgramEdgeCleanDirection.LEFT), result.edgeCleanDirections)
        assertEquals(0, result.centralBlackCount)
        assertTrue(result.debugMatrix.all { row -> row.take(4).all { value -> value == '1' } })
    }

    @Test
    fun lightFillIsMarkedByAdaptiveThreshold() {
        val frame = frame(width = 80, height = 80, value = 230)
        fillRect(frame, row = 16, column = 16, height = 12, width = 12, value = 150)

        val result = MiniProgramBubbleReader.read(frame, cell(row = 10, column = 10, size = 24))

        assertTrue(result.isMarked)
        assertTrue(result.blackThreshold > 150)
        assertTrue(result.centralBlackCount > 0)
    }

    @Test
    fun noiseAndRealFillKeepsRealFill() {
        val frame = frame(width = 90, height = 90, value = 230)
        fillRect(frame, row = 18, column = 18, height = 12, width = 12, value = 20)
        frame.pixels[12 * frame.width + 12] = 20
        frame.pixels[37 * frame.width + 37] = 20

        val result = MiniProgramBubbleReader.read(frame, cell(row = 10, column = 10, size = 30))

        assertTrue(result.isMarked)
        assertTrue(result.centralBlackCount > 0)
        assertTrue(result.noisePixelsRemoved > 0)
        assertTrue(result.componentsKept > 0)
    }

    @Test
    fun perspectiveCellSamplesWithoutCoordinateReversal() {
        val frame = frame(width = 120, height = 120, value = 230)
        fillRect(frame, row = 45, column = 45, height = 12, width = 12, value = 20)
        val perspectiveCell = MiniProgramCell(
            row = 0,
            column = 0,
            leftTop = MiniProgramGridPoint(row = 20.0, column = 20.0),
            rightTop = MiniProgramGridPoint(row = 18.0, column = 80.0),
            leftBottom = MiniProgramGridPoint(row = 88.0, column = 16.0),
            rightBottom = MiniProgramGridPoint(row = 92.0, column = 86.0),
        )

        val result = MiniProgramBubbleReader.read(frame, perspectiveCell)

        assertNull(result.failureReason)
        assertTrue(result.sampleRows > 0)
        assertTrue(result.sampleColumns > 0)
        assertTrue(result.debugMatrix.size == result.sampleRows)
        assertTrue(result.debugMatrix.all { it.length == result.sampleColumns })
    }

    @Test
    fun invalidOrOutOfBoundsCellReturnsClearFailure() {
        val frame = frame(width = 80, height = 80, value = 230)
        val invalidCell = MiniProgramCell(
            row = 0,
            column = 0,
            leftTop = MiniProgramGridPoint(row = 10.0, column = 10.0),
            rightTop = MiniProgramGridPoint(row = Double.NaN, column = 20.0),
            leftBottom = MiniProgramGridPoint(row = 30.0, column = 10.0),
            rightBottom = MiniProgramGridPoint(row = 30.0, column = 20.0),
        )
        val outOfBoundsCell = cell(row = -5, column = 10, size = 20)
        val tooSmallCell = cell(row = 10, column = 10, size = 4)
        val reversedCell = MiniProgramCell(
            row = 0,
            column = 0,
            leftTop = MiniProgramGridPoint(row = 30.0, column = 30.0),
            rightTop = MiniProgramGridPoint(row = 30.0, column = 10.0),
            leftBottom = MiniProgramGridPoint(row = 50.0, column = 30.0),
            rightBottom = MiniProgramGridPoint(row = 50.0, column = 10.0),
        )

        assertEquals("cell points must be finite", MiniProgramBubbleReader.read(frame, invalidCell).failureReason)
        assertEquals("cell must be inside frame", MiniProgramBubbleReader.read(frame, outOfBoundsCell).failureReason)
        assertEquals(
            "source cell too small: minimum=10x8, actual=4.0x4.0",
            MiniProgramBubbleReader.read(frame, tooSmallCell).failureReason,
        )
        assertEquals("cell points must form a valid quadrilateral", MiniProgramBubbleReader.read(frame, reversedCell).failureReason)
    }

    private fun cell(row: Int, column: Int, size: Int): MiniProgramCell =
        MiniProgramCell(
            row = 0,
            column = 0,
            leftTop = MiniProgramGridPoint(row = row.toDouble(), column = column.toDouble()),
            rightTop = MiniProgramGridPoint(row = row.toDouble(), column = (column + size).toDouble()),
            leftBottom = MiniProgramGridPoint(row = (row + size).toDouble(), column = column.toDouble()),
            rightBottom = MiniProgramGridPoint(row = (row + size).toDouble(), column = (column + size).toDouble()),
        )

    private fun frame(width: Int, height: Int, value: Int): MiniProgramFrame =
        MiniProgramFrame(width = width, height = height, pixels = IntArray(width * height) { value })

    private fun fillRect(
        frame: MiniProgramFrame,
        row: Int,
        column: Int,
        height: Int,
        width: Int,
        value: Int,
    ) {
        for (y in row until row + height) {
            for (x in column until column + width) {
                frame.pixels[y * frame.width + x] = value
            }
        }
    }
}
