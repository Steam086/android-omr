package com.answercard.grader.miniprogram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchorAmbiguityEvaluatorTest {
    @Test
    fun rejectsTwoDistinctCandidatesWithCloseScores() {
        val selection = AnchorAmbiguityEvaluator.select(
            candidates = listOf(
                ScoredAnchorCandidate(anchors(offset = 0), score = 100.0),
                ScoredAnchorCandidate(anchors(offset = 30), score = 97.5),
            ),
            direction = AnchorScoreDirection.HIGHER_IS_BETTER,
            absoluteMinimumGap = 3.0,
            relativeMinimumGap = 0.02,
        )

        assertTrue(selection.ambiguous)
    }

    @Test
    fun ignoresEquivalentSubpixelScaleAlternatives() {
        val selection = AnchorAmbiguityEvaluator.select(
            candidates = listOf(
                ScoredAnchorCandidate(anchors(offset = 0), score = 100.0),
                ScoredAnchorCandidate(anchors(offset = 2), score = 99.0),
            ),
            direction = AnchorScoreDirection.HIGHER_IS_BETTER,
            absoluteMinimumGap = 3.0,
            relativeMinimumGap = 0.02,
        )

        assertFalse(selection.ambiguous)
    }

    @Test
    fun acceptsClearlyBetterDistinctCandidate() {
        val selection = AnchorAmbiguityEvaluator.select(
            candidates = listOf(
                ScoredAnchorCandidate(anchors(offset = 0), score = -0.8),
                ScoredAnchorCandidate(anchors(offset = 30), score = -0.5),
            ),
            direction = AnchorScoreDirection.LOWER_IS_BETTER,
            absoluteMinimumGap = 0.1,
            relativeMinimumGap = 0.08,
        )

        assertFalse(selection.ambiguous)
    }

    private fun anchors(offset: Int): MiniProgramAnchors {
        fun candidate(kind: MiniProgramCornerKind, row: Int, column: Int) =
            MiniProgramCornerCandidate(kind, MiniProgramPoint(row + offset, column + offset), 30)
        val lu = candidate(MiniProgramCornerKind.LU, 20, 20)
        val ld = candidate(MiniProgramCornerKind.LD, 220, 20)
        val ru = candidate(MiniProgramCornerKind.RU, 20, 320)
        val rd = candidate(MiniProgramCornerKind.RD, 220, 320)
        return MiniProgramAnchors(lu, ld, ru, rd, MiniProgramGeometry.isQuad(lu.point, ld.point, ru.point, rd.point))
    }
}
