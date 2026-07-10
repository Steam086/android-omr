package com.answercard.grader.miniprogram

enum class OmrAnalysisOrientationMode {
    LANDSCAPE_TEMPLATE,
    FOLLOW_IMAGE_ROTATION,
    PORTRAIT_TEMPLATE,
}

data class AnalysisResolution(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0) { "Analysis resolution must be positive" }
    }

    fun accepts(actualWidth: Int, actualHeight: Int): Boolean {
        val actualLong = maxOf(actualWidth, actualHeight)
        val actualShort = minOf(actualWidth, actualHeight)
        return actualLong >= maxOf(width, height) && actualShort >= minOf(width, height)
    }

    override fun toString(): String = "${width}x$height"
}

data class AndroidOmrAnalyzerOptions(
    val minAnalyzeIntervalMs: Long = 300L,
    val candidateWindowMs: Long = 0L,
    val enableFrameQualityGate: Boolean = true,
    val analysisOrientationMode: OmrAnalysisOrientationMode = OmrAnalysisOrientationMode.LANDSCAPE_TEMPLATE,
    val requestedAnalysisResolutionLabel: String? = null,
    val minimumAnalysisResolution: AnalysisResolution? = null,
) {
    init {
        require(minAnalyzeIntervalMs >= 0L) { "minAnalyzeIntervalMs must be non-negative" }
        require(candidateWindowMs >= 0L) { "candidateWindowMs must be non-negative" }
    }
}
