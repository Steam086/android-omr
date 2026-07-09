package com.answercard.grader.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityEvaluatorTest {
    @Test
    fun unstableUntilQuietForRequiredDuration() {
        val evaluator = StabilityEvaluator(maxAngularSpeedRadPerSec = 0.15f, requiredStableDurationMs = 300L)
        evaluator.onGyroscopeSample(timestampMs = 0L, angularSpeed = 0.05f)
        assertFalse(evaluator.isStable(nowMs = 100L))
        assertTrue(evaluator.isStable(nowMs = 300L))
    }

    @Test
    fun shakeResetsTheQuietWindow() {
        val evaluator = StabilityEvaluator()
        evaluator.onGyroscopeSample(0L, 0.02f)
        evaluator.onGyroscopeSample(200L, 0.5f)
        assertFalse(evaluator.isStable(400L))
        evaluator.onGyroscopeSample(450L, 0.02f)
        assertFalse(evaluator.isStable(500L))
        assertTrue(evaluator.isStable(750L))
    }

    @Test
    fun neverStableWithoutAnySample() {
        val evaluator = StabilityEvaluator()
        assertFalse(evaluator.isStable(10_000L))
    }

    @Test
    fun linearAccelerationSpikeResetsQuietWindow() {
        val evaluator = StabilityEvaluator(
            maxAngularSpeedRadPerSec = 0.15f,
            maxLinearAccelMetersPerSec2 = 0.30f,
            requiredStableDurationMs = 300L,
        )
        // Gyroscope reports no rotation the entire time...
        evaluator.onGyroscopeSample(0L, 0.02f)
        // ...but a purely translational jolt must still break the quiet window,
        // because sliding the phone blurs the frame without rotating it.
        evaluator.onLinearAccelerationSample(200L, 0.9f)
        assertFalse(evaluator.isStable(400L))
        // Once the jolt settles, stability rebuilds from the moment it went quiet again.
        evaluator.onLinearAccelerationSample(450L, 0.05f)
        assertFalse(evaluator.isStable(500L))
        assertTrue(evaluator.isStable(750L))
    }

    @Test
    fun steadyGyroscopeAndAccelerationBecomeStableTogether() {
        val evaluator = StabilityEvaluator(requiredStableDurationMs = 300L)
        evaluator.onGyroscopeSample(0L, 0.01f)
        evaluator.onLinearAccelerationSample(0L, 0.02f)
        assertTrue(evaluator.isStable(300L))
    }
}
