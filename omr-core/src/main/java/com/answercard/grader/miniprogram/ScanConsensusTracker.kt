package com.answercard.grader.miniprogram

sealed interface ScanConsensusDecision {
    data class Pending(val streak: Int, val required: Int) : ScanConsensusDecision
    data class Locked(val signature: String, val result: AndroidOmrResult) : ScanConsensusDecision
    data class AlreadyLocked(val signature: String) : ScanConsensusDecision
}

/**
 * Accepts a scan only after [requiredFrames] consecutive successful frames agree on the
 * same signature (admission number + per-question selections + score).
 */
class ScanConsensusTracker(
    private val requiredFrames: Int = 3,
    private val signatureProvider: (AndroidOmrResult) -> String? = Companion::signatureOf,
) {
    init {
        require(requiredFrames >= 1) { "requiredFrames must be at least 1" }
    }

    private var currentSignature: String? = null
    private var streak = 0
    private var lockedSignature: String? = null

    fun offer(result: AndroidOmrResult): ScanConsensusDecision {
        val signature = signatureProvider(result)
        if (signature == null) {
            currentSignature = null
            streak = 0
            return ScanConsensusDecision.Pending(streak = 0, required = requiredFrames)
        }
        if (signature == currentSignature) {
            streak += 1
        } else {
            currentSignature = signature
            streak = 1
        }
        if (streak < requiredFrames) {
            return ScanConsensusDecision.Pending(streak = streak, required = requiredFrames)
        }
        if (signature == lockedSignature) {
            return ScanConsensusDecision.AlreadyLocked(signature)
        }
        lockedSignature = signature
        return ScanConsensusDecision.Locked(signature = signature, result = result)
    }

    fun reset() {
        currentSignature = null
        streak = 0
        lockedSignature = null
    }

    companion object {
        fun signatureOf(result: AndroidOmrResult): String? {
            if (!result.success) return null
            val score = result.score ?: return null
            val answers = result.answerArea?.questions
                ?.sortedBy { it.questionIndex }
                ?.joinToString(";") { question ->
                    "${question.questionIndex}:${question.selectedLabels.joinToString(",")}"
                }
                ?: return null
            val admission = result.admissionNumber?.digits.orEmpty()
            return "$admission|$answers|${score.totalScore}/${score.maxScore}"
        }
    }
}
