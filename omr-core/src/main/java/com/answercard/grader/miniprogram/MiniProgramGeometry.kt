package com.answercard.grader.miniprogram

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class MiniProgramQuadCheck(
    val accepted: Boolean,
    val diagonalRelativeDeviation: Double,
    val upDownRelativeDeviation: Double,
    val leftRightRelativeDeviation: Double,
)

object MiniProgramGeometry {
    fun centerMean(frame: MiniProgramFrame): Double {
        var sum = 0L
        var count = 0
        val rowStart = (frame.height * 0.4).toInt()
        val rowEnd = (frame.height * 0.6).toInt()
        val columnStart = (frame.width * 0.4).toInt()
        val columnEnd = (frame.width * 0.6).toInt()

        for (row in rowStart until rowEnd) {
            for (column in columnStart until columnEnd) {
                sum += frame[row, column]
                count += 1
            }
        }

        return if (count == 0) 0.0 else sum.toDouble() / count.toDouble()
    }

    fun threshold(frame: MiniProgramFrame, thresholdOffset: Int = -30): Int =
        (centerMean(frame).toInt() + thresholdOffset).coerceIn(0, 255)

    fun isQuad(
        lu: MiniProgramPoint,
        ld: MiniProgramPoint,
        ru: MiniProgramPoint,
        rd: MiniProgramPoint,
    ): MiniProgramQuadCheck {
        val leftHeight = ld.row - lu.row
        val rightHeight = rd.row - ru.row
        val topWidth = ru.column - lu.column
        val bottomWidth = rd.column - ld.column
        val diagonalOne = distance(lu, rd)
        val diagonalTwo = distance(ru, ld)
        val diagonalRelativeDeviation = relativeDeviation(diagonalOne, diagonalTwo)
        val upDownRelativeDeviation = relativeDeviation(leftHeight.toDouble(), rightHeight.toDouble())
        val leftRightRelativeDeviation = relativeDeviation(topWidth.toDouble(), bottomWidth.toDouble())

        return MiniProgramQuadCheck(
            accepted = diagonalRelativeDeviation < 0.1 &&
                upDownRelativeDeviation + leftRightRelativeDeviation < 0.2,
            diagonalRelativeDeviation = diagonalRelativeDeviation,
            upDownRelativeDeviation = upDownRelativeDeviation,
            leftRightRelativeDeviation = leftRightRelativeDeviation,
        )
    }

    private fun distance(a: MiniProgramPoint, b: MiniProgramPoint): Double =
        sqrt((a.column - b.column).toDouble().pow(2) + (a.row - b.row).toDouble().pow(2))

    private fun relativeDeviation(a: Double, b: Double): Double {
        val average = (a + b) / 2.0
        return if (average == 0.0) Double.POSITIVE_INFINITY else abs(a - b) / average
    }
}
