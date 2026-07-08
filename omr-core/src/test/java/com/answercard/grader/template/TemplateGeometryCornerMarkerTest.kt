package com.answercard.grader.template

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateGeometryCornerMarkerTest {
    @Test
    fun markerRectsSitInsideCornerMarginsOfRenderedCard() {
        val layout = TemplateGeometry.buildLayout()
        val rects = TemplateGeometry.cornerMarkerRects(layout)
        val margin = TemplateGeometry.CORNER_BRACKET_MARGIN
        val size = TemplateGeometry.CORNER_MARKER_SIZE
        val right = TemplateGeometry.renderedWidth(layout) - margin - size
        val bottom = TemplateGeometry.renderedHeight(layout) - margin - size

        assertEquals(margin, rects.lu.x, 1e-4f)
        assertEquals(margin, rects.lu.y, 1e-4f)
        assertEquals(right, rects.ru.x, 1e-4f)
        assertEquals(margin, rects.ru.y, 1e-4f)
        assertEquals(margin, rects.ld.x, 1e-4f)
        assertEquals(bottom, rects.ld.y, 1e-4f)
        assertEquals(right, rects.rd.x, 1e-4f)
        assertEquals(bottom, rects.rd.y, 1e-4f)
        listOf(rects.lu, rects.ru, rects.ld, rects.rd).forEach { rect ->
            assertEquals(size, rect.w, 1e-4f)
            assertEquals(size, rect.h, 1e-4f)
        }
    }

    @Test
    fun markerCentersAreRectCenters() {
        val layout = TemplateGeometry.buildLayout()
        val rects = TemplateGeometry.cornerMarkerRects(layout)
        val centers = TemplateGeometry.cornerMarkerCenters(layout)

        assertEquals(rects.lu.x + rects.lu.w / 2f, centers.lu.x, 1e-4f)
        assertEquals(rects.lu.y + rects.lu.h / 2f, centers.lu.y, 1e-4f)
        assertEquals(rects.ru.x + rects.ru.w / 2f, centers.ru.x, 1e-4f)
        assertEquals(rects.rd.y + rects.rd.h / 2f, centers.rd.y, 1e-4f)
        assertEquals(rects.ld.x + rects.ld.w / 2f, centers.ld.x, 1e-4f)
    }
}
