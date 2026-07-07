package com.answercard.grader.miniprogram

data class AndroidOmrResult(
    val success: Boolean,
    val failureReason: String?,
    val layout: AndroidPaperTemplateLayout?,
    val anchors: MiniProgramAnchors?,
    val grid: MiniProgramGrid?,
    val answerArea: AndroidAnswerAreaReadResult?,
    val admissionNumber: AndroidAdmissionNumberReadResult?,
    val score: AndroidOmrScoreResult?,
    val warnings: List<String>,
    val debugInfo: List<String>,
)
