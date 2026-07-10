package com.answercard.grader.miniprogram

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class FrameQualityThresholds(
    val minimumNormalizedLaplacianVariance: Double,
    val maximumHighlightClipRatio: Double,
    val maximumShadowClipRatio: Double,
    val minimumDynamicRange: Int,
) {
    companion object {
        val PRE_OMR = FrameQualityThresholds(
            minimumNormalizedLaplacianVariance = 10.0,
            maximumHighlightClipRatio = 0.985,
            maximumShadowClipRatio = 0.985,
            minimumDynamicRange = 24,
        )
        val CARD_ROI = FrameQualityThresholds(
            minimumNormalizedLaplacianVariance = 100.0,
            maximumHighlightClipRatio = 0.970,
            maximumShadowClipRatio = 0.900,
            minimumDynamicRange = 36,
        )
    }
}

data class FrameQualityMetrics(
    val sampleCount: Int,
    val rawLaplacianVariance: Double,
    val normalizedLaplacianVariance: Double,
    val highlightClipRatio: Double,
    val shadowClipRatio: Double,
    val lowPercentile: Int,
    val median: Int,
    val highPercentile: Int,
    val dynamicRange: Int,
    val regionWidth: Int,
    val regionHeight: Int,
) {
    val qualityScore: Double
        get() = normalizedLaplacianVariance + dynamicRange * 0.5 -
            highlightClipRatio * 100.0 - shadowClipRatio * 60.0

    fun debugInfo(prefix: String): List<String> = listOf(
        "${prefix}QualitySamples=$sampleCount",
        "${prefix}QualityRegion=${regionWidth}x$regionHeight",
        "${prefix}LaplacianVariance=${format(rawLaplacianVariance)}",
        "${prefix}NormalizedLaplacianVariance=${format(normalizedLaplacianVariance)}",
        "${prefix}HighlightClipRatio=${format(highlightClipRatio)}",
        "${prefix}ShadowClipRatio=${format(shadowClipRatio)}",
        "${prefix}LumaPercentiles=$lowPercentile,$median,$highPercentile",
        "${prefix}DynamicRange=$dynamicRange",
        "${prefix}QualityScore=${format(qualityScore)}",
    )

    private fun format(value: Double): String = "%.3f".format(Locale.US, value)
}

data class FrameQualityDecision(
    val accepted: Boolean,
    val rejectionReason: ScanRejectionReason?,
    val metrics: FrameQualityMetrics,
) {
    fun debugInfo(prefix: String): List<String> =
        metrics.debugInfo(prefix) +
            "${prefix}QualityAccepted=$accepted" +
            "${prefix}QualityRejection=${rejectionReason?.name ?: "none"}"
}

class FrameQualityEvaluator(
    private val thresholds: FrameQualityThresholds,
) {
    fun evaluate(frame: MiniProgramFrame, anchors: MiniProgramAnchors? = null): FrameQualityDecision {
        val region = SampleRegion.from(frame, anchors)
        val histogram = IntArray(256)
        var sampleCount = 0
        var shadowCount = 0
        var highlightCount = 0
        var laplacianSum = 0.0
        var laplacianSquares = 0.0
        var laplacianCount = 0
        val sampleStep = max(1, min(region.width, region.height) / 480)
        var row = region.top
        while (row <= region.bottom) {
            val span = region.spanAt(row)
            var column = span.first
            while (column <= span.last) {
                val value = frame[row, column].coerceIn(0, 255)
                histogram[value] += 1
                sampleCount += 1
                if (value <= SHADOW_CLIP_VALUE) shadowCount += 1
                if (value >= HIGHLIGHT_CLIP_VALUE) highlightCount += 1
                if (
                    row > region.top && row < region.bottom &&
                    column > span.first && column < span.last
                ) {
                    val laplacian = 4 * value -
                        frame[row - 1, column] - frame[row + 1, column] -
                        frame[row, column - 1] - frame[row, column + 1]
                    laplacianSum += laplacian
                    laplacianSquares += laplacian.toDouble() * laplacian
                    laplacianCount += 1
                }
                column += sampleStep
            }
            row += sampleStep
        }

        val rawVariance = if (laplacianCount == 0) {
            0.0
        } else {
            val mean = laplacianSum / laplacianCount
            laplacianSquares / laplacianCount - mean * mean
        }
        val shortSide = min(region.width, region.height).coerceAtLeast(1)
        val normalization = (REFERENCE_SHORT_SIDE.toDouble() / shortSide)
            .let { it * it }
            .coerceIn(0.25, 4.0)
        val metrics = FrameQualityMetrics(
            sampleCount = sampleCount,
            rawLaplacianVariance = rawVariance,
            normalizedLaplacianVariance = rawVariance * normalization,
            highlightClipRatio = highlightCount.toDouble() / sampleCount.coerceAtLeast(1),
            shadowClipRatio = shadowCount.toDouble() / sampleCount.coerceAtLeast(1),
            lowPercentile = percentile(histogram, sampleCount, 0.02),
            median = percentile(histogram, sampleCount, 0.50),
            highPercentile = percentile(histogram, sampleCount, 0.98),
            dynamicRange = percentile(histogram, sampleCount, 0.98) - percentile(histogram, sampleCount, 0.02),
            regionWidth = region.width,
            regionHeight = region.height,
        )
        if (sampleCount < MINIMUM_SAMPLE_COUNT) return FrameQualityDecision(true, null, metrics)
        val exposureRejected = metrics.highlightClipRatio > thresholds.maximumHighlightClipRatio ||
            metrics.shadowClipRatio > thresholds.maximumShadowClipRatio ||
            metrics.dynamicRange < thresholds.minimumDynamicRange
        if (exposureRejected) {
            return FrameQualityDecision(false, ScanRejectionReason.RETAKE_EXPOSURE, metrics)
        }
        if (metrics.normalizedLaplacianVariance < thresholds.minimumNormalizedLaplacianVariance) {
            return FrameQualityDecision(false, ScanRejectionReason.RETAKE_BLUR, metrics)
        }
        return FrameQualityDecision(true, null, metrics)
    }

    private fun percentile(histogram: IntArray, count: Int, fraction: Double): Int {
        if (count <= 0) return 0
        val target = (count * fraction).roundToInt().coerceIn(1, count)
        var cumulative = 0
        histogram.forEachIndexed { value, occurrences ->
            cumulative += occurrences
            if (cumulative >= target) return value
        }
        return 255
    }

    private data class SampleRegion(
        val top: Int,
        val bottom: Int,
        val leftTop: Double,
        val leftBottom: Double,
        val rightTop: Double,
        val rightBottom: Double,
    ) {
        val width: Int get() = max(rightTop - leftTop, rightBottom - leftBottom).roundToInt().coerceAtLeast(1)
        val height: Int get() = (bottom - top + 1).coerceAtLeast(1)

        fun spanAt(row: Int): IntRange {
            val ratio = if (bottom == top) 0.0 else (row - top).toDouble() / (bottom - top)
            val left = interpolate(leftTop, leftBottom, ratio).roundToInt()
            val right = interpolate(rightTop, rightBottom, ratio).roundToInt()
            return min(left, right)..max(left, right)
        }

        private fun interpolate(start: Double, end: Double, ratio: Double): Double = start + (end - start) * ratio

        companion object {
            fun from(frame: MiniProgramFrame, anchors: MiniProgramAnchors?): SampleRegion {
                if (anchors == null) {
                    return SampleRegion(
                        top = 1.coerceAtMost(frame.height - 1),
                        bottom = (frame.height - 2).coerceAtLeast(0),
                        leftTop = 1.0.coerceAtMost((frame.width - 1).toDouble()),
                        leftBottom = 1.0.coerceAtMost((frame.width - 1).toDouble()),
                        rightTop = (frame.width - 2).coerceAtLeast(0).toDouble(),
                        rightBottom = (frame.width - 2).coerceAtLeast(0).toDouble(),
                    )
                }
                val top = min(anchors.lu.point.row, anchors.ru.point.row).coerceIn(1, frame.height - 2)
                val bottom = max(anchors.ld.point.row, anchors.rd.point.row).coerceIn(top, frame.height - 2)
                return SampleRegion(
                    top = top,
                    bottom = bottom,
                    leftTop = anchors.lu.point.column.coerceIn(1, frame.width - 2).toDouble(),
                    leftBottom = anchors.ld.point.column.coerceIn(1, frame.width - 2).toDouble(),
                    rightTop = anchors.ru.point.column.coerceIn(1, frame.width - 2).toDouble(),
                    rightBottom = anchors.rd.point.column.coerceIn(1, frame.width - 2).toDouble(),
                )
            }
        }
    }

    private companion object {
        const val SHADOW_CLIP_VALUE = 5
        const val HIGHLIGHT_CLIP_VALUE = 250
        const val MINIMUM_SAMPLE_COUNT = 256
        const val REFERENCE_SHORT_SIDE = 720
    }
}
