package com.answercard.grader.camera

import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import com.answercard.grader.miniprogram.CameraExposureState
import com.answercard.grader.miniprogram.CameraFocusState
import com.answercard.grader.miniprogram.FrameCaptureMetadata

class CameraCaptureMetadataTracker {
    private val frames = object : LinkedHashMap<Long, FrameCaptureMetadata>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, FrameCaptureMetadata>?): Boolean =
            size > MAX_CAPTURE_RESULTS
    }

    @Synchronized
    fun record(result: TotalCaptureResult) {
        val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
        val afMode = result.get(CaptureResult.CONTROL_AF_MODE)
        val aeMode = result.get(CaptureResult.CONTROL_AE_MODE)
        frames[timestamp] = FrameCaptureMetadata(
            timestampNs = timestamp,
            focusState = focusState(result.get(CaptureResult.CONTROL_AF_STATE)),
            exposureState = exposureState(result.get(CaptureResult.CONTROL_AE_STATE)),
            focusRequired = afMode != null && afMode !in setOf(
                CaptureRequest.CONTROL_AF_MODE_OFF,
                CaptureRequest.CONTROL_AF_MODE_EDOF,
            ),
            exposureRequired = aeMode != null && aeMode != CaptureRequest.CONTROL_AE_MODE_OFF,
            exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
        )
    }

    @Synchronized
    fun metadataFor(timestampNs: Long): FrameCaptureMetadata? {
        frames.remove(timestampNs)?.let { return it }
        val nearest = frames.entries.minByOrNull { (_, metadata) ->
            kotlin.math.abs(metadata.timestampNs - timestampNs)
        } ?: return null
        if (kotlin.math.abs(nearest.key - timestampNs) > MAX_TIMESTAMP_DELTA_NS) return null
        frames.remove(nearest.key)
        return nearest.value
    }

    @Synchronized
    fun clear() {
        frames.clear()
    }

    private fun focusState(state: Int?): CameraFocusState =
        when (state) {
            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED,
            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
            -> CameraFocusState.FOCUSED
            CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN,
            CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN,
            -> CameraFocusState.SCANNING
            CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED,
            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED,
            -> CameraFocusState.UNFOCUSED
            CaptureResult.CONTROL_AF_STATE_INACTIVE -> CameraFocusState.INACTIVE
            else -> CameraFocusState.UNKNOWN
        }

    private fun exposureState(state: Int?): CameraExposureState =
        when (state) {
            CaptureResult.CONTROL_AE_STATE_CONVERGED,
            CaptureResult.CONTROL_AE_STATE_LOCKED,
            -> CameraExposureState.CONVERGED
            CaptureResult.CONTROL_AE_STATE_SEARCHING,
            CaptureResult.CONTROL_AE_STATE_PRECAPTURE,
            -> CameraExposureState.SEARCHING
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED -> CameraExposureState.UNDEREXPOSED
            CaptureResult.CONTROL_AE_STATE_INACTIVE -> CameraExposureState.INACTIVE
            else -> CameraExposureState.UNKNOWN
        }

    private companion object {
        const val MAX_CAPTURE_RESULTS = 48
        const val MAX_TIMESTAMP_DELTA_NS = 2_000_000L
    }
}
