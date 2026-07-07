package com.answercard.grader.miniprogram

enum class OmrAnalysisOrientationMode {
    LANDSCAPE_TEMPLATE,
    FOLLOW_IMAGE_ROTATION,
    PORTRAIT_TEMPLATE,
}

data class AndroidOmrAnalyzerOptions(
    val minAnalyzeIntervalMs: Long = 300L,
    val analysisOrientationMode: OmrAnalysisOrientationMode = OmrAnalysisOrientationMode.LANDSCAPE_TEMPLATE,
    val requestedAnalysisResolutionLabel: String? = null,
) {
    init {
        require(minAnalyzeIntervalMs >= 0L) { "minAnalyzeIntervalMs must be non-negative" }
    }
}
