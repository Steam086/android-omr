package com.answercard.grader.camera

import android.util.Range
import android.util.Size

object CameraAnalysisConfig {
    val RequestedResolution: Size = Size(1280, 960)
    const val RequestedResolutionLabel: String = "1280x960"

    // Auto-exposure frame-rate floor. Pinning the AE target to a steady 30fps caps the maximum
    // exposure time at ~1/30s, so the sensor cannot lengthen the shutter in dim light — that
    // long shutter is the main cause of motion blur. Trade-off: dim scenes come out darker and
    // noisier instead of blurred, which is the right bias for reading crisp answer marks.
    val TargetFrameRateRange: Range<Int> = Range(30, 30)

    // Minimum Laplacian-variance sharpness a full-resolution analysis frame must reach to be
    // accepted. Set conservatively low so a properly focused answer sheet always passes and
    // only clearly smeared / out-of-focus frames are rejected. The analyzer logs the measured
    // value as `laplacianVariance=` in the debug panel — raise this toward that range to make
    // the blur gate stricter once real on-device numbers are known.
    const val MinFrameSharpness: Double = 20.0
}
