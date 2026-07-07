package com.answercard.grader.miniprogram

import kotlin.math.abs

enum class MiniProgramCornerKind {
    LU,
    LD,
    RU,
    RD,
}

data class MiniProgramCornerCandidate(
    val kind: MiniProgramCornerKind,
    val point: MiniProgramPoint,
    val length: Int,
    val source: String = "component",
)

data class MiniProgramAnchors(
    val lu: MiniProgramCornerCandidate,
    val ld: MiniProgramCornerCandidate,
    val ru: MiniProgramCornerCandidate,
    val rd: MiniProgramCornerCandidate,
    val quadCheck: MiniProgramQuadCheck,
)

data class CornerAnchorMatchResult(
    val anchors: MiniProgramAnchors?,
    val diagnostics: CornerAnchorDiagnostics,
)

data class CornerAnchorDiagnostics(
    val totalScanElapsedMs: Long,
    val cornerElapsedMs: Long,
    val cornerCandidateElapsedMs: Long,
    val strongTraceElapsedMs: Long,
    val componentCandidateCount: Int,
    val templateCandidateCount: Int,
    val strongTraceCandidateCount: Int,
    val finalAnchorSource: String,
    val luCandidateCount: Int,
    val ldCandidateCount: Int,
    val ruCandidateCount: Int,
    val rdCandidateCount: Int,
    val bestLu: String,
    val bestLd: String,
    val bestRu: String,
    val bestRd: String,
    val selectedLu: String,
    val selectedLd: String,
    val selectedRu: String,
    val selectedRd: String,
) {
    fun debugInfo(): List<String> =
        listOf(
            "totalScanElapsedMs=$totalScanElapsedMs",
            "cornerElapsedMs=$cornerElapsedMs",
            "cornerCandidateElapsedMs=$cornerCandidateElapsedMs",
            "strongTraceElapsedMs=$strongTraceElapsedMs",
            "componentCandidateCount=$componentCandidateCount",
            "templateCandidateCount=$templateCandidateCount",
            "strongTraceCandidateCount=$strongTraceCandidateCount",
            "finalAnchorSource=$finalAnchorSource",
            "cornerCandidateMode=component+template-scan+strong-trace",
            "luCandidateCount=$luCandidateCount",
            "ldCandidateCount=$ldCandidateCount",
            "ruCandidateCount=$ruCandidateCount",
            "rdCandidateCount=$rdCandidateCount",
            "bestLU=$bestLu",
            "bestLD=$bestLd",
            "bestRU=$bestRu",
            "bestRD=$bestRd",
            "selectedLU=$selectedLu",
            "selectedLD=$selectedLd",
            "selectedRU=$selectedRu",
            "selectedRD=$selectedRd",
        )
}

object CornerAnchorMatcher {
    private const val DEFAULT_THRESHOLD_OFFSET = -30
    private const val BORDER_CANDIDATE_MARGIN = 3
    private const val MIN_TEMPLATE_TRACE_LENGTH = 8
    private const val MIN_STRONG_TRACE_LENGTH = 24
    private const val MAX_TRACE_BALANCE_RATIO = 3.0
    private const val LOCAL_THRESHOLD_RADIUS_SMALL = 9
    private const val LOCAL_THRESHOLD_RADIUS_LARGE = 36
    private const val LOCAL_CONTRAST_DELTA = 40
    private const val MAX_SCAN_CANDIDATES_PER_KIND = 36
    private const val MAX_ANCHOR_CHOICES_PER_KIND = 12

    fun findAnchors(
        frame: MiniProgramFrame,
        thresholdOffset: Int = DEFAULT_THRESHOLD_OFFSET,
        expectedAspectRatio: Double? = null,
    ): MiniProgramAnchors? =
        findAnchorsWithDiagnostics(frame, thresholdOffset, expectedAspectRatio).anchors

    fun findCandidatesForTest(
        frame: MiniProgramFrame,
        kind: MiniProgramCornerKind,
    ): List<MiniProgramCornerCandidate> =
        findCandidates(frame, kind, MiniProgramGeometry.threshold(frame, DEFAULT_THRESHOLD_OFFSET), includeTraceScan = false).candidates

    fun findAnchorsWithDiagnostics(
        frame: MiniProgramFrame,
        thresholdOffset: Int = DEFAULT_THRESHOLD_OFFSET,
        expectedAspectRatio: Double? = null,
    ): CornerAnchorMatchResult {
        val totalStartedAt = System.nanoTime()
        val threshold = MiniProgramGeometry.threshold(frame, thresholdOffset)
        val cornerStartedAt = System.nanoTime()
        val lu = findCandidates(frame, MiniProgramCornerKind.LU, threshold, includeTraceScan = false)
        val ld = findCandidates(frame, MiniProgramCornerKind.LD, threshold, includeTraceScan = false)
        val ru = findCandidates(frame, MiniProgramCornerKind.RU, threshold, includeTraceScan = false)
        val rd = findCandidates(frame, MiniProgramCornerKind.RD, threshold, includeTraceScan = false)
        val cornerElapsedMs = elapsedMs(cornerStartedAt)
        val searches = listOf(lu, ld, ru, rd)
        val componentAnchors = chooseAnchors(lu.candidates, ld.candidates, ru.candidates, rd.candidates, expectedAspectRatio)
        if (componentAnchors != null) {
            return CornerAnchorMatchResult(
                anchors = componentAnchors,
                diagnostics = diagnostics(
                    totalStartedAt = totalStartedAt,
                    cornerElapsedMs = cornerElapsedMs,
                    searches = searches,
                    anchors = componentAnchors,
                ),
            )
        }

        val traceStartedAt = System.nanoTime()
        val traceLu = findCandidates(frame, MiniProgramCornerKind.LU, threshold, includeTraceScan = true)
        val traceLd = findCandidates(frame, MiniProgramCornerKind.LD, threshold, includeTraceScan = true)
        val traceRu = findCandidates(frame, MiniProgramCornerKind.RU, threshold, includeTraceScan = true)
        val traceRd = findCandidates(frame, MiniProgramCornerKind.RD, threshold, includeTraceScan = true)
        val traceCornerElapsedMs = cornerElapsedMs + elapsedMs(traceStartedAt)
        val traceSearches = listOf(traceLu, traceLd, traceRu, traceRd)
        val best = chooseAnchors(
            traceLu.candidates,
            traceLd.candidates,
            traceRu.candidates,
            traceRd.candidates,
            expectedAspectRatio,
        )
        return CornerAnchorMatchResult(
            anchors = best,
            diagnostics = diagnostics(
                totalStartedAt = totalStartedAt,
                cornerElapsedMs = traceCornerElapsedMs,
                searches = traceSearches,
                anchors = best,
            ),
        )
    }

    private fun chooseAnchors(
        lu: List<MiniProgramCornerCandidate>,
        ld: List<MiniProgramCornerCandidate>,
        ru: List<MiniProgramCornerCandidate>,
        rd: List<MiniProgramCornerCandidate>,
        expectedAspectRatio: Double?,
    ): MiniProgramAnchors? {
        if (lu.isEmpty() || ld.isEmpty() || ru.isEmpty() || rd.isEmpty()) return null
        var best: MiniProgramAnchors? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (a in lu.take(MAX_ANCHOR_CHOICES_PER_KIND)) {
            for (b in ld.take(MAX_ANCHOR_CHOICES_PER_KIND)) {
                for (c in ru.take(MAX_ANCHOR_CHOICES_PER_KIND)) {
                    for (d in rd.take(MAX_ANCHOR_CHOICES_PER_KIND)) {
                        val check = MiniProgramGeometry.isQuad(a.point, b.point, c.point, d.point)
                        if (!check.accepted) continue
                        val score = anchorScore(a, b, c, d, check, expectedAspectRatio)
                        if (score > bestScore) {
                            bestScore = score
                            best = MiniProgramAnchors(a, b, c, d, check)
                        }
                    }
                }
            }
        }
        return best
    }

    private fun anchorScore(
        lu: MiniProgramCornerCandidate,
        ld: MiniProgramCornerCandidate,
        ru: MiniProgramCornerCandidate,
        rd: MiniProgramCornerCandidate,
        check: MiniProgramQuadCheck,
        expectedAspectRatio: Double?,
    ): Double {
        val minLength = minOf(lu.length, ld.length, ru.length, rd.length).toDouble()
        val averageLength = (lu.length + ld.length + ru.length + rd.length) / 4.0
        val edgeSkewPenalty = edgeSkewPenalty(lu.point, ld.point, ru.point, rd.point)
        val quadPenalty = check.diagonalRelativeDeviation +
            check.upDownRelativeDeviation +
            check.leftRightRelativeDeviation
        val aspectPenalty = expectedAspectRatio?.let { expected ->
            val actual = aspectRatio(lu.point, ld.point, ru.point, rd.point)
            relativeDeviation(actual, expected)
        } ?: 0.0
        val componentBonus = listOf(lu, ld, ru, rd).count { it.source == "component" } * 2.0

        return minLength +
            averageLength * 0.1 +
            componentBonus -
            quadPenalty * 50.0 -
            edgeSkewPenalty * 120.0 -
            aspectPenalty * 80.0
    }

    private fun edgeSkewPenalty(
        lu: MiniProgramPoint,
        ld: MiniProgramPoint,
        ru: MiniProgramPoint,
        rd: MiniProgramPoint,
    ): Double {
        val width = average(
            (ru.column - lu.column).toDouble(),
            (rd.column - ld.column).toDouble(),
        ).coerceAtLeast(1.0)
        val height = average(
            (ld.row - lu.row).toDouble(),
            (rd.row - ru.row).toDouble(),
        ).coerceAtLeast(1.0)
        return abs(lu.row - ru.row) / height +
            abs(ld.row - rd.row) / height +
            abs(lu.column - ld.column) / width +
            abs(ru.column - rd.column) / width
    }

    private fun aspectRatio(
        lu: MiniProgramPoint,
        ld: MiniProgramPoint,
        ru: MiniProgramPoint,
        rd: MiniProgramPoint,
    ): Double {
        val width = average(
            (ru.column - lu.column).toDouble(),
            (rd.column - ld.column).toDouble(),
        )
        val height = average(
            (ld.row - lu.row).toDouble(),
            (rd.row - ru.row).toDouble(),
        )
        return width / height.coerceAtLeast(1.0)
    }

    private fun relativeDeviation(a: Double, b: Double): Double {
        val average = average(a, b)
        return if (average == 0.0) Double.POSITIVE_INFINITY else abs(a - b) / average
    }

    private fun average(a: Double, b: Double): Double = (a + b) / 2.0

    private data class CandidateSearchResult(
        val kind: MiniProgramCornerKind,
        val candidates: List<MiniProgramCornerCandidate>,
        val componentCandidateCount: Int,
        val templateCandidateCount: Int,
        val strongTraceCandidateCount: Int,
        val elapsedMs: Long,
        val strongTraceElapsedMs: Long,
    )

    private fun findCandidates(
        frame: MiniProgramFrame,
        kind: MiniProgramCornerKind,
        globalThreshold: Int,
        includeTraceScan: Boolean,
    ): CandidateSearchResult {
        val startedAt = System.nanoTime()
        val large = frame.width > 288
        val template = template(kind, large)
        val localThresholdRadius = if (large) LOCAL_THRESHOLD_RADIUS_LARGE else LOCAL_THRESHOLD_RADIUS_SMALL
        val scanStep = if (large) 6 else 1
        val rowRange = when (kind) {
            MiniProgramCornerKind.LU, MiniProgramCornerKind.RU -> 10 until frame.height / 2 step scanStep
            MiniProgramCornerKind.LD, MiniProgramCornerKind.RD -> frame.height - 10 downTo frame.height / 2 + 1 step scanStep
        }
        val columnRange = when (kind) {
            MiniProgramCornerKind.LU, MiniProgramCornerKind.LD -> 10 until frame.width / 2 step scanStep
            MiniProgramCornerKind.RU, MiniProgramCornerKind.RD -> frame.width - 10 downTo frame.width / 2 + 1 step scanStep
        }
        val candidates = componentCandidates(frame, kind, globalThreshold).toMutableList()
        val componentCandidateCount = candidates.size
        var templateCandidateCount = 0
        var strongTraceCandidateCount = 0
        var strongTraceElapsedNs = 0L

        var scanCandidateCount = 0
        @Suppress("LoopWithTooManyJumpStatements")
        if (includeTraceScan) {
            for (row in rowRange) {
                for (column in columnRange) {
                    if (tooClose(candidates, row, column)) continue
                    if (!hasLocalDarkContrast(frame, row, column, localThresholdRadius)) continue
                    val traceCandidateStartedAt = System.nanoTime()
                    val threshold = localThreshold(frame, row, column, globalThreshold, localThresholdRadius)
                    val lengths = traceLengths(frame, row, column, threshold, kind)
                    val length = minOf(lengths.horizontal, lengths.vertical)
                    val matchesTemplate = matchesTemplate(frame, row, column, threshold, template) &&
                        length >= MIN_TEMPLATE_TRACE_LENGTH
                    val matchesStrongTrace = matchesStrongTraceAnchor(
                        frame = frame,
                        row = row,
                        column = column,
                        threshold = threshold,
                        kind = kind,
                        lengths = lengths,
                    )
                    strongTraceElapsedNs += System.nanoTime() - traceCandidateStartedAt
                    val point = if (matchesStrongTrace) {
                        canonicalTracePoint(frame, row, column, threshold, kind, length)
                    } else {
                        MiniProgramPoint(row, column)
                    }
                    if ((matchesTemplate || matchesStrongTrace) && !point.touchesBorder(frame)) {
                        val source = if (matchesStrongTrace) {
                            strongTraceCandidateCount += 1
                            "strong-trace"
                        } else {
                            templateCandidateCount += 1
                            "template-scan"
                        }
                        candidates += MiniProgramCornerCandidate(kind, point, length, source)
                        scanCandidateCount += 1
                        if (scanCandidateCount >= MAX_SCAN_CANDIDATES_PER_KIND) break
                    }
                }
                if (scanCandidateCount >= MAX_SCAN_CANDIDATES_PER_KIND) break
            }
        }

        val sorted = candidates
            .distinctBy { it.point }
            .sortedWith(
                compareByDescending<MiniProgramCornerCandidate> { it.length }
                    .thenBy { it.point.row }
                    .thenBy { it.point.column },
            )
        return CandidateSearchResult(
            kind = kind,
            candidates = sorted,
            componentCandidateCount = componentCandidateCount,
            templateCandidateCount = templateCandidateCount,
            strongTraceCandidateCount = strongTraceCandidateCount,
            elapsedMs = elapsedMs(startedAt),
            strongTraceElapsedMs = strongTraceElapsedNs / 1_000_000,
        )
    }

    private fun componentCandidates(
        frame: MiniProgramFrame,
        kind: MiniProgramCornerKind,
        threshold: Int,
    ): List<MiniProgramCornerCandidate> {
        val large = frame.width > 288
        val tmpl = template(kind, large)
        val mask = BooleanArray(frame.width * frame.height) { index ->
            frame.pixels[index] < threshold
        }
        return findComponents(mask, frame.width, frame.height)
            .filter { component -> component.area >= 70 }
            .filter { component -> component.rect.width >= 14 && component.rect.height >= 14 }
            .filter { component -> component.fillRatio in 0.16f..0.74f }
            .filterNot { component -> component.rect.touchesBorder(frame) }
            .mapNotNull { component ->
                val point = when (kind) {
                    MiniProgramCornerKind.LU -> MiniProgramPoint(component.rect.top, component.rect.left)
                    MiniProgramCornerKind.LD -> MiniProgramPoint(bottomHorizontalTop(frame, component, threshold), component.rect.left)
                    MiniProgramCornerKind.RU -> MiniProgramPoint(component.rect.top, component.rect.right - 1)
                    MiniProgramCornerKind.RD -> MiniProgramPoint(bottomHorizontalTop(frame, component, threshold), component.rect.right - 1)
                }
                if (point.touchesBorder(frame)) return@mapNotNull null
                if (!pointInQuadrant(frame, kind, point)) return@mapNotNull null
                // Verify L-shape template to filter out solid black regions
                if (!matchesTemplateSafe(frame, point.row, point.column, threshold, tmpl)) return@mapNotNull null
                // Use trace length for more accurate scoring
                val lengths = traceLengthsSafe(frame, point.row, point.column, threshold, kind)
                val length = minOf(lengths.horizontal, lengths.vertical)
                if (length < MIN_TEMPLATE_TRACE_LENGTH) return@mapNotNull null
                MiniProgramCornerCandidate(kind, point, length, "component")
            }
    }

    private fun matchesTemplateSafe(
        frame: MiniProgramFrame,
        row: Int,
        column: Int,
        threshold: Int,
        template: List<TemplatePoint>,
    ): Boolean {
        for (point in template) {
            val r = row + point.rowOffset
            val c = column + point.columnOffset
            if (r !in 0 until frame.height || c !in 0 until frame.width) return false
            val value = frame[r, c]
            val isBlack = value < threshold
            val expectedBlack = point.expected == 0
            if (isBlack != expectedBlack || kotlin.math.abs(threshold - value) <= 10) return false
        }
        return true
    }

    private fun traceLengthsSafe(
        frame: MiniProgramFrame,
        row: Int,
        column: Int,
        threshold: Int,
        kind: MiniProgramCornerKind,
    ): TraceLengths {
        val horizontal = when (kind) {
            MiniProgramCornerKind.LU, MiniProgramCornerKind.LD -> traceSafe(frame, row, column, 0, 1, threshold)
            MiniProgramCornerKind.RU, MiniProgramCornerKind.RD -> traceSafe(frame, row, column, 0, -1, threshold)
        }
        val vertical = when (kind) {
            MiniProgramCornerKind.LU, MiniProgramCornerKind.RU -> traceSafe(frame, row, column, 1, 0, threshold)
            MiniProgramCornerKind.LD, MiniProgramCornerKind.RD -> traceSafe(frame, row, column, -1, 0, threshold)
        }
        return TraceLengths(horizontal = horizontal, vertical = vertical)
    }

    private fun traceSafe(
        frame: MiniProgramFrame,
        startRow: Int,
        startColumn: Int,
        rowStep: Int,
        columnStep: Int,
        threshold: Int,
    ): Int {
        var row = startRow
        var column = startColumn
        var length = 0
        while (length < 100 && row in 0 until frame.height && column in 0 until frame.width && frame[row, column] < threshold) {
            length += 1
            row += rowStep
            column += columnStep
        }
        return length
    }

    private fun MiniProgramPoint.touchesBorder(frame: MiniProgramFrame): Boolean =
        column <= BORDER_CANDIDATE_MARGIN ||
            row <= BORDER_CANDIDATE_MARGIN ||
            column >= frame.width - 1 - BORDER_CANDIDATE_MARGIN ||
            row >= frame.height - 1 - BORDER_CANDIDATE_MARGIN

    private fun bottomHorizontalTop(frame: MiniProgramFrame, component: Component, threshold: Int): Int {
        for (row in component.rect.bottom - 1 downTo component.rect.top) {
            var dark = 0
            for (column in component.rect.left until component.rect.right) {
                if (frame[row, column] < threshold) dark += 1
            }
            if (dark < component.rect.width * 0.75f) return row + 1
        }
        return component.rect.top
    }

    private fun pointInQuadrant(frame: MiniProgramFrame, kind: MiniProgramCornerKind, point: MiniProgramPoint): Boolean =
        when (kind) {
            MiniProgramCornerKind.LU -> point.row < frame.height / 2 && point.column < frame.width / 2
            MiniProgramCornerKind.LD -> point.row > frame.height / 2 && point.column < frame.width / 2
            MiniProgramCornerKind.RU -> point.row < frame.height / 2 && point.column > frame.width / 2
            MiniProgramCornerKind.RD -> point.row > frame.height / 2 && point.column > frame.width / 2
        }

    private fun tooClose(candidates: List<MiniProgramCornerCandidate>, row: Int, column: Int): Boolean =
        candidates.any { kotlin.math.abs(it.point.row - row) < 8 && kotlin.math.abs(it.point.column - column) < 8 }

    private fun localThreshold(
        frame: MiniProgramFrame,
        row: Int,
        column: Int,
        globalThreshold: Int,
        radius: Int,
    ): Int {
        var max = 0
        for (r in row - radius..row + radius) {
            for (c in column - radius..column + radius) {
                max = maxOf(max, frame[r, c])
            }
        }
        return (globalThreshold + 0.6 * (max - globalThreshold)).toInt().coerceIn(0, 255)
    }

    private fun hasLocalDarkContrast(
        frame: MiniProgramFrame,
        row: Int,
        column: Int,
        radius: Int,
    ): Boolean {
        val center = frame[row, column]
        val localMax = maxOf(
            center,
            frame[row - radius, column],
            frame[row + radius, column],
            frame[row, column - radius],
            frame[row, column + radius],
            frame[row - radius, column - radius],
            frame[row - radius, column + radius],
            frame[row + radius, column - radius],
            frame[row + radius, column + radius],
        )
        return center + LOCAL_CONTRAST_DELTA < localMax
    }

    private fun matchesTemplate(
        frame: MiniProgramFrame,
        row: Int,
        column: Int,
        threshold: Int,
        template: List<TemplatePoint>,
    ): Boolean {
        return template.all { point ->
            val value = frame[row + point.rowOffset, column + point.columnOffset]
            val isBlack = value < threshold
            val expectedBlack = point.expected == 0
            isBlack == expectedBlack && kotlin.math.abs(threshold - value) > 10
        }
    }

    private data class TraceLengths(
        val horizontal: Int,
        val vertical: Int,
    )

    private fun traceLengths(
        frame: MiniProgramFrame,
        row: Int,
        column: Int,
        threshold: Int,
        kind: MiniProgramCornerKind,
    ): TraceLengths {
        val horizontal = when (kind) {
            MiniProgramCornerKind.LU, MiniProgramCornerKind.LD -> trace(frame, row, column, 0, 1, threshold)
            MiniProgramCornerKind.RU, MiniProgramCornerKind.RD -> trace(frame, row, column, 0, -1, threshold)
        }
        val vertical = when (kind) {
            MiniProgramCornerKind.LU, MiniProgramCornerKind.RU -> trace(frame, row, column, 1, 0, threshold)
            MiniProgramCornerKind.LD, MiniProgramCornerKind.RD -> trace(frame, row, column, -1, 0, threshold)
        }
        return TraceLengths(horizontal = horizontal, vertical = vertical)
    }

    private fun matchesStrongTraceAnchor(
        frame: MiniProgramFrame,
        row: Int,
        column: Int,
        threshold: Int,
        kind: MiniProgramCornerKind,
        lengths: TraceLengths,
    ): Boolean {
        val minLength = minOf(lengths.horizontal, lengths.vertical)
        if (minLength < MIN_STRONG_TRACE_LENGTH) return false
        val maxLength = maxOf(lengths.horizontal, lengths.vertical)
        if (maxLength.toDouble() / minLength.toDouble() > MAX_TRACE_BALANCE_RATIO) return false
        return hasLightInterior(frame, row, column, threshold, kind, minLength)
    }

    private fun canonicalTracePoint(
        frame: MiniProgramFrame,
        row: Int,
        column: Int,
        threshold: Int,
        kind: MiniProgramCornerKind,
        minLength: Int,
    ): MiniProgramPoint {
        if (kind != MiniProgramCornerKind.LD && kind != MiniProgramCornerKind.RD) {
            return MiniProgramPoint(row, column)
        }
        var topOfBottomBar = row
        while (
            topOfBottomBar - 1 >= 0 &&
            frame[topOfBottomBar - 1, column] < threshold &&
            traceLengths(frame, topOfBottomBar - 1, column, threshold, kind).horizontal >= minLength
        ) {
            topOfBottomBar -= 1
        }
        return MiniProgramPoint(topOfBottomBar, column)
    }

    private fun hasLightInterior(
        frame: MiniProgramFrame,
        row: Int,
        column: Int,
        threshold: Int,
        kind: MiniProgramCornerKind,
        length: Int,
    ): Boolean {
        val offset = (length / 2).coerceAtLeast(1)
        val interior = when (kind) {
            MiniProgramCornerKind.LU -> MiniProgramPoint(row + offset, column + offset)
            MiniProgramCornerKind.LD -> MiniProgramPoint(row - offset, column + offset)
            MiniProgramCornerKind.RU -> MiniProgramPoint(row + offset, column - offset)
            MiniProgramCornerKind.RD -> MiniProgramPoint(row - offset, column - offset)
        }
        if (interior.row !in 0 until frame.height || interior.column !in 0 until frame.width) return false
        return frame[interior.row, interior.column] >= threshold
    }

    private fun trace(
        frame: MiniProgramFrame,
        startRow: Int,
        startColumn: Int,
        rowStep: Int,
        columnStep: Int,
        threshold: Int,
    ): Int {
        var row = startRow
        var column = startColumn
        var length = 0
        while (length < 100 && frame[row, column] < threshold) {
            length += 1
            row += rowStep
            column += columnStep
        }
        return length
    }

    private data class TemplatePoint(
        val rowOffset: Int,
        val columnOffset: Int,
        val expected: Int,
    )

    private data class ComponentRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    private fun ComponentRect.touchesBorder(frame: MiniProgramFrame): Boolean =
        left <= BORDER_CANDIDATE_MARGIN ||
            top <= BORDER_CANDIDATE_MARGIN ||
            right - 1 >= frame.width - 1 - BORDER_CANDIDATE_MARGIN ||
            bottom - 1 >= frame.height - 1 - BORDER_CANDIDATE_MARGIN

    private data class Component(
        val rect: ComponentRect,
        val area: Int,
    ) {
        val fillRatio: Float get() = area.toFloat() / (rect.width * rect.height).coerceAtLeast(1)
    }

    private fun findComponents(mask: BooleanArray, width: Int, height: Int): List<Component> {
        val visited = BooleanArray(mask.size)
        val components = mutableListOf<Component>()
        val queue = IntArray(mask.size)
        for (row in 0 until height) {
            for (column in 0 until width) {
                val index = row * width + column
                if (visited[index] || !mask[index]) {
                    visited[index] = true
                    continue
                }
                var minRow = row
                var maxRow = row
                var minColumn = column
                var maxColumn = column
                var count = 0
                var head = 0
                var tail = 0
                queue[tail++] = index
                visited[index] = true
                while (head < tail) {
                    val current = queue[head++]
                    val y = current / width
                    val x = current % width
                    count += 1
                    minRow = minOf(minRow, y)
                    maxRow = maxOf(maxRow, y)
                    minColumn = minOf(minColumn, x)
                    maxColumn = maxOf(maxColumn, x)
                    tail = enqueue(mask, visited, queue, tail, width, height, y - 1, x)
                    tail = enqueue(mask, visited, queue, tail, width, height, y + 1, x)
                    tail = enqueue(mask, visited, queue, tail, width, height, y, x - 1)
                    tail = enqueue(mask, visited, queue, tail, width, height, y, x + 1)
                }
                components += Component(
                    rect = ComponentRect(
                        left = minColumn,
                        top = minRow,
                        right = maxColumn + 1,
                        bottom = maxRow + 1,
                    ),
                    area = count,
                )
            }
        }
        return components
    }

    private fun enqueue(
        mask: BooleanArray,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int,
        width: Int,
        height: Int,
        row: Int,
        column: Int,
    ): Int {
        if (row !in 0 until height || column !in 0 until width) return tail
        val index = row * width + column
        if (visited[index]) return tail
        visited[index] = true
        if (!mask[index]) return tail
        queue[tail] = index
        return tail + 1
    }

    private fun template(kind: MiniProgramCornerKind, large: Boolean): List<TemplatePoint> {
        val unit = if (large) 6 else 3
        val base = when (kind) {
            MiniProgramCornerKind.LU -> listOf(
                Triple(0, 0, 0), Triple(-1, 0, 1), Triple(0, -1, 1), Triple(1, 1, 1),
                Triple(0, 1, 0), Triple(-1, 1, 1), Triple(1, -1, 1), Triple(1, 0, 0),
                Triple(2, 1, 1), Triple(1, 2, 1),
            )
            MiniProgramCornerKind.LD -> listOf(
                Triple(0, 0, 0), Triple(-1, 0, 1), Triple(0, 1, 1), Triple(1, -1, 1),
                Triple(1, 0, 0), Triple(0, -1, 0), Triple(1, 1, 1), Triple(-1, -1, 1),
                Triple(2, -1, 1), Triple(1, -2, 1),
            )
            MiniProgramCornerKind.RU -> listOf(
                Triple(0, 0, 0), Triple(1, 0, 1), Triple(0, -1, 1), Triple(-1, 1, 1),
                Triple(0, 1, 0), Triple(-1, 0, 0), Triple(1, 1, 1), Triple(-1, -1, 1),
                Triple(-1, 2, 1), Triple(-2, 1, 1),
            )
            MiniProgramCornerKind.RD -> listOf(
                Triple(0, 0, 0), Triple(0, 1, 1), Triple(1, 0, 1), Triple(-1, -1, 1),
                Triple(-1, 0, 0), Triple(0, -1, 0), Triple(-1, 1, 1), Triple(1, -1, 1),
                Triple(-2, -1, 1), Triple(-1, -2, 1),
            )
        }
        val refinements = when (kind) {
            MiniProgramCornerKind.LU -> listOf(
                Triple(0, 2, 0), Triple(2, 0, 0), Triple(-1, 2, 1), Triple(2, -1, 1),
                Triple(1, 1 + unit / unit, 1), Triple(1 + unit / unit, 1, 1),
                Triple(0, 4 / (if (large) 2 else 2), 0), Triple(4 / (if (large) 2 else 2), 0, 0),
                Triple(-1, 4 / (if (large) 2 else 2), 1), Triple(4 / (if (large) 2 else 2), -1, 1),
            )
            MiniProgramCornerKind.LD -> listOf(
                Triple(2, 0, 0), Triple(0, -2, 0), Triple(2, 1, 1), Triple(-1, -2, 1),
                Triple(1 + unit / unit, -1, 1), Triple(1, -(1 + unit / unit), 1),
                Triple(4 / (if (large) 2 else 2), 0, 0), Triple(0, -4 / (if (large) 2 else 2), 0),
                Triple(4 / (if (large) 2 else 2), 1, 1), Triple(-1, -4 / (if (large) 2 else 2), 1),
            )
            MiniProgramCornerKind.RU -> listOf(
                Triple(0, 2, 0), Triple(-2, 0, 0), Triple(1, 2, 1), Triple(-2, -1, 1),
                Triple(-1, 1 + unit / unit, 1), Triple(-(1 + unit / unit), 1, 1),
                Triple(0, 4 / (if (large) 2 else 2), 0), Triple(-4 / (if (large) 2 else 2), 0, 0),
                Triple(1, 4 / (if (large) 2 else 2), 1), Triple(-4 / (if (large) 2 else 2), -1, 1),
            )
            MiniProgramCornerKind.RD -> listOf(
                Triple(-2, 0, 0), Triple(0, -2, 0), Triple(-2, 1, 1), Triple(1, -2, 1),
                Triple(-(1 + unit / unit), -1, 1), Triple(-1, -(1 + unit / unit), 1),
                Triple(-4 / (if (large) 2 else 2), 0, 0), Triple(0, -4 / (if (large) 2 else 2), 0),
                Triple(-4 / (if (large) 2 else 2), 1, 1), Triple(1, -4 / (if (large) 2 else 2), 1),
            )
        }
        return (base + refinements).map { TemplatePoint(it.first * unit, it.second * unit, it.third) }
    }

    private fun diagnostics(
        totalStartedAt: Long,
        cornerElapsedMs: Long,
        searches: List<CandidateSearchResult>,
        anchors: MiniProgramAnchors?,
    ): CornerAnchorDiagnostics =
        CornerAnchorDiagnostics(
            totalScanElapsedMs = elapsedMs(totalStartedAt),
            cornerElapsedMs = cornerElapsedMs,
            cornerCandidateElapsedMs = searches.sumOf { it.elapsedMs },
            strongTraceElapsedMs = searches.sumOf { it.strongTraceElapsedMs },
            componentCandidateCount = searches.sumOf { it.componentCandidateCount },
            templateCandidateCount = searches.sumOf { it.templateCandidateCount },
            strongTraceCandidateCount = searches.sumOf { it.strongTraceCandidateCount },
            finalAnchorSource = anchors?.let(::finalAnchorSource) ?: "none",
            luCandidateCount = searches.firstCount(MiniProgramCornerKind.LU),
            ldCandidateCount = searches.firstCount(MiniProgramCornerKind.LD),
            ruCandidateCount = searches.firstCount(MiniProgramCornerKind.RU),
            rdCandidateCount = searches.firstCount(MiniProgramCornerKind.RD),
            bestLu = searches.bestCandidate(MiniProgramCornerKind.LU),
            bestLd = searches.bestCandidate(MiniProgramCornerKind.LD),
            bestRu = searches.bestCandidate(MiniProgramCornerKind.RU),
            bestRd = searches.bestCandidate(MiniProgramCornerKind.RD),
            selectedLu = anchors?.lu?.formatCandidate() ?: "none",
            selectedLd = anchors?.ld?.formatCandidate() ?: "none",
            selectedRu = anchors?.ru?.formatCandidate() ?: "none",
            selectedRd = anchors?.rd?.formatCandidate() ?: "none",
        )

    private fun List<CandidateSearchResult>.firstCount(kind: MiniProgramCornerKind): Int =
        firstOrNull { it.kind == kind }?.candidates?.size ?: 0

    private fun List<CandidateSearchResult>.bestCandidate(kind: MiniProgramCornerKind): String =
        firstOrNull { it.kind == kind }?.candidates?.firstOrNull()?.formatCandidate() ?: "none"

    private fun MiniProgramCornerCandidate.formatCandidate(): String =
        "${point.column},${point.row} score=$length source=$source"

    private fun finalAnchorSource(anchors: MiniProgramAnchors): String =
        listOf(anchors.lu, anchors.ld, anchors.ru, anchors.rd)
            .map { it.source }
            .distinct()
            .joinToString("+")

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000
}
