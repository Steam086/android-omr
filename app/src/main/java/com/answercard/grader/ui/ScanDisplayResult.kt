package com.answercard.grader.ui

import com.answercard.grader.miniprogram.AndroidOmrResult
import com.answercard.grader.miniprogram.ScanRejectionReason
import com.answercard.grader.template.TemplateState
import com.answercard.grader.vision.OmrScanResult

data class ScanDisplayResult(
    val isRecognized: Boolean,
    val examId: String?,
    val scoreText: String?,
    val failureReason: String?,
    val friendlyMessage: String?,
    val debugInfo: List<String>,
    val answerMarks: List<ScanAnswerMark> = emptyList(),
    val admissionMarks: List<ScanAdmissionMark> = emptyList(),
) {
    companion object {
        fun fromAndroidOmrResult(
            result: AndroidOmrResult,
            template: TemplateState? = null,
        ): ScanDisplayResult =
            ScanDisplayResult(
                isRecognized = result.success,
                examId = result.admissionNumber?.digits,
                scoreText = result.score?.let { "${formatScore(it.totalScore)}/${formatScore(it.maxScore)}" },
                failureReason = result.failureReason,
                friendlyMessage = friendlyMessage(result.rejectionReason, result.failureReason),
                debugInfo = result.debugInfo,
                answerMarks = answerMarks(result, template),
                admissionMarks = admissionMarks(result),
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

        private fun answerMarks(
            result: AndroidOmrResult,
            template: TemplateState?,
        ): List<ScanAnswerMark> {
            val scoreItemsByQuestion = result.score?.items.orEmpty().associateBy { it.questionIndex }
            return result.answerArea?.questions.orEmpty().flatMap { question ->
                val scoreItem = scoreItemsByQuestion[scoreQuestionIndex(question.questionIndex, template)]
                val expectedLabels = scoreItem?.expectedLabels.orEmpty().toSet()
                question.optionResults.mapNotNull { option ->
                    val state = answerMarkState(
                        isMarked = option.readResult.isMarked,
                        optionLabel = option.optionLabel,
                        expectedLabels = expectedLabels,
                    )
                    if (state == ScanAnswerMarkState.UNMARKED) {
                        null
                    } else {
                        ScanAnswerMark(
                            questionIndex = option.questionIndex,
                            optionIndex = option.optionIndex,
                            optionLabel = option.optionLabel,
                            state = state,
                        )
                    }
                }
            }
        }

        private fun scoreQuestionIndex(questionIndex: Int, template: TemplateState?): Int {
            val questionNumber = template?.questions?.getOrNull(questionIndex)?.number
            return questionNumber?.minus(1) ?: questionIndex
        }

        private fun answerMarkState(
            isMarked: Boolean,
            optionLabel: String,
            expectedLabels: Set<String>,
        ): ScanAnswerMarkState =
            when {
                isMarked && expectedLabels.isEmpty() -> ScanAnswerMarkState.MARKED_UNKNOWN
                isMarked && optionLabel in expectedLabels -> ScanAnswerMarkState.MARKED_CORRECT
                isMarked -> ScanAnswerMarkState.MARKED_WRONG
                optionLabel in expectedLabels -> ScanAnswerMarkState.MISSED_CORRECT
                else -> ScanAnswerMarkState.UNMARKED
            }

        private fun admissionMarks(result: AndroidOmrResult): List<ScanAdmissionMark> =
            result.admissionNumber?.digitResults.orEmpty().flatMap { digit ->
                digit.candidates.mapNotNull { candidate ->
                    val isSelected = digit.selectedNumber == candidate.numberValue
                    val isMarked = candidate.readResult.isMarked
                    val state = when {
                        isMarked && digit.isMultiMarked -> ScanAdmissionMarkState.CONFLICT
                        isSelected -> ScanAdmissionMarkState.SELECTED
                        isMarked -> ScanAdmissionMarkState.MARKED
                        else -> null
                    }
                    state?.let {
                        ScanAdmissionMark(
                            digitIndex = candidate.digitIndex,
                            numberValue = candidate.numberValue,
                            state = it,
                        )
                    }
                }
            }

        private fun formatScore(score: Double): String =
            if (score % 1.0 == 0.0) score.toInt().toString() else score.toString()

        private fun friendlyMessage(
            rejectionReason: ScanRejectionReason?,
            failureReason: String?,
        ): String? =
            when {
                rejectionReason == ScanRejectionReason.WAIT_STABILITY -> "请持稳手机。"
                rejectionReason == ScanRejectionReason.WAIT_FOCUS -> "正在对焦，请保持不动。"
                rejectionReason == ScanRejectionReason.WAIT_EXPOSURE -> "正在调整曝光，请保持不动。"
                rejectionReason == ScanRejectionReason.RETAKE_BLUR -> "画面模糊，请持稳后重拍。"
                rejectionReason == ScanRejectionReason.RETAKE_EXPOSURE -> "光线过强或过暗，请调整光线后重拍。"
                rejectionReason == ScanRejectionReason.RETAKE_CODED_MARKERS ->
                    "角标不清晰，请完整对准四个编码角标后重拍。"
                rejectionReason == ScanRejectionReason.RETAKE_LEGACY_MARKERS ->
                    "未可靠找到旧卡角标，请完整对准四角后重拍。"
                rejectionReason == ScanRejectionReason.LEGACY_ANCHOR_AMBIGUOUS ->
                    "旧卡定位有歧义，请调整角度和光线后重拍。"
                rejectionReason == ScanRejectionReason.RETAKE_CARD_GEOMETRY ->
                    "答题卡位置或透视不可靠，请对正后重拍。"
                rejectionReason == ScanRejectionReason.RETAKE_CELL_SIZE ->
                    "答题卡距离过远，请靠近后重拍。"
                rejectionReason == ScanRejectionReason.RETAKE_READ ->
                    "答题区存在不确定内容，请重拍或人工确认。"
                rejectionReason == ScanRejectionReason.INVALID_TEMPLATE -> "当前答题卡模板无效。"
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

data class ScanAnswerMark(
    val questionIndex: Int,
    val optionIndex: Int,
    val optionLabel: String,
    val state: ScanAnswerMarkState,
)

enum class ScanAnswerMarkState {
    MARKED_CORRECT,
    MARKED_WRONG,
    MISSED_CORRECT,
    MARKED_UNKNOWN,
    UNMARKED,
}

data class ScanAdmissionMark(
    val digitIndex: Int,
    val numberValue: Int,
    val state: ScanAdmissionMarkState,
)

enum class ScanAdmissionMarkState {
    SELECTED,
    MARKED,
    CONFLICT,
}
