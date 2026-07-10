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
    val rejectionReason: ScanRejectionReason? = null,
) {
    companion object {
        fun rejected(
            reason: ScanRejectionReason,
            message: String,
            layout: AndroidPaperTemplateLayout? = null,
            anchors: MiniProgramAnchors? = null,
            grid: MiniProgramGrid? = null,
            debugInfo: List<String> = emptyList(),
        ): AndroidOmrResult =
            AndroidOmrResult(
                success = false,
                failureReason = message,
                layout = layout,
                anchors = anchors,
                grid = grid,
                answerArea = null,
                admissionNumber = null,
                score = null,
                warnings = emptyList(),
                debugInfo = debugInfo + "rejectionReason=${reason.name}",
                rejectionReason = reason,
            )
    }
}
