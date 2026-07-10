package com.answercard.grader.ui

import com.answercard.grader.miniprogram.AndroidAdmissionNumberReadResult
import com.answercard.grader.miniprogram.AndroidAdmissionDigitReadResult
import com.answercard.grader.miniprogram.AndroidAdmissionNumberCandidate
import com.answercard.grader.miniprogram.AndroidAnswerAreaReadResult
import com.answercard.grader.miniprogram.AndroidOmrResult
import com.answercard.grader.miniprogram.ScanRejectionReason
import com.answercard.grader.miniprogram.AndroidOmrScoreItem
import com.answercard.grader.miniprogram.AndroidOmrScoreResult
import com.answercard.grader.miniprogram.AndroidOptionReadResult
import com.answercard.grader.miniprogram.AndroidQuestionReadResult
import com.answercard.grader.miniprogram.MiniProgramBubbleReadResult
import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDisplayResultTest {
    @Test
    fun mapsStableRejectionCodeToActionableRetakeMessageWithoutScore() {
        val rejected = androidResult(
            success = false,
            examId = null,
            totalScore = null,
            maxScore = null,
            failureReason = "frame is too blurry",
        ).copy(rejectionReason = ScanRejectionReason.RETAKE_BLUR)

        val display = ScanDisplayResult.fromAndroidOmrResult(rejected)

        assertEquals("画面模糊，请持稳后重拍。", display.friendlyMessage)
        assertEquals(null, display.scoreText)
    }

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

    @Test
    fun mapsAnswerAreaToTemplateOverlayMarks() {
        val display = ScanDisplayResult.fromAndroidOmrResult(
            androidResult(
                success = false,
                examId = null,
                totalScore = 0.0,
                maxScore = 2.0,
                failureReason = "score warnings: Q1 incorrect",
                answerArea = AndroidAnswerAreaReadResult(
                    questions = listOf(
                        AndroidQuestionReadResult(
                            questionIndex = 0,
                            selectedOptions = listOf(0),
                            selectedLabels = listOf("A"),
                            optionResults = listOf(
                                optionResult(questionIndex = 0, optionIndex = 0, optionLabel = "A", marked = true),
                                optionResult(questionIndex = 0, optionIndex = 1, optionLabel = "B", marked = false),
                            ),
                            isBlank = false,
                            isMultiMarked = false,
                        ),
                    ),
                    failureReason = null,
                    debugInfo = emptyList(),
                ),
                scoreItems = listOf(
                    AndroidOmrScoreItem(
                        questionIndex = 0,
                        expectedLabels = listOf("B"),
                        selectedLabels = listOf("A"),
                        score = 2.0,
                        earnedScore = 0.0,
                        isCorrect = false,
                        isBlank = false,
                        isMultiMarked = false,
                        warning = "incorrect",
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                ScanAnswerMark(
                    questionIndex = 0,
                    optionIndex = 0,
                    optionLabel = "A",
                    state = ScanAnswerMarkState.MARKED_WRONG,
                ),
                ScanAnswerMark(
                    questionIndex = 0,
                    optionIndex = 1,
                    optionLabel = "B",
                    state = ScanAnswerMarkState.MISSED_CORRECT,
                ),
            ),
            display.answerMarks,
        )
    }

    @Test
    fun mapsAdmissionNumberCandidatesToTemplateOverlayMarks() {
        val display = ScanDisplayResult.fromAndroidOmrResult(
            androidResult(
                success = true,
                examId = "5",
                totalScore = 2.0,
                maxScore = 2.0,
                failureReason = null,
                admissionNumber = AndroidAdmissionNumberReadResult(
                    digits = "5",
                    digitResults = listOf(
                        AndroidAdmissionDigitReadResult(
                            digitIndex = 0,
                            selectedNumber = 5,
                            candidates = listOf(
                                admissionCandidate(digitIndex = 0, numberValue = 5, marked = true),
                                admissionCandidate(digitIndex = 0, numberValue = 6, marked = false),
                            ),
                            isBlank = false,
                            isMultiMarked = false,
                            failureReason = null,
                        ),
                    ),
                    success = true,
                    failureReason = null,
                    debugInfo = emptyList(),
                ),
            ),
        )

        assertEquals(
            listOf(
                ScanAdmissionMark(
                    digitIndex = 0,
                    numberValue = 5,
                    state = ScanAdmissionMarkState.SELECTED,
                ),
            ),
            display.admissionMarks,
        )
    }

    @Test
    fun usesTemplateQuestionNumberWhenMappingScoreItemsForNonSequentialQuestions() {
        val template = TemplateState(
            name = "non sequential",
            questions = listOf(
                QuestionSetting(number = 1, answer = "A", score = 2),
                QuestionSetting(number = 3, answer = "B", score = 2),
            ),
        )
        val display = ScanDisplayResult.fromAndroidOmrResult(
            result = androidResult(
                success = false,
                examId = null,
                totalScore = 0.0,
                maxScore = 4.0,
                failureReason = "score warnings: question 3 incorrect",
                answerArea = AndroidAnswerAreaReadResult(
                    questions = listOf(
                        AndroidQuestionReadResult(
                            questionIndex = 1,
                            selectedOptions = listOf(0),
                            selectedLabels = listOf("A"),
                            optionResults = listOf(
                                optionResult(questionIndex = 1, optionIndex = 0, optionLabel = "A", marked = true),
                                optionResult(questionIndex = 1, optionIndex = 1, optionLabel = "B", marked = false),
                            ),
                            isBlank = false,
                            isMultiMarked = false,
                        ),
                    ),
                    failureReason = null,
                    debugInfo = emptyList(),
                ),
                scoreItems = listOf(
                    AndroidOmrScoreItem(
                        questionIndex = 2,
                        expectedLabels = listOf("B"),
                        selectedLabels = listOf("A"),
                        score = 2.0,
                        earnedScore = 0.0,
                        isCorrect = false,
                        isBlank = false,
                        isMultiMarked = false,
                        warning = "incorrect",
                    ),
                ),
            ),
            template = template,
        )

        assertEquals(
            listOf(
                ScanAnswerMark(
                    questionIndex = 1,
                    optionIndex = 0,
                    optionLabel = "A",
                    state = ScanAnswerMarkState.MARKED_WRONG,
                ),
                ScanAnswerMark(
                    questionIndex = 1,
                    optionIndex = 1,
                    optionLabel = "B",
                    state = ScanAnswerMarkState.MISSED_CORRECT,
                ),
            ),
            display.answerMarks,
        )
    }

    @Test
    fun mapsMultiMarkedAdmissionDigitCandidatesAsConflicts() {
        val display = ScanDisplayResult.fromAndroidOmrResult(
            androidResult(
                success = false,
                examId = "5",
                totalScore = null,
                maxScore = null,
                failureReason = "admission number failed: multi marked",
                admissionNumber = AndroidAdmissionNumberReadResult(
                    digits = "5",
                    digitResults = listOf(
                        AndroidAdmissionDigitReadResult(
                            digitIndex = 0,
                            selectedNumber = 5,
                            candidates = listOf(
                                admissionCandidate(digitIndex = 0, numberValue = 5, marked = true),
                                admissionCandidate(digitIndex = 0, numberValue = 6, marked = true),
                            ),
                            isBlank = false,
                            isMultiMarked = true,
                            failureReason = "multi marked",
                        ),
                    ),
                    success = false,
                    failureReason = "multi marked",
                    debugInfo = emptyList(),
                ),
            ),
        )

        assertEquals(
            listOf(
                ScanAdmissionMark(
                    digitIndex = 0,
                    numberValue = 5,
                    state = ScanAdmissionMarkState.CONFLICT,
                ),
                ScanAdmissionMark(
                    digitIndex = 0,
                    numberValue = 6,
                    state = ScanAdmissionMarkState.CONFLICT,
                ),
            ),
            display.admissionMarks,
        )
    }

    private fun androidResult(
        success: Boolean,
        examId: String?,
        totalScore: Double?,
        maxScore: Double?,
        failureReason: String?,
        answerArea: AndroidAnswerAreaReadResult? = null,
        admissionNumber: AndroidAdmissionNumberReadResult? = null,
        scoreItems: List<AndroidOmrScoreItem> = emptyList(),
    ): AndroidOmrResult =
        AndroidOmrResult(
            success = success,
            failureReason = failureReason,
            layout = null,
            anchors = null,
            grid = null,
            answerArea = answerArea,
            admissionNumber = admissionNumber ?: examId?.let {
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
                    items = scoreItems,
                    success = true,
                    warnings = emptyList(),
                )
            } else {
                null
            },
            warnings = emptyList(),
            debugInfo = emptyList(),
        )

    private fun optionResult(
        questionIndex: Int,
        optionIndex: Int,
        optionLabel: String,
        marked: Boolean,
    ): AndroidOptionReadResult =
        AndroidOptionReadResult(
            questionIndex = questionIndex,
            optionIndex = optionIndex,
            optionLabel = optionLabel,
            row = 0,
            column = optionIndex,
            readResult = bubble(marked),
        )

    private fun admissionCandidate(
        digitIndex: Int,
        numberValue: Int,
        marked: Boolean,
    ): AndroidAdmissionNumberCandidate =
        AndroidAdmissionNumberCandidate(
            digitIndex = digitIndex,
            numberValue = numberValue,
            row = digitIndex,
            column = numberValue,
            readResult = bubble(marked),
        )

    private fun bubble(marked: Boolean): MiniProgramBubbleReadResult =
        MiniProgramBubbleReadResult(
            isMarked = marked,
            containCount = if (marked) 10 else 0,
            blackThreshold = 5,
            totalBlackCount = if (marked) 10 else 0,
            centralBlackCount = if (marked) 8 else 0,
            centralMeanGray = if (marked) 80.0 else 255.0,
            solidBoundsWidth = if (marked) 8 else 0,
            solidBoundsHeight = if (marked) 8 else 0,
            cleanedTotalBlackCount = if (marked) 10 else 0,
            noiseComponentsRemoved = 0,
            noisePixelsRemoved = 0,
            componentsKept = if (marked) 1 else 0,
            largestComponentArea = if (marked) 10 else 0,
            sampleRows = 10,
            sampleColumns = 10,
            edgeCleanDirections = emptySet(),
            failureReason = null,
        )
}
