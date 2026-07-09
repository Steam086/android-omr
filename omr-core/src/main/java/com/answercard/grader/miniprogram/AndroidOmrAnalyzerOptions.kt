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
    // Minimum Laplacian-variance sharpness a frame must reach to be analyzed. Frames below
    // this are treated as motion-blurred / out-of-focus and dropped before the OMR pipeline.
    // 0.0 disables the gate (any frame passes); the running app raises it to a tuned value.
    val minLaplacianVariance: Double = 0.0,
) {
    init {
        require(minAnalyzeIntervalMs >= 0L) { "minAnalyzeIntervalMs must be non-negative" }
        require(minLaplacianVariance >= 0.0) { "minLaplacianVariance must be non-negative" }
    }
}
