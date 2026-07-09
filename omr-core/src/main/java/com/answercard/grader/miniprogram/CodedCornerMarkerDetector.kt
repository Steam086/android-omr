package com.answercard.grader.miniprogram

import com.answercard.grader.template.CardLayout
import com.answercard.grader.template.CodedCornerMarker
import com.answercard.grader.template.CornerMarkerId
import com.answercard.grader.template.Rect
import com.answercard.grader.template.TemplateGeometry
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class CodedCornerMarkerDiagnostics(
    val detectedIds: List<CornerMarkerId>,
    val rotations: Map<CornerMarkerId, Int>,
    val bitErrors: Map<CornerMarkerId, Int>,
    val inferredIds: List<CornerMarkerId>,
    val reprojectionRms: Double?,
) {
    fun debugInfo(): List<String> = listOf(
        "codedMarkerVersion=${CodedCornerMarker.VERSION}",
        "codedMarkerIds=${detectedIds.joinToString(separator = ",")}",
        "codedMarkerRotations=${rotations.entries.joinToString(separator = ",") { "${it.key}:${it.value}" }}",
        "codedMarkerBitErrors=${bitErrors.entries.joinToString(separator = ",") { "${it.key}:${it.value}" }}",
        "codedMarkerInferred=${inferredIds.joinToString(separator = ",")}",
        "codedMarkerReprojectionRms=${reprojectionRms?.let(::format) ?: "none"}",
    )

    private fun format(value: Double): String = "%.3f".format(java.util.Locale.US, value)
}

data class CodedCornerMarkerMatchResult(
    val anchors: MiniProgramAnchors?,
    val diagnostics: CodedCornerMarkerDiagnostics,
)

/** Detects the dependency-free 6x6 coded fiducials emitted by the template renderer. */
object CodedCornerMarkerDetector {
    const val SOURCE = "coded-marker"
    private const val MIN_COMPONENT_FILL = 0.30f
    private const val MAX_COMPONENT_FILL = 0.92f
    private const val MIN_SIZE_RATIO = 0.012
    private const val MAX_SIZE_RATIO = 0.18
    private const val MAX_BOUNDING_ASPECT = 2.0
    private const val MAX_CANDIDATES = 64
    private const val MIN_DARK_BORDER_CELLS = 18
    private const val MAX_BIT_ERRORS = 2
    private const val MIN_DECODE_MARGIN = 2
    private const val MIN_UNIQUE_MARKERS = 3
    private const val MAX_ASPECT_RELATIVE_DEVIATION = 0.55
    private const val MAX_REPROJECTION_MARKER_RATIO = 0.16

    fun findAnchors(frame: MiniProgramFrame, layout: CardLayout): MiniProgramAnchors? =
        findAnchorsWithDiagnostics(frame, layout).anchors

    fun findAnchorsWithDiagnostics(
        frame: MiniProgramFrame,
        layout: CardLayout,
    ): CodedCornerMarkerMatchResult {
        val threshold = MiniProgramGeometry.threshold(frame)
        val decoded = findCandidates(frame, threshold)
        val selected = decoded
            .groupBy { it.id }
            .mapValues { (_, candidates) ->
                candidates.minWithOrNull(
                    compareBy<DecodedCandidate> { it.bitErrors }
                        .thenBy { it.borderMisses }
                        .thenByDescending { it.edgeLength },
                )!!
            }
        val emptyDiagnostics = diagnostics(selected, inferred = emptyList(), reprojectionRms = null)
        if (selected.size < MIN_UNIQUE_MARKERS) {
            return CodedCornerMarkerMatchResult(anchors = null, diagnostics = emptyDiagnostics)
        }

        val source = mutableListOf<PerspectivePoint>()
        val target = mutableListOf<PerspectivePoint>()
        selected.values.forEach { candidate ->
            val sourceCorners = TemplateGeometry.cornerMarkerRect(layout, candidate.id).corners()
            val targetCorners = candidate.targetCornersInCanonicalOrder()
            source += sourceCorners
            target += targetCorners
        }
        val mapping = PerspectiveMapping.fromCorrespondences(source, target)
            ?: return CodedCornerMarkerMatchResult(anchors = null, diagnostics = emptyDiagnostics)
        val reprojectionRms = reprojectionRms(mapping, source, target)
        val averageMarkerLength = selected.values.map { it.edgeLength }.average()
        val diagnostics = diagnostics(
            selected = selected,
            inferred = CornerMarkerId.entries.filterNot(selected::containsKey),
            reprojectionRms = reprojectionRms,
        )
        if (reprojectionRms > maxOf(2.5, averageMarkerLength * MAX_REPROJECTION_MARKER_RATIO)) {
            return CodedCornerMarkerMatchResult(anchors = null, diagnostics = diagnostics)
        }

        val centers = CornerMarkerId.entries.associateWith { id ->
            val rect = TemplateGeometry.cornerMarkerRect(layout, id)
            mapping.map(PerspectivePoint(rect.x + rect.w / 2.0, rect.y + rect.h / 2.0))
        }
        val templateCenters = TemplateGeometry.cornerMarkerCenters(layout)
        val expectedAspect = (templateCenters.ru.x - templateCenters.lu.x).toDouble() /
            (templateCenters.ld.y - templateCenters.lu.y).toDouble().coerceAtLeast(1.0)
        val actualWidth = averageDistance(
            centers.getValue(CornerMarkerId.LU),
            centers.getValue(CornerMarkerId.RU),
            centers.getValue(CornerMarkerId.LD),
            centers.getValue(CornerMarkerId.RD),
        )
        val actualHeight = averageDistance(
            centers.getValue(CornerMarkerId.LU),
            centers.getValue(CornerMarkerId.LD),
            centers.getValue(CornerMarkerId.RU),
            centers.getValue(CornerMarkerId.RD),
        )
        val actualAspect = actualWidth / actualHeight.coerceAtLeast(1.0)
        if (abs(actualAspect - expectedAspect) / expectedAspect > MAX_ASPECT_RELATIVE_DEVIATION) {
            return CodedCornerMarkerMatchResult(anchors = null, diagnostics = diagnostics)
        }

        val averageLength = averageMarkerLength.roundToInt().coerceAtLeast(1)
        fun candidate(id: CornerMarkerId, kind: MiniProgramCornerKind): MiniProgramCornerCandidate {
            val point = centers.getValue(id)
            return MiniProgramCornerCandidate(
                kind = kind,
                point = MiniProgramPoint(row = point.y.roundToInt(), column = point.x.roundToInt()),
                length = selected[id]?.edgeLength?.roundToInt() ?: averageLength,
                source = SOURCE,
            )
        }
        val lu = candidate(CornerMarkerId.LU, MiniProgramCornerKind.LU)
        val ru = candidate(CornerMarkerId.RU, MiniProgramCornerKind.RU)
        val ld = candidate(CornerMarkerId.LD, MiniProgramCornerKind.LD)
        val rd = candidate(CornerMarkerId.RD, MiniProgramCornerKind.RD)
        return CodedCornerMarkerMatchResult(
            anchors = MiniProgramAnchors(
                lu = lu,
                ld = ld,
                ru = ru,
                rd = rd,
                quadCheck = MiniProgramGeometry.isQuad(lu.point, ld.point, ru.point, rd.point),
            ),
            diagnostics = diagnostics,
        )
    }

    private fun findCandidates(frame: MiniProgramFrame, threshold: Int): List<DecodedCandidate> {
        val minimumDimension = minOf(frame.width, frame.height)
        val minimumSize = maxOf(12, (minimumDimension * MIN_SIZE_RATIO).toInt())
        val maximumSize = maxOf(minimumSize, (minimumDimension * MAX_SIZE_RATIO).toInt())
        return MiniProgramComponentScanner.scan(frame, threshold)
            .asSequence()
            .filter { it.fillRatio in MIN_COMPONENT_FILL..MAX_COMPONENT_FILL }
            .filter { it.rect.width in minimumSize..maximumSize && it.rect.height in minimumSize..maximumSize }
            .filter {
                val aspect = it.rect.width.toDouble() / it.rect.height.coerceAtLeast(1)
                aspect in (1.0 / MAX_BOUNDING_ASPECT)..MAX_BOUNDING_ASPECT
            }
            .filter { it.rect.left > 0 && it.rect.top > 0 && it.rect.right < frame.width && it.rect.bottom < frame.height }
            .sortedByDescending { it.rect.width * it.rect.height }
            .take(MAX_CANDIDATES)
            .mapNotNull { decodeCandidate(frame, threshold, it) }
            .toList()
    }

    private fun decodeCandidate(
        frame: MiniProgramFrame,
        threshold: Int,
        component: MiniProgramComponent,
    ): DecodedCandidate? {
        val quad = componentQuad(frame, threshold, component.rect) ?: return null
        val local = PerspectiveMapping.fromCorrespondences(
            source = listOf(
                PerspectivePoint(0.0, 0.0),
                PerspectivePoint(CodedCornerMarker.GRID_SIZE.toDouble(), 0.0),
                PerspectivePoint(CodedCornerMarker.GRID_SIZE.toDouble(), CodedCornerMarker.GRID_SIZE.toDouble()),
                PerspectivePoint(0.0, CodedCornerMarker.GRID_SIZE.toDouble()),
            ),
            target = quad,
        ) ?: return null
        val edgeLength = averageEdgeLength(quad)
        val sampleRadius = (edgeLength / CodedCornerMarker.GRID_SIZE * 0.18).roundToInt().coerceAtLeast(1)
        var darkBorderCells = 0
        var observedPayload = 0
        for (row in 0 until CodedCornerMarker.GRID_SIZE) {
            for (column in 0 until CodedCornerMarker.GRID_SIZE) {
                val point = local.map(PerspectivePoint(column + 0.5, row + 0.5))
                val dark = patchMean(frame, point, sampleRadius) < threshold
                val border = row == 0 || column == 0 ||
                    row == CodedCornerMarker.GRID_SIZE - 1 || column == CodedCornerMarker.GRID_SIZE - 1
                if (border && dark) darkBorderCells += 1
                if (!border) {
                    observedPayload = observedPayload shl 1
                    if (dark) observedPayload = observedPayload or 1
                }
            }
        }
        if (darkBorderCells < MIN_DARK_BORDER_CELLS) return null

        val decodes = buildList {
            CornerMarkerId.entries.forEach { id ->
                for (rotation in 0 until 4) {
                    val expected = CodedCornerMarker.rotateClockwise(CodedCornerMarker.payload(id), rotation)
                    add(Decode(id, rotation, CodedCornerMarker.hammingDistance(observedPayload, expected)))
                }
            }
        }.sortedBy { it.bitErrors }
        val best = decodes.first()
        val second = decodes[1]
        if (best.bitErrors > MAX_BIT_ERRORS || second.bitErrors - best.bitErrors < MIN_DECODE_MARGIN) return null
        return DecodedCandidate(
            id = best.id,
            rotation = best.rotation,
            bitErrors = best.bitErrors,
            borderMisses = 20 - darkBorderCells,
            quad = quad,
            edgeLength = edgeLength,
        )
    }

    private fun componentQuad(
        frame: MiniProgramFrame,
        threshold: Int,
        rect: MiniProgramComponentRect,
    ): List<PerspectivePoint>? {
        var lu: PerspectivePoint? = null
        var ru: PerspectivePoint? = null
        var rd: PerspectivePoint? = null
        var ld: PerspectivePoint? = null
        var minSum = Double.POSITIVE_INFINITY
        var maxSum = Double.NEGATIVE_INFINITY
        var minDifference = Double.POSITIVE_INFINITY
        var maxDifference = Double.NEGATIVE_INFINITY
        for (row in rect.top until rect.bottom) {
            for (column in rect.left until rect.right) {
                if (frame[row, column] >= threshold) continue
                val point = PerspectivePoint(column.toDouble(), row.toDouble())
                val sum = column + row.toDouble()
                val difference = column - row.toDouble()
                if (sum < minSum) {
                    minSum = sum
                    lu = point
                }
                if (difference > maxDifference) {
                    maxDifference = difference
                    ru = point
                }
                if (sum > maxSum) {
                    maxSum = sum
                    rd = point
                }
                if (difference < minDifference) {
                    minDifference = difference
                    ld = point
                }
            }
        }
        val points = listOfNotNull(lu, ru, rd, ld)
        if (points.size != 4 || points.distinct().size != 4) return null
        if (averageEdgeLength(points) < 8.0) return null
        return points
    }

    private fun patchMean(frame: MiniProgramFrame, point: PerspectivePoint, radius: Int): Double {
        val centerRow = point.y.roundToInt()
        val centerColumn = point.x.roundToInt()
        var sum = 0L
        var count = 0
        for (row in centerRow - radius..centerRow + radius) {
            for (column in centerColumn - radius..centerColumn + radius) {
                sum += frame[row, column]
                count += 1
            }
        }
        return sum.toDouble() / count.coerceAtLeast(1)
    }

    private fun diagnostics(
        selected: Map<CornerMarkerId, DecodedCandidate>,
        inferred: List<CornerMarkerId>,
        reprojectionRms: Double?,
    ): CodedCornerMarkerDiagnostics = CodedCornerMarkerDiagnostics(
        detectedIds = selected.keys.sortedBy { it.ordinal },
        rotations = selected.mapValues { it.value.rotation },
        bitErrors = selected.mapValues { it.value.bitErrors },
        inferredIds = inferred,
        reprojectionRms = reprojectionRms,
    )

    private fun reprojectionRms(
        mapping: PerspectiveMapping,
        source: List<PerspectivePoint>,
        target: List<PerspectivePoint>,
    ): Double = sqrt(source.indices.sumOf { index ->
        val projected = mapping.map(source[index])
        val dx = projected.x - target[index].x
        val dy = projected.y - target[index].y
        dx * dx + dy * dy
    } / source.size)

    private fun Rect.corners(): List<PerspectivePoint> = listOf(
        PerspectivePoint(x.toDouble(), y.toDouble()),
        PerspectivePoint((x + w).toDouble(), y.toDouble()),
        PerspectivePoint((x + w).toDouble(), (y + h).toDouble()),
        PerspectivePoint(x.toDouble(), (y + h).toDouble()),
    )

    private fun averageEdgeLength(points: List<PerspectivePoint>): Double =
        points.indices.map { index ->
            val first = points[index]
            val second = points[(index + 1) % points.size]
            hypot(first.x - second.x, first.y - second.y)
        }.average()

    private fun averageDistance(
        firstA: PerspectivePoint,
        firstB: PerspectivePoint,
        secondA: PerspectivePoint,
        secondB: PerspectivePoint,
    ): Double = (hypot(firstA.x - firstB.x, firstA.y - firstB.y) +
        hypot(secondA.x - secondB.x, secondA.y - secondB.y)) / 2.0

    private data class Decode(
        val id: CornerMarkerId,
        val rotation: Int,
        val bitErrors: Int,
    )

    private data class DecodedCandidate(
        val id: CornerMarkerId,
        val rotation: Int,
        val bitErrors: Int,
        val borderMisses: Int,
        val quad: List<PerspectivePoint>,
        val edgeLength: Double,
    ) {
        fun targetCornersInCanonicalOrder(): List<PerspectivePoint> =
            quad.indices.map { canonicalIndex -> quad[(canonicalIndex + rotation) % quad.size] }
    }
}
