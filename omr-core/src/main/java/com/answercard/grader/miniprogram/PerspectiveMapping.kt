package com.answercard.grader.miniprogram

import kotlin.math.abs

data class PerspectivePoint(val x: Double, val y: Double)

/**
 * 3x3 homography from 4 point correspondences (order lu, ru, rd, ld).
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
            require(source.size == 4 && target.size == 4) { "exactly 4 correspondences required" }
            val forward = solve(source, target) ?: return null
            val backward = solve(target, source) ?: return null
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
    }
}
