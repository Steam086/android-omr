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
                    "未找到有效答题卡，请对准四个 L 形定位点。"
                failureReason.contains("card too small") ->
                    "答题卡太小或距离过远，请靠近拍摄，让定位点充满画面。"
                failureReason.contains("projected cell too small") ->
                    "识别单元格过小：${failureReason.substringAfter("projected cell too small: ")}。" +
                        "请靠近拍摄或提高分析分辨率。"
                else -> null
            }
    }
}
