package com.answercard.grader.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateGeometryShowHeaderTest {
    @Test
    fun defaultTemplateKeepsHeaderLayout() {
        val layout = TemplateGeometry.buildLayout(TemplateState.default())

        assertTrue(layout.showHeader)
        assertEquals(4, layout.examIdRows.size)
        assertEquals(216f, layout.height, 0.001f)
    }

    @Test
    fun headerlessTemplateDropsExamRowsAndShrinksHeight() {
        val layout = TemplateGeometry.buildLayout(TemplateState.default().withShowHeader(false))

        assertFalse(layout.showHeader)
        assertTrue(layout.examIdRows.isEmpty())
        assertEquals(112f, layout.height, 0.001f)
        assertEquals(540f, layout.width, 0.001f)
    }

    @Test
    fun headerlessQuestionsShiftUpByHeaderOffset() {
        val header = TemplateGeometry.buildLayout(TemplateState.default())
        val headerless = TemplateGeometry.buildLayout(TemplateState.default().withShowHeader(false))
        val headerOffset = TemplateGeometry.HEADER_HEIGHT + TemplateGeometry.HEADER_DIVIDER_GAP

        header.options.zip(headerless.options).forEach { (withHeader, compact) ->
            assertEquals(withHeader.rect.x, compact.rect.x, 0.001f)
            assertEquals(withHeader.rect.y - headerOffset, compact.rect.y, 0.001f)
        }
        val firstOption = headerless.options.single { it.question == 1 && it.option == "A" }
        assertEquals(20f, firstOption.rect.y, 0.001f)
    }

    @Test
    fun headerlessRenderedSizeShrinksWithCorners() {
        val header = TemplateGeometry.buildLayout(TemplateState.default())
        val headerless = TemplateGeometry.buildLayout(TemplateState.default().withShowHeader(false))

        assertEquals(TemplateGeometry.renderedWidth(header), TemplateGeometry.renderedWidth(headerless), 0.001f)
        assertEquals(192f, TemplateGeometry.renderedHeight(headerless), 0.001f)

        val anchors = TemplateGeometry.cornerAnchorReference(headerless)
        assertEquals(TemplateGeometry.CORNER_BRACKET_MARGIN, anchors.lu.y, 0.001f)
        assertEquals(
            192f - TemplateGeometry.CORNER_BRACKET_MARGIN - TemplateGeometry.CORNER_BRACKET_THICKNESS,
            anchors.ld.y,
            0.001f,
        )
    }
}
