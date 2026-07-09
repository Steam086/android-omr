package com.answercard.grader.miniprogram

import com.answercard.grader.template.CodedCornerMarker
import com.answercard.grader.template.CornerMarkerId
import com.answercard.grader.template.CornerMarkerStyle
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopTemplateCardRendererMarkerTest {
    @Test
    fun solidSquareModeFillsMarkerRectsAndSkipsBracketArms() {
        val renderer = DesktopTemplateCardRenderer(
            template = TemplateState.default(),
            scale = 3f,
            markerStyle = CornerMarkerStyle.SOLID_SQUARE,
        )
        val frame = renderer.frame()
        val layout = TemplateGeometry.buildLayout(TemplateState.default())
        val rects = TemplateGeometry.cornerMarkerRects(layout)
        val scale = 3f

        // Center of each marker square is black.
        listOf(rects.lu, rects.ru, rects.ld, rects.rd).forEach { rect ->
            val row = ((rect.y + rect.h / 2f) * scale).toInt()
            val column = ((rect.x + rect.w / 2f) * scale).toInt()
            assertTrue("marker center should be dark", frame[row, column] < 100)
        }

        // A point on the old bracket top arm beyond the square (margin+30, margin+4) is white now.
        val armRow = ((TemplateGeometry.CORNER_BRACKET_MARGIN + 4f) * scale).toInt()
        val armColumn = ((TemplateGeometry.CORNER_BRACKET_MARGIN + 30f) * scale).toInt()
        assertTrue("bracket arm area should be white in square mode", frame[armRow, armColumn] > 200)
    }

    @Test
    fun defaultModeDrawsCodedMarkers() {
        val renderer = DesktopTemplateCardRenderer(template = TemplateState.default(), scale = 3f)
        val frame = renderer.frame()
        val layout = TemplateGeometry.buildLayout(TemplateState.default())
        val scale = 3f
        CornerMarkerId.entries.forEach { id ->
            val rect = TemplateGeometry.cornerMarkerRect(layout, id)
            val module = rect.w / CodedCornerMarker.GRID_SIZE
            for (row in 0 until CodedCornerMarker.GRID_SIZE) {
                for (column in 0 until CodedCornerMarker.GRID_SIZE) {
                    val sampleRow = ((rect.y + (row + 0.5f) * module) * scale).toInt()
                    val sampleColumn = ((rect.x + (column + 0.5f) * module) * scale).toInt()
                    val expectedDark = CodedCornerMarker.isDark(id, row, column)
                    assertTrue(
                        "$id cell $row,$column",
                        if (expectedDark) frame[sampleRow, sampleColumn] < 100 else frame[sampleRow, sampleColumn] > 200,
                    )
                }
            }
        }
        val armRow = ((TemplateGeometry.CORNER_BRACKET_MARGIN + 4f) * scale).toInt()
        val armColumn = ((TemplateGeometry.CORNER_BRACKET_MARGIN + 30f) * scale).toInt()
        assertTrue("old bracket arm area should remain white", frame[armRow, armColumn] > 200)
    }
}
