package com.answercard.grader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanGuideGeometryTest {
    @Test
    fun wideCardGuideFitsInsidePortraitViewfinder() {
        val rect = ScanGuideGeometry.calculate(
            viewWidth = 900f,
            viewHeight = 1200f,
            cardAspectRatio = 2f,
        )

        assertEquals(810f, rect.width, 0.01f)
        assertEquals(405f, rect.height, 0.01f)
        assertEquals(45f, rect.left, 0.01f)
        assertEquals(397.5f, rect.top, 0.01f)
    }

    @Test
    fun tallTemplateGuideIsLimitedByAvailableHeight() {
        val rect = ScanGuideGeometry.calculate(
            viewWidth = 900f,
            viewHeight = 1200f,
            cardAspectRatio = 0.6f,
        )

        assertTrue(rect.left >= 0f)
        assertTrue(rect.top >= 0f)
        assertTrue(rect.right <= 900f)
        assertTrue(rect.bottom <= 1200f)
    }
}
