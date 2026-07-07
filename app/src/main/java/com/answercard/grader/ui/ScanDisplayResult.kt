package com.answercard.grader.ui

import com.answercard.grader.miniprogram.AndroidOmrResult
import com.answercard.grader.vision.OmrScanResult

data class ScanDisplayResult(
    val isRecognized: Boolean,
    val examId: String?,
    val scoreText: String?,
    val failureReason: String?,
    val friendlyMessage: String?,
    val debugInfo: List<String>,
) {
    companion object {
        fun fromAndroidOmrResult(result: AndroidOmrResult): ScanDisplayResult =
            ScanDisplayResult(
                isRecognized = result.success,
                examId = result.admissionNumber?.digits,
                scoreText = result.score?.let { "${formatScore(it.totalScore)}/${formatScore(it.maxScore)}" },
                failureReason = result.failureReason,
                friendlyMessage = friendlyMessage(result.failureReason),
                debugInfo = result.debugInfo,
            )

        fun fromLegacyResult(result: OmrScanResult?): ScanDisplayResult? =
            result?.let {
                ScanDisplayResult(
                    isRecognized = true,
                    examId = it.examId,
                    scoreText = "${it.grade.totalScore}/${it.grade.maxScore}",
                    failureReason = null,
                    friendlyMessage = null,
                    debugInfo = emptyList(),
                )
            }

        private fun formatScore(score: Double): String =
            if (score % 1.0 == 0.0) score.toInt().toString() else score.toString()

        private fun friendlyMessage(failureReason: String?): String? =
            when {
                failureReason == null -> null
                failureReason.contains("corner anchors not found") ||
                    failureReason.contains("invalid card geometry") ->
                    "No valid answer card found. Aim at the four L anchors."
                failureReason.contains("card too small") ->
                    "Card too small or too far. Move closer until the four L anchors nearly fill the preview."
                failureReason.contains("projected cell too small") ->
                    "Projected cell too small: ${failureReason.substringAfter("projected cell too small: ")}. " +
                        "Move closer or increase analysis resolution."
                else -> null
            }
    }
}
