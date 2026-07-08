package com.answercard.grader.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityEvaluatorTest {
    @Test
    fun unstableUntilQuietForRequiredDuration() {
        val evaluator = StabilityEvaluator(maxAngularSpeedRadPerSec = 0.15f, requiredStableDurationMs = 300L)
        evaluator.onSample(timestampMs = 0L, angularSpeed = 0.05f)
        assertFalse(evaluator.isStable(nowMs = 100L))
        assertTrue(evaluator.isStable(nowMs = 300L))
    }

    @Test
    fun shakeResetsTheQuietWindow() {
        val evaluator = StabilityEvaluator()
        evaluator.onSample(0L, 0.02f)
        evaluator.onSample(200L, 0.5f)
        assertFalse(evaluator.isStable(400L))
        evaluator.onSample(450L, 0.02f)
        assertFalse(evaluator.isStable(500L))
        assertTrue(evaluator.isStable(750L))
    }

    @Test
    fun neverStableWithoutAnySample()  {
        val evaluator = StabilityEvaluator()
        assertFalse(evaluator.isStable(10_000L))
    }
}
