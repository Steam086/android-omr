package com.answercard.grader.miniprogram

data class AndroidAdmissionNumberReadResult(
    val digits: String,
    val digitResults: List<AndroidAdmissionDigitReadResult>,
    val success: Boolean,
    val failureReason: String?,
    val debugInfo: List<String>,
)

data class AndroidAdmissionDigitReadResult(
    val digitIndex: Int,
    val selectedNumber: Int?,
    val candidates: List<AndroidAdmissionNumberCandidate>,
    val isBlank: Boolean,
    val isMultiMarked: Boolean,
    val failureReason: String?,
)

data class AndroidAdmissionNumberCandidate(
    val digitIndex: Int,
    val numberValue: Int,
    val row: Int,
    val column: Int,
    val readResult: MiniProgramBubbleReadResult,
)
