package com.answercard.grader.camera

class StabilityEvaluator(
    private val maxAngularSpeedRadPerSec: Float = 0.15f,
    private val requiredStableDurationMs: Long = 300L,
) {
    private var stableSinceMs: Long? = null

    @Synchronized
    fun onSample(timestampMs: Long, angularSpeed: Float) {
        stableSinceMs = if (angularSpeed >= maxAngularSpeedRadPerSec) {
            null
        } else {
            stableSinceMs ?: timestampMs
        }
    }

    @Synchronized
    fun isStable(nowMs: Long): Boolean {
        val since = stableSinceMs ?: return false
        return nowMs - since >= requiredStableDurationMs
    }
}
