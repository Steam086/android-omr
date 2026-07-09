package com.answercard.grader.miniprogram

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SolidCornerMarkerDiagnostics(
    val threshold: Int,
    val componentCount: Int,
    val rawCandidateCount: Int,
    val localizedCandidateCount: Int,
    val candidateSummaries: List<String>,
    val selectedCenters: String?,
) {
    fun debugInfo(): List<String> = listOf(
        "solidMarkerThreshold=$threshold",
        "solidMarkerComponentCount=$componentCount",
        "solidMarkerRawCandidates=$rawCandidateCount",
        "solidMarkerLocalizedCandidates=$localizedCandidateCount",
        "solidMarkerCandidates=${candidateSummaries.joinToString(separator = ";")}",
        "solidMarkerSelected=${selectedCenters ?: "none"}",
    )
}

data class SolidCornerMarkerMatchResult(
    val anchors: MiniProgramAnchors?,
    val diagnostics: SolidCornerMarkerDiagnostics,
)

object SolidCornerMarkerDetector {
    const val SOURCE = "solid-marker"
    private const val MIN_FILL_RATIO = 0.85f
    private const val MIN_SIZE_RATIO = 0.012

    // A headerless single-band card renders 192 template units tall, so its 26-unit
    // markers are about 13.5% of the short side. This upper bound leaves perspective headroom.
    private const val MAX_SIZE_RATIO = 0.16
    private const val MAX_BOX_ASPECT_DEVIATION = 0.35
    private const val MAX_CANDIDATES_PER_CORNER = 8
    private const val MAX_QUAD_ASPECT_RELATIVE_DEVIATION = 0.45
    private const val MAX_SIZE_SPREAD = 2.0
    private const val HIGH_CONFIDENCE_SIZE_RATIO = 0.70

    fun findAnchors(frame: MiniProgramFrame, expectedAspectRatio: Double? = null): MiniProgramAnchors? =
        findAnchorsWithDiagnostics(frame, expectedAspectRatio).anchors

    fun findAnchorsWithDiagnostics(
        frame: MiniProgramFrame,
        expectedAspectRatio: Double? = null,
    ): SolidCornerMarkerMatchResult {
        val threshold = MiniProgramGeometry.threshold(frame)
        val minSize = maxOf(8, (minOf(frame.width, frame.height) * MIN_SIZE_RATIO).toInt())
        val maxSize = (minOf(frame.width, frame.height) * MAX_SIZE_RATIO).toInt()
        val components = MiniProgramComponentScanner.scan(frame, threshold)
        val rawCandidates = components
            .filter { it.rect.width in minSize..maxSize && it.rect.height in minSize..maxSize }
            .filter {
                val aspect = it.rect.width.toDouble() / it.rect.height.coerceAtLeast(1)
                abs(aspect - 1.0) <= MAX_BOX_ASPECT_DEVIATION
            }
            .filter { it.fillRatio >= MIN_FILL_RATIO }
            .filter {
                it.rect.left > 0 && it.rect.top > 0 &&
                    it.rect.right < frame.width && it.rect.bottom < frame.height
            }
        val localizedCandidates = if (rawCandidates.size < 8) {
            findLocalizedDenseSquareCandidates(
                frame = frame,
                threshold = threshold,
                rawCandidates = rawCandidates,
                minSize = minSize,
                maxSize = maxSize,
                expectedAspectRatio = expectedAspectRatio ?: 1.6,
            )
        } else {
            emptyList()
        }
        val candidates = localizedCandidates.takeIf { it.size >= 4 } ?: rawCandidates

        fun summary(component: MiniProgramComponent): String {
            val rect = component.rect
            return "${rect.left},${rect.top},${rect.width}x${rect.height}," +
                "${"%.2f".format(Locale.US, component.fillRatio)}"
        }
        fun diagnostics(selectedCenters: String? = null) = SolidCornerMarkerDiagnostics(
            threshold = threshold,
            componentCount = components.size,
            rawCandidateCount = rawCandidates.size,
            localizedCandidateCount = localizedCandidates.size,
            candidateSummaries = candidates
                .sortedByDescending { minOf(it.rect.width, it.rect.height) }
                .take(8)
                .map(::summary),
            selectedCenters = selectedCenters,
        )
        if (candidates.size < 4) {
            return SolidCornerMarkerMatchResult(anchors = null, diagnostics = diagnostics())
        }

        val lastRow = frame.height - 1.0
        val lastColumn = frame.width - 1.0
        val luChoices = nearest(candidates, row = 0.0, column = 0.0)
        val ruChoices = nearest(candidates, row = 0.0, column = lastColumn)
        val ldChoices = nearest(candidates, row = lastRow, column = 0.0)
        val rdChoices = nearest(candidates, row = lastRow, column = lastColumn)

        var best: MiniProgramAnchors? = null
        var bestScore = Double.MAX_VALUE
        for (lu in luChoices) for (ru in ruChoices) for (ld in ldChoices) for (rd in rdChoices) {
            val four = listOf(lu, ru, ld, rd)
            if (four.distinct().size != 4) continue
            val luPoint = lu.toAnchorPoint()
            val ruPoint = ru.toAnchorPoint()
            val ldPoint = ld.toAnchorPoint()
            val rdPoint = rd.toAnchorPoint()
            if (luPoint.column >= ruPoint.column || ldPoint.column >= rdPoint.column) continue
            if (luPoint.row >= ldPoint.row || ruPoint.row >= rdPoint.row) continue

            val sizes = four.map { minOf(it.rect.width, it.rect.height).toDouble() }
            val spread = sizes.max() / sizes.min()
            if (spread > MAX_SIZE_SPREAD) continue

            val width = (distance(luPoint, ruPoint) + distance(ldPoint, rdPoint)) / 2.0
            val height = (distance(luPoint, ldPoint) + distance(ruPoint, rdPoint)) / 2.0
            if (height <= 0.0) continue
            val aspect = width / height
            val aspectDeviation = expectedAspectRatio?.let { abs(aspect - it) / it } ?: 0.0
            if (aspectDeviation > MAX_QUAD_ASPECT_RELATIVE_DEVIATION) continue

            val areaRatio = quadrilateralArea(luPoint, ruPoint, rdPoint, ldPoint) /
                (frame.width.toDouble() * frame.height)
            if (areaRatio < 0.12) continue
            val score = aspectDeviation * 3.0 + (spread - 1.0) * 0.25 - areaRatio * 1.5
            if (score < bestScore) {
                bestScore = score
                best = MiniProgramAnchors(
                    lu = lu.toCandidate(MiniProgramCornerKind.LU),
                    ld = ld.toCandidate(MiniProgramCornerKind.LD),
                    ru = ru.toCandidate(MiniProgramCornerKind.RU),
                    rd = rd.toCandidate(MiniProgramCornerKind.RD),
                    quadCheck = MiniProgramGeometry.isQuad(luPoint, ldPoint, ruPoint, rdPoint),
                )
            }
        }
        val selectedCenters = best?.let { anchors ->
            "${anchors.lu.point.column},${anchors.lu.point.row}|" +
                "${anchors.ru.point.column},${anchors.ru.point.row}|" +
                "${anchors.ld.point.column},${anchors.ld.point.row}|" +
                "${anchors.rd.point.column},${anchors.rd.point.row}"
        }
        return SolidCornerMarkerMatchResult(anchors = best, diagnostics = diagnostics(selectedCenters))
    }

    private fun nearest(
        candidates: List<MiniProgramComponent>,
        row: Double,
        column: Double,
    ): List<MiniProgramComponent> = candidates.sortedBy { component ->
        val dr = component.centroidRow - row
        val dc = component.centroidColumn - column
        dr * dr + dc * dc
    }.take(MAX_CANDIDATES_PER_CORNER)

    /**
     * Recovers solid markers swallowed by a large connected dark background. Independent markers
     * establish their approximate size and missing-corner positions; integral images then search
     * only those neighborhoods for a square whose quadrants are dense and genuinely near-black.
     */
    private fun findLocalizedDenseSquareCandidates(
        frame: MiniProgramFrame,
        threshold: Int,
        rawCandidates: List<MiniProgramComponent>,
        minSize: Int,
        maxSize: Int,
        expectedAspectRatio: Double,
    ): List<MiniProgramComponent> {
        val referenceSize = rawCandidates.maxOfOrNull { minOf(it.rect.width, it.rect.height) }
            ?: return emptyList()
        val known = rawCandidates
            .filter { minOf(it.rect.width, it.rect.height) >= referenceSize * HIGH_CONFIDENCE_SIZE_RATIO }
            .groupBy { component -> component.cornerKind(frame) }
            .mapValues { (_, values) -> values.maxBy { minOf(it.rect.width, it.rect.height) } }
        if (known.size < 2) return emptyList()
        val predicted = predictMissingCorners(known, expectedAspectRatio)
        if (predicted.isEmpty()) return known.values.toList()

        val integralWidth = frame.width + 1
        val darkIntegral = IntArray(integralWidth * (frame.height + 1))
        val grayIntegral = LongArray(integralWidth * (frame.height + 1))
        for (row in 0 until frame.height) {
            var darkRowSum = 0
            var grayRowSum = 0L
            for (column in 0 until frame.width) {
                if (frame[row, column] < threshold) darkRowSum += 1
                grayRowSum += frame[row, column]
                darkIntegral[(row + 1) * integralWidth + column + 1] =
                    darkIntegral[row * integralWidth + column + 1] + darkRowSum
                grayIntegral[(row + 1) * integralWidth + column + 1] =
                    grayIntegral[row * integralWidth + column + 1] + grayRowSum
            }
        }
        fun darkCount(left: Int, top: Int, right: Int, bottom: Int): Int =
            darkIntegral[bottom * integralWidth + right] -
                darkIntegral[top * integralWidth + right] -
                darkIntegral[bottom * integralWidth + left] +
                darkIntegral[top * integralWidth + left]
        fun graySum(left: Int, top: Int, right: Int, bottom: Int): Long =
            grayIntegral[bottom * integralWidth + right] -
                grayIntegral[top * integralWidth + right] -
                grayIntegral[bottom * integralWidth + left] +
                grayIntegral[top * integralWidth + left]

        val searchSizes = listOf(0.55, 0.70, 0.85, 1.0)
            .map { (referenceSize * it).roundToInt().coerceIn(minSize, maxSize) }
            .distinct()
        val searchRadius = maxOf(referenceSize * 3, minOf(frame.width, frame.height) / 12)
        fun recover(point: PredictedPoint): MiniProgramComponent? {
            var best: MiniProgramComponent? = null
            var bestScore = Double.NEGATIVE_INFINITY
            searchSizes.forEach { size ->
                val half = size / 2
                val stride = maxOf(3, size / 10)
                val rowStart = maxOf(half, (point.row - searchRadius).roundToInt())
                val rowEnd = minOf(frame.height - half - 1, (point.row + searchRadius).roundToInt())
                val columnStart = maxOf(half, (point.column - searchRadius).roundToInt())
                val columnEnd = minOf(frame.width - half - 1, (point.column + searchRadius).roundToInt())
                var centerRow = rowStart
                while (centerRow <= rowEnd) {
                    var centerColumn = columnStart
                    while (centerColumn <= columnEnd) {
                        val left = centerColumn - half
                        val top = centerRow - half
                        val right = left + size
                        val bottom = top + size
                        val area = size * size
                        val innerDark = darkCount(left, top, right, bottom)
                        val innerRatio = innerDark.toDouble() / area
                        val innerMean = graySum(left, top, right, bottom).toDouble() / area
                        if (innerRatio < 0.75 || innerMean > threshold * 0.75) {
                            centerColumn += stride
                            continue
                        }

                        val middleColumn = (left + right) / 2
                        val middleRow = (top + bottom) / 2
                        val minimumQuadrantRatio = listOf(
                            darkCount(left, top, middleColumn, middleRow).toDouble() /
                                ((middleColumn - left) * (middleRow - top)).coerceAtLeast(1),
                            darkCount(middleColumn, top, right, middleRow).toDouble() /
                                ((right - middleColumn) * (middleRow - top)).coerceAtLeast(1),
                            darkCount(left, middleRow, middleColumn, bottom).toDouble() /
                                ((middleColumn - left) * (bottom - middleRow)).coerceAtLeast(1),
                            darkCount(middleColumn, middleRow, right, bottom).toDouble() /
                                ((right - middleColumn) * (bottom - middleRow)).coerceAtLeast(1),
                        ).min()
                        if (minimumQuadrantRatio < 0.60) {
                            centerColumn += stride
                            continue
                        }

                        val ringMargin = maxOf(4, size / 3)
                        val outerLeft = maxOf(0, left - ringMargin)
                        val outerTop = maxOf(0, top - ringMargin)
                        val outerRight = minOf(frame.width, right + ringMargin)
                        val outerBottom = minOf(frame.height, bottom + ringMargin)
                        val outerArea = (outerRight - outerLeft) * (outerBottom - outerTop)
                        val ringArea = outerArea - area
                        val ringDark = darkCount(outerLeft, outerTop, outerRight, outerBottom) - innerDark
                        val ringRatio = ringDark.toDouble() / ringArea.coerceAtLeast(1)
                        val contrast = innerRatio - ringRatio
                        val distance = sqrt(
                            (centerRow - point.row) * (centerRow - point.row) +
                                (centerColumn - point.column) * (centerColumn - point.column),
                        )
                        val score = innerRatio + minimumQuadrantRatio * 0.30 +
                            contrast.coerceAtLeast(0.0) * 1.5 + size.toDouble() / referenceSize +
                            ((threshold - innerMean) / threshold).coerceAtLeast(0.0) * 2.0 -
                            distance / searchRadius * 0.35
                        if (score > bestScore) {
                            bestScore = score
                            best = MiniProgramComponent(
                                rect = MiniProgramComponentRect(left, top, right, bottom),
                                area = innerDark,
                                centroidRow = centerRow.toDouble(),
                                centroidColumn = centerColumn.toDouble(),
                            )
                        }
                        centerColumn += stride
                    }
                    centerRow += stride
                }
            }
            return best
        }

        return known.values.toList() + predicted.values.mapNotNull(::recover)
    }

    private data class PredictedPoint(val column: Double, val row: Double)

    private fun MiniProgramComponent.cornerKind(frame: MiniProgramFrame): MiniProgramCornerKind = when {
        centroidRow < frame.height / 2.0 && centroidColumn < frame.width / 2.0 -> MiniProgramCornerKind.LU
        centroidRow >= frame.height / 2.0 && centroidColumn < frame.width / 2.0 -> MiniProgramCornerKind.LD
        centroidRow < frame.height / 2.0 -> MiniProgramCornerKind.RU
        else -> MiniProgramCornerKind.RD
    }

    private fun predictMissingCorners(
        known: Map<MiniProgramCornerKind, MiniProgramComponent>,
        aspectRatio: Double,
    ): Map<MiniProgramCornerKind, PredictedPoint> {
        fun point(kind: MiniProgramCornerKind): PredictedPoint? = known[kind]?.let {
            PredictedPoint(column = it.centroidColumn, row = it.centroidRow)
        }
        fun add(a: PredictedPoint, b: PredictedPoint) =
            PredictedPoint(a.column + b.column, a.row + b.row)
        fun subtract(a: PredictedPoint, b: PredictedPoint) =
            PredictedPoint(a.column - b.column, a.row - b.row)

        val missing = MiniProgramCornerKind.entries.filterNot(known::containsKey)
        if (missing.size == 1) {
            val predicted = when (missing.single()) {
                MiniProgramCornerKind.LU -> subtract(
                    add(point(MiniProgramCornerKind.RU)!!, point(MiniProgramCornerKind.LD)!!),
                    point(MiniProgramCornerKind.RD)!!,
                )
                MiniProgramCornerKind.LD -> subtract(
                    add(point(MiniProgramCornerKind.LU)!!, point(MiniProgramCornerKind.RD)!!),
                    point(MiniProgramCornerKind.RU)!!,
                )
                MiniProgramCornerKind.RU -> subtract(
                    add(point(MiniProgramCornerKind.LU)!!, point(MiniProgramCornerKind.RD)!!),
                    point(MiniProgramCornerKind.LD)!!,
                )
                MiniProgramCornerKind.RD -> subtract(
                    add(point(MiniProgramCornerKind.RU)!!, point(MiniProgramCornerKind.LD)!!),
                    point(MiniProgramCornerKind.LU)!!,
                )
            }
            return mapOf(missing.single() to predicted)
        }

        val lu = point(MiniProgramCornerKind.LU)
        val ld = point(MiniProgramCornerKind.LD)
        val ru = point(MiniProgramCornerKind.RU)
        val rd = point(MiniProgramCornerKind.RD)
        return when {
            lu != null && ru != null -> {
                val vertical = PredictedPoint(
                    column = -(ru.row - lu.row) / aspectRatio,
                    row = (ru.column - lu.column) / aspectRatio,
                )
                mapOf(
                    MiniProgramCornerKind.LD to add(lu, vertical),
                    MiniProgramCornerKind.RD to add(ru, vertical),
                )
            }
            ld != null && rd != null -> {
                val vertical = PredictedPoint(
                    column = -(rd.row - ld.row) / aspectRatio,
                    row = (rd.column - ld.column) / aspectRatio,
                )
                mapOf(
                    MiniProgramCornerKind.LU to subtract(ld, vertical),
                    MiniProgramCornerKind.RU to subtract(rd, vertical),
                )
            }
            lu != null && ld != null -> {
                val horizontal = PredictedPoint(
                    column = (ld.row - lu.row) * aspectRatio,
                    row = -(ld.column - lu.column) * aspectRatio,
                )
                mapOf(
                    MiniProgramCornerKind.RU to add(lu, horizontal),
                    MiniProgramCornerKind.RD to add(ld, horizontal),
                )
            }
            ru != null && rd != null -> {
                val horizontal = PredictedPoint(
                    column = (rd.row - ru.row) * aspectRatio,
                    row = -(rd.column - ru.column) * aspectRatio,
                )
                mapOf(
                    MiniProgramCornerKind.LU to subtract(ru, horizontal),
                    MiniProgramCornerKind.LD to subtract(rd, horizontal),
                )
            }
            else -> emptyMap()
        }
    }

    private fun quadrilateralArea(
        lu: MiniProgramPoint,
        ru: MiniProgramPoint,
        rd: MiniProgramPoint,
        ld: MiniProgramPoint,
    ): Double {
        val points = listOf(lu, ru, rd, ld)
        val twiceArea = points.indices.sumOf { index ->
            val first = points[index]
            val second = points[(index + 1) % points.size]
            first.column.toDouble() * second.row - second.column.toDouble() * first.row
        }
        return abs(twiceArea) / 2.0
    }

    private fun MiniProgramComponent.toAnchorPoint(): MiniProgramPoint =
        MiniProgramPoint(row = centroidRow.roundToInt(), column = centroidColumn.roundToInt())

    private fun MiniProgramComponent.toCandidate(kind: MiniProgramCornerKind): MiniProgramCornerCandidate =
        MiniProgramCornerCandidate(
            kind = kind,
            point = toAnchorPoint(),
            length = minOf(rect.width, rect.height),
            source = SOURCE,
        )

    private fun distance(a: MiniProgramPoint, b: MiniProgramPoint): Double {
        val dr = (a.row - b.row).toDouble()
        val dc = (a.column - b.column).toDouble()
        return sqrt(dr * dr + dc * dc)
    }
}
