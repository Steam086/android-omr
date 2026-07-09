package com.answercard.grader.ui

import com.answercard.grader.miniprogram.AndroidAdmissionNumberReadResult
import com.answercard.grader.miniprogram.AndroidOmrResult
import com.answercard.grader.miniprogram.AndroidOmrScoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDisplayResultTest {
    @Test
    fun mapsSuccessfulAndroidOmrResultToAdmissionNumberAndScore() {
        val display = ScanDisplayResult.fromAndroidOmrResult(
            androidResult(
                success = true,
                examId = "1234",
                totalScore = 10.0,
                maxScore = 10.0,
                failureReason = null,
            ),
        )

        assertTrue(display.isRecognized)
        assertEquals("1234", display.examId)
        assertEquals("10/10", display.scoreText)
        assertEquals(null, display.failureReason)
    }

    @Test
    fun mapsFailedAndroidOmrResultToFailureReason() {
        val display = ScanDisplayResult.fromAndroidOmrResult(
            androidResult(
                success = false,
                examId = null,
                totalScore = null,
                maxScore = null,
                failureReason = "corner anchors not found",
            ),
        )

        assertFalse(display.isRecognized)
        assertEquals(null, display.examId)
        assertEquals(null, display.scoreText)
        assertEquals("corner anchors not found", display.failureReason)
    }

    @Test
    fun mapsCellTooSmallFailureToFriendlyMessage() {
        val display = ScanDisplayResult.fromAndroidOmrResult(
            androidResult(
                success = false,
                examId = null,
                totalScore = null,
                maxScore = null,
                failureReason = "projected cell too small: Q1A=10x8",
            ),
        )

        assertFalse(display.isRecognized)
        assertEquals("识别单元格过小：Q1A=10x8。请靠近拍摄或提高分析分辨率。", display.friendlyMessage)
    }

    private fun androidResult(
        success: Boolean,
        examId: String?,
        totalScore: Double?,
        maxScore: Double?,
        failureReason: String?,
    ): AndroidOmrResult =
        AndroidOmrResult(
            success = success,
            failureReason = failureReason,
            layout = null,
            anchors = null,
            grid = null,
            answerArea = null,
            admissionNumber = examId?.let {
                AndroidAdmissionNumberReadResult(
                    digits = it,
                    digitResults = emptyList(),
                    success = true,
                    failureReason = null,
                    debugInfo = emptyList(),
                )
            },
            score = if (totalScore != null && maxScore != null) {
                AndroidOmrScoreResult(
                    totalScore = totalScore,
                    maxScore = maxScore,
                    items = emptyList(),
                    success = true,
                    warnings = emptyList(),
                )
            } else {
                null
            },
            warnings = emptyList(),
            debugInfo = emptyList(),
        )
}
