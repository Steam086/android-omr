package com.answercard.grader.miniprogram

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

internal enum class AnchorScoreDirection {
    HIGHER_IS_BETTER,
    LOWER_IS_BETTER,
}

internal data class ScoredAnchorCandidate(
    val anchors: MiniProgramAnchors,
    val score: Double,
)

internal data class AnchorCandidateSelection(
    val best: ScoredAnchorCandidate?,
    val runnerUp: ScoredAnchorCandidate?,
    val scoreGap: Double?,
    val ambiguous: Boolean,
)

internal object AnchorAmbiguityEvaluator {
    fun select(
        candidates: List<ScoredAnchorCandidate>,
        direction: AnchorScoreDirection,
        absoluteMinimumGap: Double,
        relativeMinimumGap: Double,
    ): AnchorCandidateSelection {
        val sorted = when (direction) {
            AnchorScoreDirection.HIGHER_IS_BETTER -> candidates.sortedByDescending { it.score }
            AnchorScoreDirection.LOWER_IS_BETTER -> candidates.sortedBy { it.score }
        }
        val best = sorted.firstOrNull() ?: return AnchorCandidateSelection(null, null, null, false)
        val runnerUp = sorted.drop(1).firstOrNull { !equivalentGeometry(best.anchors, it.anchors) }
            ?: return AnchorCandidateSelection(best, null, null, false)
        val gap = when (direction) {
            AnchorScoreDirection.HIGHER_IS_BETTER -> best.score - runnerUp.score
            AnchorScoreDirection.LOWER_IS_BETTER -> runnerUp.score - best.score
        }
        val requiredGap = max(absoluteMinimumGap, abs(best.score) * relativeMinimumGap)
        return AnchorCandidateSelection(
            best = best,
            runnerUp = runnerUp,
            scoreGap = gap,
            ambiguous = gap < requiredGap,
        )
    }

    private fun equivalentGeometry(first: MiniProgramAnchors, second: MiniProgramAnchors): Boolean {
        val firstPoints = listOf(first.lu.point, first.ru.point, first.ld.point, first.rd.point)
        val secondPoints = listOf(second.lu.point, second.ru.point, second.ld.point, second.rd.point)
        val averageWidth = (
            distance(first.lu.point, first.ru.point) + distance(first.ld.point, first.rd.point)
            ) / 2.0
        val averageHeight = (
            distance(first.lu.point, first.ld.point) + distance(first.ru.point, first.rd.point)
            ) / 2.0
        // A trace may land on either side of the same printed bracket stroke. The stroke is
        // about 1.4% of the rendered card span, so shifts up to 1.5% are one physical anchor;
        // larger alternatives remain distinct and require a decisive score margin.
        val tolerance = max(4.0, max(averageWidth, averageHeight) * 0.015)
        return firstPoints.zip(secondPoints).all { (a, b) -> distance(a, b) <= tolerance }
    }

    private fun distance(a: MiniProgramPoint, b: MiniProgramPoint): Double =
        hypot((a.column - b.column).toDouble(), (a.row - b.row).toDouble())
}
