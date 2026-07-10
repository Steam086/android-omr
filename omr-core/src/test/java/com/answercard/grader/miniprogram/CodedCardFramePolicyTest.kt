package com.answercard.grader.miniprogram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodedCardFramePolicyTest {
    @Test
    fun codedPathWithExactlyOneInferredMarkerMaySkipAnchorInset() {
        assertFalse(CodedCardFramePolicy.requiresAnchorBorderInset(AnchorMode.CODED_ONLY, 1))
        assertTrue(CodedCardFramePolicy.requiresAnchorBorderInset(AnchorMode.CODED_ONLY, 0))
        assertTrue(CodedCardFramePolicy.requiresAnchorBorderInset(AnchorMode.LEGACY, 1))
    }
}
