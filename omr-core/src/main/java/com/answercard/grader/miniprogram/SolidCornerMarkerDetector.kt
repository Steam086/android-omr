package com.answercard.grader.miniprogram

import kotlin.math.abs
import kotlin.math.roundToInt

object SolidCornerMarkerDetector {
    const val SOURCE = "solid-marker"
    private const val MIN_FILL_RATIO = 0.85f
    private const val MIN_SIZE_RATIO = 0.012

    // Floor case: headerless single-band card renders 192 template units tall, so the
    // 26-unit corner marker is ~13.5% of the min frame dimension; 0.16 adds headroom for
    // perspective enlargement of near-corner markers. False positives stay gated by aspect/fill/spread.
    private const val MAX_SIZE_RATIO = 0.16
    private const val MAX_BOX_ASPECT_DEVIATION = 0.35
    private const val MAX_CANDIDATES_PER_CORNER = 3
    private const val MAX_QUAD_ASPECT_RELATIVE_DEVIATION = 0.45
    private const val MAX_SIZE_SPREAD = 2.0

    fun findAnchors(frame: MiniProgramFrame, expectedAspectRatio: Double? = null): MiniProgramAnchors? {
        val threshold = MiniProgramGeometry.threshold(frame)
        val minSize = maxOf(8, (minOf(frame.width, frame.height) * MIN_SIZE_RATIO).toInt())
        val maxSize = (minOf(frame.width, frame.height) * MAX_SIZE_RATIO).toInt()
        val candidates = MiniProgramComponentScanner.scan(frame, threshold)
            .asSequence()
            .filter { it.fillRatio >= MIN_FILL_RATIO }
            .filter { it.rect.width in minSize..maxSize && it.rect.height in minSize..maxSize }
            .filter {
                val aspect = it.rect.width.toDouble() / it.rect.height.coerceAtLeast(1).toDouble()
                abs(aspect - 1.0) <= MAX_BOX_ASPECT_DEVIATION
            }
            .filter { it.rect.left > 0 && it.rect.top > 0 && it.rect.right < frame.width && it.rect.bottom < frame.height }
            .toList()
        if (candidates.size < 4) return null

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

            val score = aspectDeviation + (spread - 1.0)
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
        return best
    }

    private fun nearest(candidates: List<MiniProgramComponent>, row: Double, column: Double): List<MiniProgramComponent> =
        candidates.sortedBy { component ->
            val dr = component.centroidRow - row
            val dc = component.centroidColumn - column
            dr * dr + dc * dc
        }.take(MAX_CANDIDATES_PER_CORNER)

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
        return kotlin.math.sqrt(dr * dr + dc * dc)
    }
}
