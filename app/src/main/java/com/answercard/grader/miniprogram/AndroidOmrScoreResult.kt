package com.answercard.grader.miniprogram

data class AndroidOmrScoreResult(
    val totalScore: Double,
    val maxScore: Double,
    val items: List<AndroidOmrScoreItem>,
    val success: Boolean,
    val warnings: List<String>,
)

data class AndroidOmrScoreItem(
    val questionIndex: Int,
    val expectedLabels: List<String>,
    val selectedLabels: List<String>,
    val score: Double,
    val earnedScore: Double,
    val isCorrect: Boolean,
    val isBlank: Boolean,
    val isMultiMarked: Boolean,
    val warning: String?,
)
