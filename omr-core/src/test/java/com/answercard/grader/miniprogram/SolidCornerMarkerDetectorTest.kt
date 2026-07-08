package com.answercard.grader.miniprogram

import com.answercard.grader.template.CornerMarkerStyle
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolidCornerMarkerDetectorTest {
    @Test
    fun findsFourMarkerCentroidsOnSquareCard() {
        val template = TemplateState.default()
        val renderer = DesktopTemplateCardRenderer(template, scale = 3f, markerStyle = CornerMarkerStyle.SOLID_SQUARE)
        renderer.markAnswer(1, "A")
        renderer.markAdmissionNumber("1234")
        val frame = renderer.frame()
        val layout = TemplateGeometry.buildLayout(template)
        val centers = TemplateGeometry.cornerMarkerCenters(layout)

        val anchors = SolidCornerMarkerDetector.findAnchors(
            frame = frame,
            expectedAspectRatio = TemplateGeometry.renderedWidth(layout).toDouble() /
                TemplateGeometry.renderedHeight(layout).toDouble(),
        )

        assertNotNull(anchors)
        val scale = 3f
        fun assertNear(point: MiniProgramPoint, x: Float, y: Float) {
            assertTrue("row ${point.row} vs ${(y * scale)}", abs(point.row - y * scale) <= 2.0)
            assertTrue("column ${point.column} vs ${(x * scale)}", abs(point.column - x * scale) <= 2.0)
        }
        assertNear(anchors!!.lu.point, centers.lu.x, centers.lu.y)
        assertNear(anchors.ru.point, centers.ru.x, centers.ru.y)
        assertNear(anchors.ld.point, centers.ld.x, centers.ld.y)
        assertNear(anchors.rd.point, centers.rd.x, centers.rd.y)
        assertEquals(SolidCornerMarkerDetector.SOURCE, anchors.lu.source)
        assertEquals(SolidCornerMarkerDetector.SOURCE, anchors.rd.source)
    }

    @Test
    fun returnsNullOnLBracketCard() {
        val renderer = DesktopTemplateCardRenderer(TemplateState.default(), scale = 3f)
        val anchors = SolidCornerMarkerDetector.findAnchors(renderer.frame())
        assertNull(anchors)
    }

    @Test
    fun answerMarksDoNotDisplaceCornerMarkers() {
        val template = TemplateState.default()
        val renderer = DesktopTemplateCardRenderer(template, scale = 3f, markerStyle = CornerMarkerStyle.SOLID_SQUARE)
        template.questions.forEach { question -> renderer.markAnswer(question.number, "A") }
        val frame = renderer.frame()
        val layout = TemplateGeometry.buildLayout(template)
        val centers = TemplateGeometry.cornerMarkerCenters(layout)

        val anchors = SolidCornerMarkerDetector.findAnchors(frame)

        assertNotNull(anchors)
        assertTrue(abs(anchors!!.lu.point.column - centers.lu.x * 3f) <= 2.0)
        assertTrue(abs(anchors.rd.point.row - centers.rd.y * 3f) <= 2.0)
    }
}
