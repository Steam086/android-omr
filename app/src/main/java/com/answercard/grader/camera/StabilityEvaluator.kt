package com.answercard.grader.camera

/**
 * Tracks whether the device has been held still long enough to capture a sharp frame.
 *
 * Two independent motion sources feed the same "quiet window": the gyroscope catches
 * rotation / hand shake, while the linear accelerometer catches pure translation (sliding
 * the phone), which does not register on the gyroscope but still smears close-range frames.
 * A spike on *either* source resets the window; the device is only "stable" once both have
 * stayed under their thresholds continuously for [requiredStableDurationMs].
 */
class StabilityEvaluator(
    private val maxAngularSpeedRadPerSec: Float = 0.15f,
    private val maxLinearAccelMetersPerSec2: Float = 0.30f,
    private val requiredStableDurationMs: Long = 300L,
) {
    private var stableSinceMs: Long? = null

    @Synchronized
    fun onGyroscopeSample(timestampMs: Long, angularSpeed: Float) {
        updateQuietWindow(timestampMs, exceeded = angularSpeed >= maxAngularSpeedRadPerSec)
    }

    @Synchronized
    fun onLinearAccelerationSample(timestampMs: Long, linearAccel: Float) {
        updateQuietWindow(timestampMs, exceeded = linearAccel >= maxLinearAccelMetersPerSec2)
    }

    private fun updateQuietWindow(timestampMs: Long, exceeded: Boolean) {
        stableSinceMs = if (exceeded) null else stableSinceMs ?: timestampMs
    }

    @Synchronized
    fun isStable(nowMs: Long): Boolean {
        val since = stableSinceMs ?: return false
        return nowMs - since >= requiredStableDurationMs
    }
}
