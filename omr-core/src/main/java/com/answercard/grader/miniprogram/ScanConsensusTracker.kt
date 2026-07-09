package com.answercard.grader.miniprogram

sealed interface ScanConsensusDecision {
    data class Pending(val streak: Int, val required: Int) : ScanConsensusDecision
    data class Locked(val signature: String, val result: AndroidOmrResult) : ScanConsensusDecision
    data class AlreadyLocked(val signature: String) : ScanConsensusDecision
}

/**
 * Locks a scan once [requiredFrames] frames have agreed on the same signature (admission
 * number + score).
 *
 * The tally is cumulative and order-independent: matches for a signature accumulate across the
 * session and are never reset by an intervening bad frame. Failed / signature-less frames are
 * no-ops — they neither count nor clear progress — so the dominant reading of a steady card
 * wins even when a third of the frames fail or a bubble read jitters between frames. This
 * mirrors "let bad frames fail and let consensus filter" rather than requiring an unbroken run
 * of identical frames.
 */
class ScanConsensusTracker(
    private val requiredFrames: Int = 3,
    private val signatureProvider: (AndroidOmrResult) -> String? = Companion::signatureOf,
) {
    init {
        require(requiredFrames >= 1) { "requiredFrames must be at least 1" }
    }

    private val counts = LinkedHashMap<String, Int>()
    private val lockedSignatures = LinkedHashSet<String>()

    fun offer(result: AndroidOmrResult): ScanConsensusDecision {
        val signature = signatureProvider(result)
            ?: return ScanConsensusDecision.Pending(streak = 0, required = requiredFrames)
        if (signature in lockedSignatures) {
            return ScanConsensusDecision.AlreadyLocked(signature)
        }
        val count = (counts[signature] ?: 0) + 1
        counts[signature] = count
        if (count < requiredFrames) {
            return ScanConsensusDecision.Pending(streak = count, required = requiredFrames)
        }
        lockedSignatures += signature
        return ScanConsensusDecision.Locked(signature = signature, result = result)
    }

    fun reset() {
        counts.clear()
        lockedSignatures.clear()
    }

    companion object {
        fun signatureOf(result: AndroidOmrResult): String? {
            if (!result.success) return null
            val score = result.score ?: return null
            val admission = result.admissionNumber?.digits.orEmpty()
            return "$admission|${score.totalScore}/${score.maxScore}"
        }
    }
}
