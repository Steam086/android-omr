package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureStateEvaluatorTest {
    @Test
    fun acceptsConvergedAutofocusAndExposure() {
        val decision = CaptureStateEvaluator.evaluate(metadata(CameraFocusState.FOCUSED, CameraExposureState.CONVERGED))

        assertTrue(decision.accepted)
    }

    @Test
    fun waitsWhileAutofocusIsScanning() {
        val decision = CaptureStateEvaluator.evaluate(metadata(CameraFocusState.SCANNING, CameraExposureState.CONVERGED))

        assertEquals(ScanRejectionReason.WAIT_FOCUS, decision.rejectionReason)
    }

    @Test
    fun waitsWhileAutoExposureIsSearching() {
        val decision = CaptureStateEvaluator.evaluate(metadata(CameraFocusState.FOCUSED, CameraExposureState.SEARCHING))

        assertEquals(ScanRejectionReason.WAIT_EXPOSURE, decision.rejectionReason)
    }

    @Test
    fun fixedFocusAndUnavailableMetadataFallBackToPixelQuality() {
        assertTrue(
            CaptureStateEvaluator.evaluate(
                metadata(
                    focus = CameraFocusState.INACTIVE,
                    exposure = CameraExposureState.UNKNOWN,
                    focusRequired = false,
                    exposureRequired = false,
                ),
            ).accepted,
        )
        assertTrue(CaptureStateEvaluator.evaluate(null).accepted)
    }

    private fun metadata(
        focus: CameraFocusState,
        exposure: CameraExposureState,
        focusRequired: Boolean = true,
        exposureRequired: Boolean = true,
    ): FrameCaptureMetadata =
        FrameCaptureMetadata(
            timestampNs = 1L,
            focusState = focus,
            exposureState = exposure,
            focusRequired = focusRequired,
            exposureRequired = exposureRequired,
            exposureTimeNs = 10_000_000L,
            iso = 100,
        )
}
