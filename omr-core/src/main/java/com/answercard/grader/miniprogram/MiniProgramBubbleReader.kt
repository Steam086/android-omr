package com.answercard.grader.miniprogram

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

object MiniProgramBubbleReader {
    private const val BLACK_RATIO = 0.2
    private const val RANGE_PERCENT = 0.9
    private const val MIN_SAMPLE_SIZE = 16
    private const val MIN_CENTRAL_BLACK_RATIO = 0.08

    fun read(
        frame: MiniProgramFrame,
        cell: MiniProgramCell,
        edgeCleanDirections: Set<MiniProgramEdgeCleanDirection> = emptySet(),
    ): MiniProgramBubbleReadResult {
        val failureReason = validate(frame, cell)
        if (failureReason != null) {
            return failure(failureReason, edgeCleanDirections)
        }

        val sampleRows = sampleRows(cell)
        val sampleColumns = sampleColumns(cell)
        val grayValues = IntArray(sampleRows * sampleColumns)
        var index = 0
        for (row in 0 until sampleRows) {
            val rowRatio = (row + 0.5) / sampleRows.toDouble()
            for (column in 0 until sampleColumns) {
                val columnRatio = (column + 0.5) / sampleColumns.toDouble()
                val point = interpolate(cell, rowRatio, columnRatio)
                grayValues[index++] = frame[floor(point.row).toInt(), floor(point.column).toInt()]
            }
        }

        val blackThreshold = blackThreshold(grayValues)
        val binary = IntArray(grayValues.size) { i -> if (grayValues[i] >= blackThreshold) 1 else 0 }
        cleanEdges(binary, sampleRows, sampleColumns, edgeCleanDirections)
        val cleanResult = MiniProgramConnectedComponentCleaner.clean(binary, sampleRows, sampleColumns)
        val cleanedBinary = cleanResult.cleanedBinary

        val totalBlackCount = binary.count { it == 0 }
        val cleanedTotalBlackCount = cleanedBinary.count { it == 0 }
        val centralBlackCount = centralBlackCount(cleanedBinary, sampleRows, sampleColumns)
        val centralArea = centralArea(sampleRows, sampleColumns)
        val isMarked = centralArea > 0 &&
            centralBlackCount.toDouble() / centralArea >= MIN_CENTRAL_BLACK_RATIO
        val debugMatrix = cleanedBinary.asList()
            .chunked(sampleColumns)
            .map { row -> row.joinToString(separator = "") }

        return MiniProgramBubbleReadResult(
            isMarked = isMarked,
            containCount = centralBlackCount,
            blackThreshold = blackThreshold,
            totalBlackCount = totalBlackCount,
            centralBlackCount = centralBlackCount,
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

    private fun centralArea(sampleRows: Int, sampleColumns: Int): Int {
        val rowStart = sampleRows / 4
        val columnStart = sampleColumns / 4
        return (sampleRows - 2 * rowStart) * (sampleColumns - 2 * columnStart)
    }

    private fun interpolate(
        cell: MiniProgramCell,
        rowRatio: Double,
        columnRatio: Double,
    ): MiniProgramGridPoint =
        MiniProgramGridBuilder.interpolate(
            lu = cell.leftTop,
            ld = cell.leftBottom,
            ru = cell.rightTop,
            rd = cell.rightBottom,
            rowRatio = rowRatio,
            columnRatio = columnRatio,
        )

    private fun sampleRows(cell: MiniProgramCell): Int =
        ((distance(cell.leftTop, cell.leftBottom) + distance(cell.rightTop, cell.rightBottom)) / 2.0)
            .roundToInt()
            .coerceAtLeast(1)

    private fun sampleColumns(cell: MiniProgramCell): Int =
        ((distance(cell.leftTop, cell.rightTop) + distance(cell.leftBottom, cell.rightBottom)) / 2.0)
            .roundToInt()
            .coerceAtLeast(1)

    private fun distance(a: MiniProgramGridPoint, b: MiniProgramGridPoint): Double {
        val row = a.row - b.row
        val column = a.column - b.column
        return sqrt(row * row + column * column)
    }

    private fun validate(frame: MiniProgramFrame, cell: MiniProgramCell): String? {
        val points = listOf(cell.leftTop, cell.rightTop, cell.leftBottom, cell.rightBottom)
        if (points.any { !it.row.isFinite() || !it.column.isFinite() }) {
            return "cell points must be finite"
        }
        if (points.any { it.row < 0.0 || it.row >= frame.height || it.column < 0.0 || it.column >= frame.width }) {
            return "cell must be inside frame"
        }
        if (!isValidCell(cell)) {
            return "cell points must form a valid quadrilateral"
        }
        val rows = sampleRows(cell)
        val columns = sampleColumns(cell)
        if (rows < MIN_SAMPLE_SIZE || columns < MIN_SAMPLE_SIZE) {
            return "cell sample size must be at least $MIN_SAMPLE_SIZE by $MIN_SAMPLE_SIZE, actual=${columns}x$rows"
        }
        return null
    }

    private fun isValidCell(cell: MiniProgramCell): Boolean =
        cell.rightTop.column > cell.leftTop.column &&
            cell.rightBottom.column > cell.leftBottom.column &&
            cell.leftBottom.row > cell.leftTop.row &&
            cell.rightBottom.row > cell.rightTop.row &&
            area(cell) > 0.0

    private fun area(cell: MiniProgramCell): Double {
        val points = listOf(cell.leftTop, cell.rightTop, cell.rightBottom, cell.leftBottom)
        var twiceArea = 0.0
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            twiceArea += current.column * next.row - current.row * next.column
        }
        return kotlin.math.abs(twiceArea) / 2.0
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
}
