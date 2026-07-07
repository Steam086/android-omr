package com.answercard.grader.miniprogram

data class AndroidAnswerAreaReadResult(
    val questions: List<AndroidQuestionReadResult>,
    val failureReason: String?,
    val debugInfo: List<String>,
)

data class AndroidQuestionReadResult(
    val questionIndex: Int,
    val selectedOptions: List<Int>,
    val selectedLabels: List<String>,
    val optionResults: List<AndroidOptionReadResult>,
    val isBlank: Boolean,
    val isMultiMarked: Boolean,
)

data class AndroidOptionReadResult(
    val questionIndex: Int,
    val optionIndex: Int,
    val optionLabel: String,
    val row: Int,
    val column: Int,
    val readResult: MiniProgramBubbleReadResult,
)
