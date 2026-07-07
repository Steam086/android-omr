package com.answercard.grader.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.answercard.grader.template.CardLayout
import org.opencv.core.Rect
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.ln

data class DetectedCorners(
    val topLeft: Rect,
    val topRight: Rect,
    val bottomLeft: Rect,
    val bottomRight: Rect,
)

object CornerDetector {
    private const val MAX_SEARCH_COMPONENTS = 18

    fun detect(bitmap: Bitmap): DetectedCorners? =
        detect(bitmap, expectedAspect = null)

    fun detect(bitmap: Bitmap, layout: CardLayout): DetectedCorners? =
        detect(bitmap, expectedAspect = layout.width / layout.height.coerceAtLeast(1f))

    private fun detect(bitmap: Bitmap, expectedAspect: Float?): DetectedCorners? {
        val dark = darkMask(bitmap)
        val thick = thickDarkMask(dark, bitmap.width, bitmap.height)
        val components = findComponents(thick, bitmap.width, bitmap.height)
            .filter { it.count >= 40 }
            .filter { it.rect.width >= 8 && it.rect.height >= 8 }
            .filter { it.fillRatio in 0.16f..0.74f }
            .sortedByDescending { it.count }
            .take(MAX_SEARCH_COMPONENTS)

        if (components.size < 4) return null
        return selectBestCorners(components, bitmap.width, bitmap.height, expectedAspect)
    }

    private fun selectBestCorners(
        components: List<Component>,
        imageWidth: Int,
        imageHeight: Int,
        expectedAspect: Float?,
    ): DetectedCorners? {
        var best: Pair<Float, DetectedCorners>? = null
        for (a in 0 until components.size - 3) {
            for (b in a + 1 until components.size - 2) {
                for (c in b + 1 until components.size - 1) {
                    for (d in c + 1 until components.size) {
                        val selected = orderCorners(listOf(components[a], components[b], components[c], components[d]))
                            ?: continue
                        val corners = cornersFrom(selected)
                        val score = scoreCorners(selected, corners, imageWidth, imageHeight, expectedAspect)
                            ?: continue
                        if (best == null || score < best.first) {
                            best = score to corners
                        }
                    }
                }
            }
        }
        return best?.second
    }

    private fun orderCorners(components: List<Component>): List<Component>? {
        val topLeft = components.minBy { it.centerX + it.centerY }
        val topRight = components.maxBy { it.centerX - it.centerY }
        val bottomRight = components.maxBy { it.centerX + it.centerY }
        val bottomLeft = components.minBy { it.centerX - it.centerY }
        val selected = listOf(topLeft, topRight, bottomRight, bottomLeft)
        return if (selected.distinct().size == 4) selected else null
    }

    private fun cornersFrom(components: List<Component>): DetectedCorners {
        val topLeft = components[0].rect
        val topRight = components[1].rect
        val bottomRight = components[2].rect
        val bottomLeft = components[3].rect
        return DetectedCorners(
            topLeft = Rect(topLeft.x, topLeft.y, topLeft.width, topLeft.height),
            topRight = Rect(topRight.x, topRight.y, topRight.width, topRight.height),
            bottomLeft = Rect(bottomLeft.x, bottomLeft.y, bottomLeft.width, bottomLeft.height),
            bottomRight = Rect(bottomRight.x, bottomRight.y, bottomRight.width, bottomRight.height),
        )
    }

    private fun scoreCorners(
        components: List<Component>,
        corners: DetectedCorners,
        imageWidth: Int,
        imageHeight: Int,
        expectedAspect: Float?,
    ): Float? {
        val left = corners.topLeft.x.toFloat()
        val top = corners.topLeft.y.toFloat()
        val right = (corners.topRight.x + corners.topRight.width).toFloat()
        val bottom = (corners.bottomLeft.y + corners.bottomLeft.height).toFloat()
        val width = right - left
        val height = bottom - top
        if (width < imageWidth * 0.18f || height < imageHeight * 0.12f) return null

        val aspect = width / height.coerceAtLeast(1f)
        if (expectedAspect != null && aspect !in expectedAspect * 0.55f..expectedAspect * 1.55f) return null
        if (expectedAspect == null && aspect !in 0.8f..3.4f) return null

        val minCornerSize = minOf(width, height) * 0.045f
        if (components.any { minOf(it.rect.width, it.rect.height) < minCornerSize }) return null

        val maxArea = components.maxOf { it.count }.toFloat()
        val minArea = components.minOf { it.count }.toFloat()
        if (minArea < maxArea * 0.45f) return null

        val topSkew = abs(corners.topLeft.y - corners.topRight.y) / height
        val bottomSkew = abs(
            (corners.bottomLeft.y + corners.bottomLeft.height) -
                (corners.bottomRight.y + corners.bottomRight.height),
        ) / height
        val leftSkew = abs(corners.topLeft.x - corners.bottomLeft.x) / width
        val rightSkew = abs(
            (corners.topRight.x + corners.topRight.width) -
                (corners.bottomRight.x + corners.bottomRight.width),
        ) / width
        if (topSkew + bottomSkew + leftSkew + rightSkew > 0.65f) return null

        val aspectPenalty = expectedAspect?.let { abs(ln((aspect / it).coerceAtLeast(0.01f))) } ?: 0f
        val areaReward = width * height / (imageWidth * imageHeight).coerceAtLeast(1)
        val consistencyPenalty = 1f - minArea / maxArea
        return aspectPenalty + consistencyPenalty * 0.3f - areaReward * 0.08f
    }

    private fun darkMask(bitmap: Bitmap): BooleanArray {
        val grays = IntArray(bitmap.width * bitmap.height)
        val histogram = IntArray(256)
        var i = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val gray = gray(bitmap.getPixel(x, y))
                grays[i] = gray
                histogram[gray] += 1
                i += 1
            }
        }
        val threshold = otsuThreshold(histogram, grays.size).coerceIn(45, 170)
        return BooleanArray(grays.size) { grays[it] < threshold }
    }

    private fun thickDarkMask(dark: BooleanArray, width: Int, height: Int): BooleanArray {
        val integral = IntArray((width + 1) * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0
            for (x in 0 until width) {
                if (dark[y * width + x]) rowSum += 1
                val integralIndex = (y + 1) * (width + 1) + (x + 1)
                integral[integralIndex] = integral[y * (width + 1) + (x + 1)] + rowSum
            }
        }

        val radius = (minOf(width, height) * 0.008f).toInt().coerceIn(3, 10)
        val output = BooleanArray(width * height)
        for (y in radius until height - radius) {
            for (x in radius until width - radius) {
                if (!dark[y * width + x]) continue
                val x0 = x - radius
                val y0 = y - radius
                val x1 = x + radius + 1
                val y1 = y + radius + 1
                val count = sum(integral, width + 1, x0, y0, x1, y1)
                val area = (x1 - x0) * (y1 - y0)
                output[y * width + x] = count >= area * 0.72f
            }
        }
        return output
    }

    private fun sum(integral: IntArray, stride: Int, x0: Int, y0: Int, x1: Int, y1: Int): Int =
        integral[y1 * stride + x1] -
            integral[y0 * stride + x1] -
            integral[y1 * stride + x0] +
            integral[y0 * stride + x0]

    private fun findComponents(mask: BooleanArray, width: Int, height: Int): List<Component> {
        val visited = BooleanArray(mask.size)
        val components = mutableListOf<Component>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (!visited[index] && mask[index]) {
                    components += collectComponent(mask, width, height, x, y, visited)
                } else {
                    visited[index] = true
                }
            }
        }
        return components
    }

    private fun collectComponent(
        mask: BooleanArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        visited: BooleanArray,
    ): Component {
        var minX = startX
        var minY = startY
        var maxX = startX
        var maxY = startY
        var count = 0
        val queue = ArrayDeque<Int>()
        queue.add(startY * width + startX)
        visited[startY * width + startX] = true
        while (!queue.isEmpty()) {
            val index = queue.removeFirst()
            val x = index % width
            val y = index / width
            count += 1
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
            enqueue(mask, width, height, x - 1, y, visited, queue)
            enqueue(mask, width, height, x + 1, y, visited, queue)
            enqueue(mask, width, height, x, y - 1, visited, queue)
            enqueue(mask, width, height, x, y + 1, visited, queue)
        }
        return Component(Rect(minX, minY, maxX - minX + 1, maxY - minY + 1), count)
    }

    private fun enqueue(
        mask: BooleanArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        visited: BooleanArray,
        queue: ArrayDeque<Int>,
    ) {
        if (x !in 0 until width || y !in 0 until height) return
        val index = y * width + x
        if (visited[index]) return
        visited[index] = true
        if (mask[index]) queue.add(index)
    }

    private fun otsuThreshold(histogram: IntArray, total: Int): Int {
        var sum = 0L
        for (i in histogram.indices) sum += i * histogram[i].toLong()

        var sumB = 0L
        var weightB = 0
        var maxVariance = -1.0
        var threshold = 95
        for (i in histogram.indices) {
            weightB += histogram[i]
            if (weightB == 0) continue
            val weightF = total - weightB
            if (weightF == 0) break
            sumB += i * histogram[i].toLong()
            val meanB = sumB.toDouble() / weightB
            val meanF = (sum - sumB).toDouble() / weightF
            val variance = weightB.toDouble() * weightF.toDouble() * (meanB - meanF) * (meanB - meanF)
            if (variance > maxVariance) {
                maxVariance = variance
                threshold = i
            }
        }
        return threshold
    }

    private fun gray(color: Int): Int =
        (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000

    private data class Component(
        val rect: Rect,
        val count: Int,
    ) {
        val centerX: Float get() = rect.x + rect.width / 2f
        val centerY: Float get() = rect.y + rect.height / 2f
        val fillRatio: Float get() = count.toFloat() / (rect.width * rect.height).coerceAtLeast(1)
    }
}
