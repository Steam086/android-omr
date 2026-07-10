package com.answercard.grader.miniprogram

import kotlin.math.floor

object MiniProgramBubbleReader {
    private const val BLACK_RATIO = 0.2
    private const val RANGE_PERCENT = 0.9
    private const val MIN_SOLID_NEIGHBOR_BLACK_COUNT = 8
    // The reader now operates on a normalized 16x16 grid. Requiring a fifth of the central
    // region keeps thin printed strokes and perspective noise from becoming solid fill evidence.
    private const val MIN_CONTAIN_RATIO = 0.2
    private const val MIN_SOLID_BOUNDS_RATIO = 0.25
    private const val ABSOLUTE_DARK_MEAN_THRESHOLD = 80.0

    fun read(
        frame: MiniProgramFrame,
        cell: MiniProgramCell,
        edgeCleanDirections: Set<MiniProgramEdgeCleanDirection> = emptySet(),
    ): MiniProgramBubbleReadResult {
        val sample = MiniProgramCellSampler.sample(frame, cell)
        if (sample.failureReason != null) return failure(sample.failureReason, edgeCleanDirections)

        val sampleRows = sample.rows
        val sampleColumns = sample.columns
        val grayValues = sample.grayValues

        val blackThreshold = blackThreshold(grayValues)
        val binary = IntArray(grayValues.size) { i -> if (grayValues[i] >= blackThreshold) 1 else 0 }
        cleanEdges(binary, sampleRows, sampleColumns, edgeCleanDirections)
        val cleanResult = MiniProgramConnectedComponentCleaner.clean(binary, sampleRows, sampleColumns)
        val cleanedBinary = cleanResult.cleanedBinary

        val totalBlackCount = binary.count { it == 0 }
        val cleanedTotalBlackCount = cleanedBinary.count { it == 0 }
        val centralBlackCount = centralBlackCount(cleanedBinary, sampleRows, sampleColumns)
        val centralArea = centralArea(sampleRows, sampleColumns)
        val centralMeanGrayValue = centralMeanGray(grayValues, sampleRows, sampleColumns)
        val solidity = solidityEvidence(cleanedBinary, sampleRows, sampleColumns)
        val minContainCount = maxOf(4, (centralArea * MIN_CONTAIN_RATIO).toInt())
        val minSolidWidth = (sampleColumns * MIN_SOLID_BOUNDS_RATIO).toInt().coerceAtLeast(1)
        val minSolidHeight = (sampleRows * MIN_SOLID_BOUNDS_RATIO).toInt().coerceAtLeast(1)
        val isMarked = centralMeanGrayValue <= ABSOLUTE_DARK_MEAN_THRESHOLD ||
            solidity.containCount >= minContainCount &&
            solidity.boundsWidth >= minSolidWidth &&
            solidity.boundsHeight >= minSolidHeight
        val debugMatrix = cleanedBinary.asList()
            .chunked(sampleColumns)
            .map { row -> row.joinToString(separator = "") }

        return MiniProgramBubbleReadResult(
            isMarked = isMarked,
            containCount = solidity.containCount,
            blackThreshold = blackThreshold,
            totalBlackCount = totalBlackCount,
            centralBlackCount = centralBlackCount,
            centralMeanGray = centralMeanGrayValue,
            solidBoundsWidth = solidity.boundsWidth,
            solidBoundsHeight = solidity.boundsHeight,
            cleanedTotalBlackCount = cleanedTotalBlackCount,
            noiseComponentsRemoved = cleanResult.noiseComponentsRemoved,
            noisePixelsRemoved = cleanResult.noisePixelsRemoved,
            componentsKept = cleanResult.componentsKept,
            largestComponentArea = cleanResult.largestComponentArea,
            sampleRows = sampleRows,
            sampleColumns = sampleColumns,
            edgeCleanDirections = edgeCleanDirections,
            failureReason = null,
            debugMatrix = debugMatrix,
        )
    }

    private fun blackThreshold(values: IntArray): Int {
        val sortedDescending = values.sortedDescending()
        val index = floor(sortedDescending.size * BLACK_RATIO).toInt().coerceIn(0, sortedDescending.lastIndex)
        return floor(sortedDescending[index] * RANGE_PERCENT).toInt().coerceIn(0, 255)
    }

    private fun cleanEdges(
        binary: IntArray,
        sampleRows: Int,
        sampleColumns: Int,
        directions: Set<MiniProgramEdgeCleanDirection>,
    ) {
        for (index in binary.indices) {
            if (directions.any { shouldClean(it, index, sampleRows, sampleColumns) }) {
                binary[index] = 1
            }
        }
    }

    private fun shouldClean(
        direction: MiniProgramEdgeCleanDirection,
        index: Int,
        sampleRows: Int,
        sampleColumns: Int,
    ): Boolean {
        val row = index / sampleColumns
        val column = index % sampleColumns
        return when (direction) {
            MiniProgramEdgeCleanDirection.LEFT ->
                sampleColumns >= 7 && column == 0 ||
                    sampleColumns >= 9 && column == 1 ||
                    sampleColumns >= 10 && column == 2 ||
                    sampleColumns >= 12 && column == 3
            MiniProgramEdgeCleanDirection.RIGHT ->
                sampleColumns >= 7 && column == sampleColumns - 1 ||
                    sampleColumns >= 9 && column == sampleColumns - 2 ||
                    sampleColumns >= 10 && column == sampleColumns - 3 ||
                    sampleColumns >= 12 && column == sampleColumns - 4
            MiniProgramEdgeCleanDirection.UP -> row < 2
            MiniProgramEdgeCleanDirection.DOWN -> row >= sampleRows - 2
        }
    }

    private fun centralBlackCount(binary: IntArray, sampleRows: Int, sampleColumns: Int): Int {
        val rowStart = sampleRows / 4
        val rowEnd = sampleRows - rowStart
        val columnStart = sampleColumns / 4
        val columnEnd = sampleColumns - columnStart
        var count = 0
        for (row in rowStart until rowEnd) {
            for (column in columnStart until columnEnd) {
                if (binary[row * sampleColumns + column] == 0) count += 1
            }
        }
        return count
    }

    private fun centralMeanGray(grayValues: IntArray, sampleRows: Int, sampleColumns: Int): Double {
        val rowStart = sampleRows / 4
        val rowEnd = sampleRows - rowStart
        val columnStart = sampleColumns / 4
        val columnEnd = sampleColumns - columnStart
        var sum = 0L
        var count = 0
        for (row in rowStart until rowEnd) {
            for (column in columnStart until columnEnd) {
                sum += grayValues[row * sampleColumns + column]
                count += 1
            }
        }
        return if (count == 0) 255.0 else sum.toDouble() / count.toDouble()
    }

    private fun solidityEvidence(binary: IntArray, sampleRows: Int, sampleColumns: Int): SolidityEvidence {
        val rowStart = maxOf(1, sampleRows / 4)
        val rowEnd = minOf(sampleRows - 1, sampleRows - sampleRows / 4)
        val columnStart = maxOf(1, sampleColumns / 4)
        val columnEnd = minOf(sampleColumns - 1, sampleColumns - sampleColumns / 4)
        var count = 0
        var minRow = Int.MAX_VALUE
        var maxRow = Int.MIN_VALUE
        var minColumn = Int.MAX_VALUE
        var maxColumn = Int.MIN_VALUE
        for (row in rowStart until rowEnd) {
            for (column in columnStart until columnEnd) {
                if (blackNeighbors(binary, sampleRows, sampleColumns, row, column) >= MIN_SOLID_NEIGHBOR_BLACK_COUNT) {
                    count += 1
                    minRow = minOf(minRow, row)
                    maxRow = maxOf(maxRow, row)
                    minColumn = minOf(minColumn, column)
                    maxColumn = maxOf(maxColumn, column)
                }
            }
        }
        val width = if (count == 0) 0 else maxColumn - minColumn + 1
        val height = if (count == 0) 0 else maxRow - minRow + 1
        return SolidityEvidence(containCount = count, boundsWidth = width, boundsHeight = height)
    }

    private fun blackNeighbors(
        binary: IntArray,
        sampleRows: Int,
        sampleColumns: Int,
        row: Int,
        column: Int,
    ): Int {
        var count = 0
        for (r in row - 1..row + 1) {
            for (c in column - 1..column + 1) {
                if (r in 0 until sampleRows && c in 0 until sampleColumns && binary[r * sampleColumns + c] == 0) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun centralArea(sampleRows: Int, sampleColumns: Int): Int {
        val rowStart = sampleRows / 4
        val columnStart = sampleColumns / 4
        return (sampleRows - 2 * rowStart) * (sampleColumns - 2 * columnStart)
    }

    private fun failure(
        reason: String,
        edgeCleanDirections: Set<MiniProgramEdgeCleanDirection>,
    ): MiniProgramBubbleReadResult =
        MiniProgramBubbleReadResult(
            isMarked = false,
            containCount = 0,
            blackThreshold = 0,
            totalBlackCount = 0,
            centralBlackCount = 0,
            centralMeanGray = 255.0,
            solidBoundsWidth = 0,
            solidBoundsHeight = 0,
            cleanedTotalBlackCount = 0,
            noiseComponentsRemoved = 0,
            noisePixelsRemoved = 0,
            componentsKept = 0,
            largestComponentArea = 0,
            sampleRows = 0,
            sampleColumns = 0,
            edgeCleanDirections = edgeCleanDirections,
            failureReason = reason,
        )

    private data class SolidityEvidence(
        val containCount: Int,
        val boundsWidth: Int,
        val boundsHeight: Int,
    )
}
