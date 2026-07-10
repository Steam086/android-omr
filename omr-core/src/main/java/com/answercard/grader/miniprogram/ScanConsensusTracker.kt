package com.answercard.grader.miniprogram

import java.util.ArrayDeque

sealed interface ScanConsensusDecision {
    data class Pending(val streak: Int, val required: Int) : ScanConsensusDecision
    data class Locked(val signature: String, val result: AndroidOmrResult) : ScanConsensusDecision
    data class AlreadyLocked(val signature: String) : ScanConsensusDecision
    data object CardCleared : ScanConsensusDecision
}

/**
 * Locks a card when a short sliding window agrees on its admission number, per-question
 * answers, and score. Once locked, no other reading can replace it until the card has been
 * absent long enough to start a new scan session.
 */
class ScanConsensusTracker(
    private val requiredFrames: Int = 3,
    private val windowSize: Int = 4,
    private val maxWindowDurationMs: Long = 3_000L,
    private val cardAbsentResetMs: Long = 900L,
    private val signatureProvider: (AndroidOmrResult) -> String? = Companion::signatureOf,
    private val nowMsProvider: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    init {
        require(requiredFrames >= 1) { "requiredFrames must be at least 1" }
        require(windowSize >= requiredFrames) { "windowSize must be at least requiredFrames" }
        require(maxWindowDurationMs > 0L) { "maxWindowDurationMs must be positive" }
        require(cardAbsentResetMs > 0L) { "cardAbsentResetMs must be positive" }
    }

    private val samples = ArrayDeque<Sample>()
    private var activeLockedSignature: String? = null
    private var absenceStartedAtMs: Long? = null

    fun offer(result: AndroidOmrResult): ScanConsensusDecision {
        val nowMs = nowMsProvider()
        expireOldSamples(nowMs)
        val signature = signatureProvider(result)
        if (signature == null) return handleUnrecognizedFrame(result, nowMs)

        absenceStartedAtMs = null
        activeLockedSignature?.let { return ScanConsensusDecision.AlreadyLocked(it) }

        samples.addLast(Sample(timestampMs = nowMs, signature = signature))
        while (samples.size > windowSize) samples.removeFirst()
        val matchingFrames = samples.count { it.signature == signature }
        if (matchingFrames < requiredFrames) {
            return ScanConsensusDecision.Pending(streak = matchingFrames, required = requiredFrames)
        }

        activeLockedSignature = signature
        samples.clear()
        return ScanConsensusDecision.Locked(signature = signature, result = result)
    }

    fun reset() {
        samples.clear()
        activeLockedSignature = null
        absenceStartedAtMs = null
    }

    private fun handleUnrecognizedFrame(
        result: AndroidOmrResult,
        nowMs: Long,
    ): ScanConsensusDecision {
        if (result.anchors != null) {
            absenceStartedAtMs = null
            return pendingDecision()
        }

        val absenceStarted = absenceStartedAtMs ?: nowMs.also { absenceStartedAtMs = it }
        if (nowMs - absenceStarted < cardAbsentResetMs) return pendingDecision()

        val hadActiveCard = activeLockedSignature != null || samples.isNotEmpty()
        reset()
        return if (hadActiveCard) {
            ScanConsensusDecision.CardCleared
        } else {
            ScanConsensusDecision.Pending(streak = 0, required = requiredFrames)
        }
    }

    private fun pendingDecision(): ScanConsensusDecision {
        val leadingCount = samples
            .groupingBy { it.signature }
            .eachCount()
            .values
            .maxOrNull()
            ?: 0
        return ScanConsensusDecision.Pending(streak = leadingCount, required = requiredFrames)
    }

    private fun expireOldSamples(nowMs: Long) {
        while (samples.isNotEmpty() && nowMs - samples.first.timestampMs > maxWindowDurationMs) {
            samples.removeFirst()
        }
    }

    private data class Sample(
        val timestampMs: Long,
        val signature: String,
    )

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
