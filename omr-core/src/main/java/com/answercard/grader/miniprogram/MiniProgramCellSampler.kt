package com.answercard.grader.miniprogram

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class MiniProgramCellSourceMetrics(
    val width: Double,
    val height: Double,
    val area: Double,
    val insideFrame: Boolean,
) {
    val hasEnoughSourceInformation: Boolean
        get() = width + 1e-6 >= MiniProgramCellSampler.MIN_SOURCE_WIDTH &&
            height + 1e-6 >= MiniProgramCellSampler.MIN_SOURCE_HEIGHT &&
            area + 1e-6 >= MiniProgramCellSampler.MIN_SOURCE_AREA
}

data class MiniProgramCellSample(
    val rows: Int,
    val columns: Int,
    val grayValues: IntArray,
    val metrics: MiniProgramCellSourceMetrics,
    val failureReason: String?,
)

object MiniProgramCellSampler {
    const val TARGET_ROWS = 16
    const val TARGET_COLUMNS = 16
    const val MIN_SOURCE_WIDTH = 10.0
    const val MIN_SOURCE_HEIGHT = 8.0
    const val MIN_SOURCE_AREA = 80.0
    const val INTERPOLATION_MARGIN = 1.0

    fun sample(frame: MiniProgramFrame, cell: MiniProgramCell): MiniProgramCellSample {
        val metrics = sourceMetrics(frame, cell)
        val failureReason = validateSource(frame, cell, metrics)
        if (failureReason != null) {
            return MiniProgramCellSample(0, 0, IntArray(0), metrics, failureReason)
        }

        val values = IntArray(TARGET_ROWS * TARGET_COLUMNS)
        var index = 0
        for (row in 0 until TARGET_ROWS) {
            val rowRatio = (row + 0.5) / TARGET_ROWS.toDouble()
            for (column in 0 until TARGET_COLUMNS) {
                val columnRatio = (column + 0.5) / TARGET_COLUMNS.toDouble()
                val point = MiniProgramGridBuilder.interpolate(
                    lu = cell.leftTop,
                    ld = cell.leftBottom,
                    ru = cell.rightTop,
                    rd = cell.rightBottom,
                    rowRatio = rowRatio,
                    columnRatio = columnRatio,
                )
                values[index++] = bilinearLuma(frame, point)
            }
        }
        return MiniProgramCellSample(
            rows = TARGET_ROWS,
            columns = TARGET_COLUMNS,
            grayValues = values,
            metrics = metrics,
            failureReason = null,
        )
    }

    fun sourceMetrics(frame: MiniProgramFrame, cell: MiniProgramCell): MiniProgramCellSourceMetrics {
        val points = points(cell)
        val finite = points.all { it.row.isFinite() && it.column.isFinite() }
        val insideFrame = finite && points.all {
            it.row >= INTERPOLATION_MARGIN &&
                it.column >= INTERPOLATION_MARGIN &&
                it.row <= frame.height - 1.0 - INTERPOLATION_MARGIN &&
                it.column <= frame.width - 1.0 - INTERPOLATION_MARGIN
        }
        return MiniProgramCellSourceMetrics(
            width = averageOppositeDistance(cell.leftTop, cell.rightTop, cell.leftBottom, cell.rightBottom),
            height = averageOppositeDistance(cell.leftTop, cell.leftBottom, cell.rightTop, cell.rightBottom),
            area = area(cell),
            insideFrame = insideFrame,
        )
    }

    fun validateSource(
        frame: MiniProgramFrame,
        cell: MiniProgramCell,
        metrics: MiniProgramCellSourceMetrics = sourceMetrics(frame, cell),
    ): String? {
        val points = points(cell)
        if (points.any { !it.row.isFinite() || !it.column.isFinite() }) {
            return "cell points must be finite"
        }
        if (points.any { it.row < 0.0 || it.row >= frame.height || it.column < 0.0 || it.column >= frame.width }) {
            return "cell must be inside frame"
        }
        if (!isValidCell(cell)) {
            return "cell points must form a valid quadrilateral"
        }
        if (!metrics.insideFrame) {
            return "cell must stay inside ${INTERPOLATION_MARGIN.roundToInt()}px interpolation margin"
        }
        if (metrics.width + 1e-6 < MIN_SOURCE_WIDTH || metrics.height + 1e-6 < MIN_SOURCE_HEIGHT) {
            return "source cell too small: minimum=${MIN_SOURCE_WIDTH.roundToInt()}x${MIN_SOURCE_HEIGHT.roundToInt()}, " +
                "actual=${format(metrics.width)}x${format(metrics.height)}"
        }
        if (metrics.area + 1e-6 < MIN_SOURCE_AREA) {
            return "source cell area too small: minimum=${MIN_SOURCE_AREA.roundToInt()}, actual=${format(metrics.area)}"
        }
        return null
    }

    private fun bilinearLuma(frame: MiniProgramFrame, point: MiniProgramGridPoint): Int {
        val top = floor(point.row).toInt()
        val left = floor(point.column).toInt()
        val rowFraction = point.row - top
        val columnFraction = point.column - left
        val topValue = frame[top, left] * (1.0 - columnFraction) + frame[top, left + 1] * columnFraction
        val bottomValue = frame[top + 1, left] * (1.0 - columnFraction) + frame[top + 1, left + 1] * columnFraction
        return (topValue * (1.0 - rowFraction) + bottomValue * rowFraction).roundToInt().coerceIn(0, 255)
    }

    private fun isValidCell(cell: MiniProgramCell): Boolean =
        cell.rightTop.column > cell.leftTop.column &&
            cell.rightBottom.column > cell.leftBottom.column &&
            cell.leftBottom.row > cell.leftTop.row &&
            cell.rightBottom.row > cell.rightTop.row &&
            area(cell) > 0.0

    private fun points(cell: MiniProgramCell): List<MiniProgramGridPoint> =
        listOf(cell.leftTop, cell.rightTop, cell.leftBottom, cell.rightBottom)

    private fun averageOppositeDistance(
        firstStart: MiniProgramGridPoint,
        firstEnd: MiniProgramGridPoint,
        secondStart: MiniProgramGridPoint,
        secondEnd: MiniProgramGridPoint,
    ): Double = (distance(firstStart, firstEnd) + distance(secondStart, secondEnd)) / 2.0

    private fun distance(a: MiniProgramGridPoint, b: MiniProgramGridPoint): Double {
        val row = a.row - b.row
        val column = a.column - b.column
        return sqrt(row * row + column * column)
    }

    private fun area(cell: MiniProgramCell): Double {
        val points = listOf(cell.leftTop, cell.rightTop, cell.rightBottom, cell.leftBottom)
        var twiceArea = 0.0
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            twiceArea += current.column * next.row - current.row * next.column
        }
        return abs(twiceArea) / 2.0
    }

    private fun format(value: Double): String = "%.1f".format(java.util.Locale.US, value)
}
