package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanConsensusTrackerTest {
    private fun result(
        success: Boolean = true,
        admission: String = "1234",
        answers: List<Pair<Int, List<String>>> = listOf(0 to listOf("A"), 1 to listOf("B")),
        totalScore: Double = 4.0,
    ): AndroidOmrResult {
        val questions = answers.map { (index, labels) ->
            AndroidQuestionReadResult(
                questionIndex = index,
                selectedOptions = labels.map { 0 },
                selectedLabels = labels,
                optionResults = emptyList(),
                isBlank = labels.isEmpty(),
                isMultiMarked = false,
            )
        }
        return AndroidOmrResult(
            success = success,
            failureReason = null,
            layout = null,
            anchors = null,
            grid = null,
            answerArea = AndroidAnswerAreaReadResult(questions = questions, failureReason = null, debugInfo = emptyList()),
            admissionNumber = AndroidAdmissionNumberReadResult(
                digits = admission,
                digitResults = emptyList(),
                success = true,
                failureReason = null,
                debugInfo = emptyList(),
            ),
            score = AndroidOmrScoreResult(
                totalScore = totalScore,
                maxScore = 10.0,
                items = emptyList(),
                success = true,
                warnings = emptyList(),
            ),
            warnings = emptyList(),
            debugInfo = emptyList(),
        )
    }

    @Test
    fun locksAfterThreeConsecutiveIdenticalFrames() {
        val tracker = ScanConsensusTracker()
        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Pending)
        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Pending)
        val third = tracker.offer(result())
        assertTrue(third is ScanConsensusDecision.Locked)
    }

    @Test
    fun failureFrameResetsStreak() {
        val tracker = ScanConsensusTracker()
        tracker.offer(result())
        tracker.offer(result())
        tracker.offer(result(success = false))
        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Pending)
        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Pending)
        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Locked)
    }

    @Test
    fun alternatingSignaturesNeverLock() {
        val tracker = ScanConsensusTracker()
        repeat(5) {
            assertTrue(tracker.offer(result(totalScore = 4.0)) is ScanConsensusDecision.Pending)
            assertTrue(tracker.offer(result(totalScore = 6.0)) is ScanConsensusDecision.Pending)
        }
    }

    @Test
    fun sameSignatureAfterLockDoesNotRetrigger() {
        val tracker = ScanConsensusTracker()
        repeat(3) { tracker.offer(result()) }
        val next = tracker.offer(result())
        assertTrue(next is ScanConsensusDecision.AlreadyLocked)
    }

    @Test
    fun newCardLocksAgainAutomatically() {
        val tracker = ScanConsensusTracker()
        repeat(3) { tracker.offer(result(admission = "1234")) }
        repeat(2) { assertTrue(tracker.offer(result(admission = "5678")) is ScanConsensusDecision.Pending) }
        val locked = tracker.offer(result(admission = "5678"))
        assertTrue(locked is ScanConsensusDecision.Locked)
        assertTrue((locked as ScanConsensusDecision.Locked).signature.startsWith("5678|"))
    }

    @Test
    fun signatureIsNullForFailedOrScorelessResults() {
        assertNull(ScanConsensusTracker.signatureOf(result(success = false)))
        assertNull(ScanConsensusTracker.signatureOf(result().copy(score = null)))
    }

    @Test
    fun customSignatureProviderIsRespected() {
        val tracker = ScanConsensusTracker(signatureProvider = { r -> r.admissionNumber?.digits?.takeIf { it.isNotBlank() } })
        repeat(2) { tracker.offer(result()) }
        val third = tracker.offer(result())
        assertTrue(third is ScanConsensusDecision.Locked)
        assertEquals("1234", (third as ScanConsensusDecision.Locked).signature)
    }
}
