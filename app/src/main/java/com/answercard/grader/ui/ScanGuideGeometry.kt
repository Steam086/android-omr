package com.answercard.grader.ui

data class ScanGuideRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

object ScanGuideGeometry {
    fun calculate(
        viewWidth: Float,
        viewHeight: Float,
        cardAspectRatio: Float,
        insetFraction: Float = 0.05f,
    ): ScanGuideRect {
        require(viewWidth > 0f && viewHeight > 0f) { "Viewfinder dimensions must be positive" }
        require(cardAspectRatio > 0f) { "Card aspect ratio must be positive" }
        require(insetFraction in 0f..<0.5f) { "Inset fraction must leave visible space" }

        val maxWidth = viewWidth * (1f - insetFraction * 2f)
        val maxHeight = viewHeight * (1f - insetFraction * 2f)
        val width = minOf(maxWidth, maxHeight * cardAspectRatio)
        val height = width / cardAspectRatio
        val left = (viewWidth - width) / 2f
        val top = (viewHeight - height) / 2f
        return ScanGuideRect(left, top, left + width, top + height)
    }
}
