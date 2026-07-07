package com.answercard.grader.miniprogram

enum class AndroidPaperTemplateType {
    ANDROID_5X3,
}

data class AndroidPaperGridArea(
    val startRow: Int,
    val endRow: Int,
    val startColumn: Int,
    val endColumn: Int,
)

data class AndroidPaperQuestionMapping(
    val questionIndex: Int,
    val optionIndex: Int,
    val row: Int,
    val column: Int,
)

data class AndroidPaperAdmissionNumberMapping(
    val digitIndex: Int,
    val numberValue: Int,
    val row: Int,
    val column: Int,
)

data class AndroidPaperTemplateLayout(
    val templateType: AndroidPaperTemplateType,
    val gridRows: Int,
    val gridColumns: Int,
    val questionCount: Int,
    val optionCount: Int,
    val admissionNumberDigits: Int,
    val admissionNumberArea: AndroidPaperGridArea,
    val answerArea: AndroidPaperGridArea,
    val questionMappings: List<AndroidPaperQuestionMapping>,
    val admissionNumberMappings: List<AndroidPaperAdmissionNumberMapping>,
    val debugInfo: Map<String, String>,
)
