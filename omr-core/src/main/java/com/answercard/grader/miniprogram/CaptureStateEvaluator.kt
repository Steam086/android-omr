package com.answercard.grader.miniprogram

enum class CameraFocusState {
    FOCUSED,
    SCANNING,
    UNFOCUSED,
    INACTIVE,
    UNKNOWN,
}

enum class CameraExposureState {
    CONVERGED,
    SEARCHING,
    UNDEREXPOSED,
    INACTIVE,
    UNKNOWN,
}

data class FrameCaptureMetadata(
    val timestampNs: Long,
    val focusState: CameraFocusState,
    val exposureState: CameraExposureState,
    val focusRequired: Boolean,
    val exposureRequired: Boolean,
    val exposureTimeNs: Long?,
    val iso: Int?,
) {
    fun debugInfo(): List<String> = listOf(
        "captureMetadata=available",
        "afState=${focusState.name}",
        "aeState=${exposureState.name}",
        "afRequired=$focusRequired",
        "aeRequired=$exposureRequired",
        "exposureTimeNs=${exposureTimeNs ?: "unknown"}",
        "iso=${iso ?: "unknown"}",
    )
}

data class CaptureGateDecision(
    val accepted: Boolean,
    val rejectionReason: ScanRejectionReason?,
)

object CaptureStateEvaluator {
    fun evaluate(metadata: FrameCaptureMetadata?): CaptureGateDecision {
        if (metadata == null) return CaptureGateDecision(accepted = true, rejectionReason = null)
        if (
            metadata.focusRequired &&
            metadata.focusState !in setOf(CameraFocusState.FOCUSED, CameraFocusState.UNKNOWN)
        ) {
            return CaptureGateDecision(false, ScanRejectionReason.WAIT_FOCUS)
        }
        if (
            metadata.exposureRequired &&
            metadata.exposureState !in setOf(CameraExposureState.CONVERGED, CameraExposureState.UNKNOWN)
        ) {
            return CaptureGateDecision(false, ScanRejectionReason.WAIT_EXPOSURE)
        }
        return CaptureGateDecision(accepted = true, rejectionReason = null)
    }
}
