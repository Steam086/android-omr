package com.answercard.grader.miniprogram

import com.answercard.grader.template.CornerMarkerId
import com.answercard.grader.template.CornerMarkerStyle
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodedCornerMarkerDetectorTest {
    @Test
    fun decodesFourUniqueMarkersAndReturnsTheirCenters() {
        val template = TemplateState.default()
        val layout = TemplateGeometry.buildLayout(template)
        val renderer = DesktopTemplateCardRenderer(template, scale = 3f, markerStyle = CornerMarkerStyle.CODED)

        val match = CodedCornerMarkerDetector.findAnchorsWithDiagnostics(renderer.frame(), layout)

        assertNotNull(match.anchors)
        assertEquals(CornerMarkerId.entries.toList(), match.diagnostics.detectedIds)
        assertTrue(match.diagnostics.inferredIds.isEmpty())
        assertEquals(setOf(0), match.diagnostics.rotations.values.toSet())
        assertEquals(setOf(0), match.diagnostics.bitErrors.values.toSet())
        val expected = TemplateGeometry.cornerMarkerCenters(layout)
        assertNear(match.anchors!!.lu.point, expected.lu.x * 3f, expected.lu.y * 3f)
        assertNear(match.anchors.ru.point, expected.ru.x * 3f, expected.ru.y * 3f)
        assertNear(match.anchors.ld.point, expected.ld.x * 3f, expected.ld.y * 3f)
        assertNear(match.anchors.rd.point, expected.rd.x * 3f, expected.rd.y * 3f)
    }

    @Test
    fun infersFourthAnchorWhenOneMarkerIsOccluded() {
        val template = TemplateState.default()
        val layout = TemplateGeometry.buildLayout(template)
        val renderer = DesktopTemplateCardRenderer(template, scale = 3f)
        renderer.eraseCornerMarker(CornerMarkerId.RD)

        val match = CodedCornerMarkerDetector.findAnchorsWithDiagnostics(renderer.frame(), layout)

        assertNotNull(match.anchors)
        assertEquals(listOf(CornerMarkerId.RD), match.diagnostics.inferredIds)
        val expected = TemplateGeometry.cornerMarkerCenters(layout).rd
        assertNear(match.anchors!!.rd.point, expected.x * 3f, expected.y * 3f, tolerance = 3.0)
    }

    @Test
    fun keepsMarkerIdentityWhenCardIsRotatedClockwise() {
        val template = TemplateState.default()
        val layout = TemplateGeometry.buildLayout(template)
        val renderer = DesktopTemplateCardRenderer(template, scale = 3f)

        val match = CodedCornerMarkerDetector.findAnchorsWithDiagnostics(rotateClockwise(renderer.frame()), layout)

        assertNotNull(match.anchors)
        assertEquals(CornerMarkerId.entries.toList(), match.diagnostics.detectedIds)
        assertEquals(setOf(1), match.diagnostics.rotations.values.toSet())
        assertTrue(match.anchors!!.lu.point.column > match.anchors.ld.point.column)
        assertTrue(match.anchors.lu.point.row < match.anchors.ru.point.row)
    }

    @Test
    fun rejectsSolidSquareMarkersAsUncoded() {
        val template = TemplateState.default()
        val layout = TemplateGeometry.buildLayout(template)
        val renderer = DesktopTemplateCardRenderer(template, markerStyle = CornerMarkerStyle.SOLID_SQUARE)

        assertNull(CodedCornerMarkerDetector.findAnchors(renderer.frame(), layout))
    }

    private fun assertNear(
        point: MiniProgramPoint,
        expectedColumn: Float,
        expectedRow: Float,
        tolerance: Double = 2.0,
    ) {
        assertTrue("column ${point.column} vs $expectedColumn", abs(point.column - expectedColumn) <= tolerance)
        assertTrue("row ${point.row} vs $expectedRow", abs(point.row - expectedRow) <= tolerance)
    }

    private fun rotateClockwise(frame: MiniProgramFrame): MiniProgramFrame {
        val targetWidth = frame.height
        val targetHeight = frame.width
        val pixels = IntArray(targetWidth * targetHeight)
        for (row in 0 until frame.height) {
            for (column in 0 until frame.width) {
                val targetRow = column
                val targetColumn = frame.height - 1 - row
                pixels[targetRow * targetWidth + targetColumn] = frame[row, column]
            }
        }
        return MiniProgramFrame(width = targetWidth, height = targetHeight, pixels = pixels)
    }
}
