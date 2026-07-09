package com.answercard.grader.miniprogram

import kotlin.math.abs

data class PerspectivePoint(val x: Double, val y: Double)

/**
 * 3x3 homography from four or more point correspondences.
 * Four points use the exact solver; additional points use a normalized least-squares fit.
 * Coefficients are h11..h32 with h33 fixed to 1.
 */
class PerspectiveMapping private constructor(
    private val forward: DoubleArray,
    private val backward: DoubleArray,
) {
    fun map(point: PerspectivePoint): PerspectivePoint = apply(forward, point)

    fun invert(point: PerspectivePoint): PerspectivePoint = apply(backward, point)

    companion object {
        fun fromCorrespondences(
            source: List<PerspectivePoint>,
            target: List<PerspectivePoint>,
        ): PerspectiveMapping? {
            require(source.size == target.size && source.size >= 4) { "at least 4 paired correspondences required" }
            val forward = (if (source.size == 4) solve(source, target) else solveLeastSquares(source, target)) ?: return null
            val backward = (if (source.size == 4) solve(target, source) else solveLeastSquares(target, source)) ?: return null
            return PerspectiveMapping(forward = forward, backward = backward)
        }

        private fun apply(h: DoubleArray, point: PerspectivePoint): PerspectivePoint {
            val w = h[6] * point.x + h[7] * point.y + 1.0
            return PerspectivePoint(
                x = (h[0] * point.x + h[1] * point.y + h[2]) / w,
                y = (h[3] * point.x + h[4] * point.y + h[5]) / w,
            )
        }

        private fun solve(source: List<PerspectivePoint>, target: List<PerspectivePoint>): DoubleArray? {
            val m = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val s = source[i]
                val t = target[i]
                m[i * 2] = doubleArrayOf(s.x, s.y, 1.0, 0.0, 0.0, 0.0, -t.x * s.x, -t.x * s.y, t.x)
                m[i * 2 + 1] = doubleArrayOf(0.0, 0.0, 0.0, s.x, s.y, 1.0, -t.y * s.x, -t.y * s.y, t.y)
            }
            for (column in 0 until 8) {
                var pivot = column
                for (row in column + 1 until 8) {
                    if (abs(m[row][column]) > abs(m[pivot][column])) pivot = row
                }
                if (abs(m[pivot][column]) < 1e-12) return null
                val swap = m[column]
                m[column] = m[pivot]
                m[pivot] = swap
                for (row in 0 until 8) {
                    if (row == column) continue
                    val factor = m[row][column] / m[column][column]
                    for (k in column until 9) {
                        m[row][k] -= factor * m[column][k]
                    }
                }
            }
            return DoubleArray(8) { m[it][8] / m[it][it] }
        }

        private fun solveLeastSquares(source: List<PerspectivePoint>, target: List<PerspectivePoint>): DoubleArray? {
            val sourceNormalization = normalize(source) ?: return null
            val targetNormalization = normalize(target) ?: return null
            val normalizedSource = source.map(sourceNormalization::apply)
            val normalizedTarget = target.map(targetNormalization::apply)
            val normal = Array(8) { DoubleArray(9) }

            normalizedSource.indices.forEach { index ->
                val s = normalizedSource[index]
                val t = normalizedTarget[index]
                accumulateNormalEquation(
                    normal,
                    doubleArrayOf(s.x, s.y, 1.0, 0.0, 0.0, 0.0, -t.x * s.x, -t.x * s.y),
                    t.x,
                )
                accumulateNormalEquation(
                    normal,
                    doubleArrayOf(0.0, 0.0, 0.0, s.x, s.y, 1.0, -t.y * s.x, -t.y * s.y),
                    t.y,
                )
            }
            val normalized = gaussianSolve(normal) ?: return null
            val normalizedMatrix = doubleArrayOf(
                normalized[0], normalized[1], normalized[2],
                normalized[3], normalized[4], normalized[5],
                normalized[6], normalized[7], 1.0,
            )
            val denormalized = multiply(
                targetNormalization.inverse,
                multiply(normalizedMatrix, sourceNormalization.forward),
            )
            val scale = denormalized[8]
            if (abs(scale) < 1e-12) return null
            return DoubleArray(8) { denormalized[it] / scale }
        }

        private fun accumulateNormalEquation(normal: Array<DoubleArray>, row: DoubleArray, target: Double) {
            for (column in row.indices) {
                for (other in row.indices) {
                    normal[column][other] += row[column] * row[other]
                }
                normal[column][8] += row[column] * target
            }
        }

        private fun gaussianSolve(matrix: Array<DoubleArray>): DoubleArray? {
            for (column in 0 until 8) {
                var pivot = column
                for (row in column + 1 until 8) {
                    if (abs(matrix[row][column]) > abs(matrix[pivot][column])) pivot = row
                }
                if (abs(matrix[pivot][column]) < 1e-10) return null
                val swap = matrix[column]
                matrix[column] = matrix[pivot]
                matrix[pivot] = swap
                val divisor = matrix[column][column]
                for (entry in column until 9) matrix[column][entry] /= divisor
                for (row in 0 until 8) {
                    if (row == column) continue
                    val factor = matrix[row][column]
                    for (entry in column until 9) matrix[row][entry] -= factor * matrix[column][entry]
                }
            }
            return DoubleArray(8) { matrix[it][8] }
        }

        private fun normalize(points: List<PerspectivePoint>): PointNormalization? {
            val centerX = points.sumOf { it.x } / points.size
            val centerY = points.sumOf { it.y } / points.size
            val meanDistance = points.sumOf { point ->
                kotlin.math.hypot(point.x - centerX, point.y - centerY)
            } / points.size
            if (meanDistance < 1e-12) return null
            val scale = kotlin.math.sqrt(2.0) / meanDistance
            return PointNormalization(
                forward = doubleArrayOf(
                    scale, 0.0, -scale * centerX,
                    0.0, scale, -scale * centerY,
                    0.0, 0.0, 1.0,
                ),
                inverse = doubleArrayOf(
                    1.0 / scale, 0.0, centerX,
                    0.0, 1.0 / scale, centerY,
                    0.0, 0.0, 1.0,
                ),
            )
        }

        private fun multiply(first: DoubleArray, second: DoubleArray): DoubleArray {
            val result = DoubleArray(9)
            for (row in 0 until 3) {
                for (column in 0 until 3) {
                    result[row * 3 + column] = (0 until 3).sumOf { index ->
                        first[row * 3 + index] * second[index * 3 + column]
                    }
                }
            }
            return result
        }

        private data class PointNormalization(
            val forward: DoubleArray,
            val inverse: DoubleArray,
        ) {
            fun apply(point: PerspectivePoint): PerspectivePoint {
                val w = forward[6] * point.x + forward[7] * point.y + forward[8]
                return PerspectivePoint(
                    x = (forward[0] * point.x + forward[1] * point.y + forward[2]) / w,
                    y = (forward[3] * point.x + forward[4] * point.y + forward[5]) / w,
                )
            }
        }
    }
}
