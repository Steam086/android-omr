package com.answercard.grader.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.answercard.grader.template.CardLayout
import kotlin.math.roundToInt

object PerspectiveWarp {
    fun normalize(
        bitmap: Bitmap,
        corners: DetectedCorners,
        layout: CardLayout,
        scale: Float = 3f,
    ): Bitmap {
        require(scale > 0f) { "scale must be greater than zero" }

        val width = (layout.width * scale).roundToInt()
        val height = (layout.height * scale).roundToInt()
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.eraseColor(Color.WHITE)

        val transform = QuadTransform.from(
            Point(corners.topLeft.x.toFloat(), corners.topLeft.y.toFloat()),
            Point((corners.topRight.x + corners.topRight.width - 1).toFloat(), corners.topRight.y.toFloat()),
            Point(
                (corners.bottomRight.x + corners.bottomRight.width - 1).toFloat(),
                (corners.bottomRight.y + corners.bottomRight.height - 1).toFloat(),
            ),
            Point(corners.bottomLeft.x.toFloat(), (corners.bottomLeft.y + corners.bottomLeft.height - 1).toFloat()),
        )

        for (y in 0 until height) {
            val v = if (height == 1) 0f else y.toFloat() / (height - 1).toFloat()
            for (x in 0 until width) {
                val u = if (width == 1) 0f else x.toFloat() / (width - 1).toFloat()
                val source = transform.map(u, v)
                output.setPixel(x, y, sampleNearest(bitmap, source.x, source.y))
            }
        }
        return output
    }

    private fun sampleNearest(bitmap: Bitmap, x: Float, y: Float): Int {
        val safeX = x.roundToInt().coerceIn(0, bitmap.width - 1)
        val safeY = y.roundToInt().coerceIn(0, bitmap.height - 1)
        return bitmap.getPixel(safeX, safeY)
    }

    private data class Point(val x: Float, val y: Float)

    private data class QuadTransform(
        val a: Float,
        val b: Float,
        val c: Float,
        val d: Float,
        val e: Float,
        val f: Float,
        val g: Float,
        val h: Float,
    ) {
        fun map(u: Float, v: Float): Point {
            val denominator = g * u + h * v + 1f
            return Point(
                (a * u + b * v + c) / denominator,
                (d * u + e * v + f) / denominator,
            )
        }

        companion object {
            fun from(topLeft: Point, topRight: Point, bottomRight: Point, bottomLeft: Point): QuadTransform {
                val dx1 = topRight.x - bottomRight.x
                val dx2 = bottomLeft.x - bottomRight.x
                val dx3 = topLeft.x - topRight.x + bottomRight.x - bottomLeft.x
                val dy1 = topRight.y - bottomRight.y
                val dy2 = bottomLeft.y - bottomRight.y
                val dy3 = topLeft.y - topRight.y + bottomRight.y - bottomLeft.y
                val determinant = dx1 * dy2 - dx2 * dy1
                val g = if (determinant == 0f) 0f else (dx3 * dy2 - dx2 * dy3) / determinant
                val h = if (determinant == 0f) 0f else (dx1 * dy3 - dx3 * dy1) / determinant

                return QuadTransform(
                    a = topRight.x - topLeft.x + g * topRight.x,
                    b = bottomLeft.x - topLeft.x + h * bottomLeft.x,
                    c = topLeft.x,
                    d = topRight.y - topLeft.y + g * topRight.y,
                    e = bottomLeft.y - topLeft.y + h * bottomLeft.y,
                    f = topLeft.y,
                    g = g,
                    h = h,
                )
            }
        }
    }
}
