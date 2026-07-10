package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanConsensusTrackerTest {
    private fun result(
        success: Boolean = true,
        admission: String = "1234",
        answers: List<Pair<Int, List<String>>> = listOf(0 to listOf("A"), 1 to listOf("B")),
        totalScore: Double = 4.0,
        cardVisible: Boolean = success,
    ): AndroidOmrResult {
        val questions = answers.map { (index, labels) ->
            AndroidQuestionReadResult(
                questionIndex = index,
                selectedOptions = labels.indices.toList(),
                selectedLabels = labels,
                optionResults = emptyList(),
                isBlank = labels.isEmpty(),
                isMultiMarked = labels.size > 1,
            )
        }
        return AndroidOmrResult(
            success = success,
            failureReason = null,
            layout = null,
            anchors = if (cardVisible) visibleAnchors() else null,
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
    fun locksAfterThreeConsistentFrames() {
        val tracker = ScanConsensusTracker()
        repeat(2) { assertTrue(tracker.offer(result()) is ScanConsensusDecision.Pending) }
        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Locked)
    }

    @Test
    fun requiresThreeMatchingFramesInsideFourFrameWindow() {
        val tracker = ScanConsensusTracker()
        listOf(4.0, 6.0, 4.0).forEach { score ->
            assertTrue(tracker.offer(result(totalScore = score)) is ScanConsensusDecision.Pending)
        }
        val locked = tracker.offer(result(totalScore = 4.0))
        assertTrue(locked is ScanConsensusDecision.Locked)
        assertTrue((locked as ScanConsensusDecision.Locked).signature.endsWith("4.0/10.0"))
    }

    @Test
    fun failedFrameWithVisibleCardDoesNotResetWindow() {
        val tracker = ScanConsensusTracker()
        repeat(2) { tracker.offer(result()) }
        val pending = tracker.offer(result(success = false, cardVisible = true))
        assertEquals(2, (pending as ScanConsensusDecision.Pending).streak)
        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Locked)
    }

    @Test
    fun samplesOutsideTimeWindowExpire() {
        var nowMs = 0L
        val tracker = ScanConsensusTracker(nowMsProvider = { nowMs })
        repeat(2) {
            tracker.offer(result())
            nowMs += 300L
        }
        nowMs = 3_301L
        val pending = tracker.offer(result())
        assertEquals(1, (pending as ScanConsensusDecision.Pending).streak)
    }

    @Test
    fun slowValidResultsFitInsideThreeSecondWindow() {
        var nowMs = 0L
        val tracker = ScanConsensusTracker(nowMsProvider = { nowMs })

        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Pending)
        nowMs = 1_100L
        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Pending)
        nowMs = 2_200L

        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Locked)
    }

    @Test
    fun answerJitterProducesDifferentSignaturesAndCannotLock() {
        val tracker = ScanConsensusTracker()
        val variants = listOf(
            listOf(0 to listOf("A"), 1 to listOf("B")),
            listOf(0 to listOf("A"), 1 to listOf("C")),
            listOf(0 to listOf("D"), 1 to listOf("B")),
        )
        repeat(6) { index ->
            assertTrue(tracker.offer(result(answers = variants[index % variants.size])) is ScanConsensusDecision.Pending)
        }
    }

    @Test
    fun signatureIncludesPerQuestionAnswers() {
        val first = ScanConsensusTracker.signatureOf(result(answers = listOf(0 to listOf("A"))))
        val second = ScanConsensusTracker.signatureOf(result(answers = listOf(0 to listOf("B"))))
        assertNotEquals(first, second)
    }

    @Test
    fun anyNewReadingIsSuppressedWhileCardRemainsLocked() {
        val tracker = ScanConsensusTracker()
        repeat(3) { tracker.offer(result()) }
        assertTrue(tracker.offer(result()) is ScanConsensusDecision.AlreadyLocked)
        assertTrue(tracker.offer(result(totalScore = 8.0)) is ScanConsensusDecision.AlreadyLocked)
        assertTrue(tracker.offer(result(admission = "5678")) is ScanConsensusDecision.AlreadyLocked)
    }

    @Test
    fun cardMustBeAbsentBeforeNextCardCanLock() {
        var nowMs = 0L
        val tracker = ScanConsensusTracker(nowMsProvider = { nowMs })
        repeat(3) { tracker.offer(result(admission = "1234")) }

        tracker.offer(result(success = false, cardVisible = false))
        nowMs = 899L
        assertTrue(
            tracker.offer(result(success = false, cardVisible = false)) is ScanConsensusDecision.Pending,
        )
        nowMs = 900L
        assertTrue(
            tracker.offer(result(success = false, cardVisible = false)) is ScanConsensusDecision.CardCleared,
        )

        repeat(2) { assertTrue(tracker.offer(result(admission = "5678")) is ScanConsensusDecision.Pending) }
        val locked = tracker.offer(result(admission = "5678"))
        assertTrue(locked is ScanConsensusDecision.Locked)
        assertTrue((locked as ScanConsensusDecision.Locked).signature.startsWith("5678|"))
    }

    @Test
    fun signatureIsNullForFailedScorelessOrAnswerlessResults() {
        assertNull(ScanConsensusTracker.signatureOf(result(success = false)))
        assertNull(ScanConsensusTracker.signatureOf(result().copy(score = null)))
        assertNull(ScanConsensusTracker.signatureOf(result().copy(answerArea = null)))
    }

    @Test
    fun customSignatureProviderIsRespected() {
        val tracker = ScanConsensusTracker(
            requiredFrames = 2,
            windowSize = 3,
            signatureProvider = { it.admissionNumber?.digits?.takeIf(String::isNotBlank) },
        )
        tracker.offer(result())
        val second = tracker.offer(result())
        assertTrue(second is ScanConsensusDecision.Locked)
        assertEquals("1234", (second as ScanConsensusDecision.Locked).signature)
    }

    private fun visibleAnchors(): MiniProgramAnchors {
        fun candidate(kind: MiniProgramCornerKind, row: Int, column: Int) =
            MiniProgramCornerCandidate(
                kind = kind,
                point = MiniProgramPoint(row = row, column = column),
                length = 10,
                source = "test",
            )
        val lu = candidate(MiniProgramCornerKind.LU, 0, 0)
        val ld = candidate(MiniProgramCornerKind.LD, 100, 0)
        val ru = candidate(MiniProgramCornerKind.RU, 0, 100)
        val rd = candidate(MiniProgramCornerKind.RD, 100, 100)
        return MiniProgramAnchors(
            lu = lu,
            ld = ld,
            ru = ru,
            rd = rd,
            quadCheck = MiniProgramGeometry.isQuad(lu.point, ld.point, ru.point, rd.point),
        )
    }
}
