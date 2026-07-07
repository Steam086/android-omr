package com.answercard.grader.miniprogram

enum class MiniProgramEdgeCleanDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN,
}

data class MiniProgramBubbleReadResult(
    val isMarked: Boolean,
    val containCount: Int,
    val blackThreshold: Int,
    val totalBlackCount: Int,
    val centralBlackCount: Int,
    val cleanedTotalBlackCount: Int,
    val noiseComponentsRemoved: Int,
    val noisePixelsRemoved: Int,
    val componentsKept: Int,
    val largestComponentArea: Int,
    val sampleRows: Int,
    val sampleColumns: Int,
    val edgeCleanDirections: Set<MiniProgramEdgeCleanDirection>,
    val failureReason: String?,
    val debugMatrix: List<String> = emptyList(),
)
