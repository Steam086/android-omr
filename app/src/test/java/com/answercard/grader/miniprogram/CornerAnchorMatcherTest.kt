package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CornerAnchorMatcherTest {
    @Test
    fun findsFourMiniProgramCornerAnchors() {
        val frame = whiteFrame(width = 420, height = 300)
        drawCornerAnchor(frame, MiniProgramCornerKind.LU, row = 42, column = 48)
        drawCornerAnchor(frame, MiniProgramCornerKind.LD, row = 250, column = 50)
        drawCornerAnchor(frame, MiniProgramCornerKind.RU, row = 44, column = 360)
        drawCornerAnchor(frame, MiniProgramCornerKind.RD, row = 252, column = 358)

        val anchors = CornerAnchorMatcher.findAnchors(frame)

        requireNotNull(anchors)
        assertNear(42, anchors.lu.point.row)
        assertNear(48, anchors.lu.point.column)
        assertNear(250, anchors.ld.point.row)
        assertNear(50, anchors.ld.point.column)
        assertNear(44, anchors.ru.point.row)
        assertNear(360, anchors.ru.point.column)
        assertNear(252, anchors.rd.point.row)
        assertNear(358, anchors.rd.point.column)
        assertTrue(anchors.quadCheck.accepted)
    }

    @Test
    fun rejectsFrameWhenOneAnchorIsMissing() {
        val frame = whiteFrame(width = 420, height = 300)
        drawCornerAnchor(frame, MiniProgramCornerKind.LU, row = 42, column = 48)
        drawCornerAnchor(frame, MiniProgramCornerKind.LD, row = 250, column = 50)
        drawCornerAnchor(frame, MiniProgramCornerKind.RU, row = 44, column = 360)

        assertNull(CornerAnchorMatcher.findAnchors(frame))
    }

    @Test
    fun filledAnswerBlocksDoNotSatisfyCornerAnchors() {
        val frame = whiteFrame(width = 420, height = 300)
        fillRect(frame, row = 60, column = 60, height = 22, width = 38, value = 20)
        fillRect(frame, row = 230, column = 60, height = 22, width = 38, value = 20)
        fillRect(frame, row = 60, column = 330, height = 22, width = 38, value = 20)
        fillRect(frame, row = 230, column = 330, height = 22, width = 38, value = 20)

        assertNull(CornerAnchorMatcher.findAnchors(frame))
    }

    @Test
    fun scorePrefersClearerLongerAnchor() {
        val frame = whiteFrame(width = 420, height = 300)
        drawCornerAnchor(frame, MiniProgramCornerKind.LU, row = 42, column = 48, armLength = 24)
        drawCornerAnchor(frame, MiniProgramCornerKind.LU, row = 72, column = 78, armLength = 60)
        drawCornerAnchor(frame, MiniProgramCornerKind.LD, row = 250, column = 78)
        drawCornerAnchor(frame, MiniProgramCornerKind.RU, row = 72, column = 360)
        drawCornerAnchor(frame, MiniProgramCornerKind.RD, row = 250, column = 358)

        val anchors = CornerAnchorMatcher.findAnchors(frame)

        assertNotNull(anchors)
        assertEquals(72, anchors?.lu?.point?.row)
        assertEquals(78, anchors?.lu?.point?.column)
    }

    @Test
    fun rejectsFrameBorderCornerCandidates() {
        val frame = whiteFrame(width = 640, height = 480)
        drawFrameBorderAnchors(frame)

        assertNull(CornerAnchorMatcher.findAnchors(frame))
    }

    @Test
    fun choosesInteriorAnchorsWhenFrameBorderCandidatesArePresent() {
        val frame = whiteFrame(width = 640, height = 480)
        drawFrameBorderAnchors(frame)
        drawCornerAnchor(frame, MiniProgramCornerKind.LU, row = 72, column = 78)
        drawCornerAnchor(frame, MiniProgramCornerKind.LD, row = 410, column = 78)
        drawCornerAnchor(frame, MiniProgramCornerKind.RU, row = 72, column = 560)
        drawCornerAnchor(frame, MiniProgramCornerKind.RD, row = 410, column = 560)

        val anchors = CornerAnchorMatcher.findAnchors(frame)

        requireNotNull(anchors)
        assertNear(72, anchors.lu.point.row)
        assertNear(78, anchors.lu.point.column)
        assertNear(410, anchors.ld.point.row)
        assertNear(78, anchors.ld.point.column)
        assertNear(72, anchors.ru.point.row)
        assertNear(560, anchors.ru.point.column)
        assertNear(410, anchors.rd.point.row)
        assertNear(560, anchors.rd.point.column)
    }

    @Test
    fun findsThickAnchorsWhenGlobalThresholdMissesComponentCandidates() {
        val frame = MiniProgramFrame(width = 1280, height = 960, pixels = IntArray(1280 * 960) { 180 })
        fillRect(frame, row = 384, column = 512, height = 192, width = 256, value = 80)
        drawTraceCornerAnchor(frame, MiniProgramCornerKind.LU, row = 120, column = 150, armLength = 90, thickness = 22, value = 70)
        drawTraceCornerAnchor(frame, MiniProgramCornerKind.LD, row = 820, column = 150, armLength = 90, thickness = 22, value = 70)
        drawTraceCornerAnchor(frame, MiniProgramCornerKind.RU, row = 120, column = 1130, armLength = 90, thickness = 22, value = 70)
        drawTraceCornerAnchor(frame, MiniProgramCornerKind.RD, row = 820, column = 1130, armLength = 90, thickness = 22, value = 70)

        val anchors = CornerAnchorMatcher.findAnchors(frame)

        requireNotNull(anchors)
        assertNear(120, anchors.lu.point.row, tolerance = 8)
        assertNear(150, anchors.lu.point.column, tolerance = 8)
        assertNear(820, anchors.ld.point.row, tolerance = 8)
        assertNear(150, anchors.ld.point.column, tolerance = 8)
        assertNear(120, anchors.ru.point.row, tolerance = 8)
        assertNear(1130, anchors.ru.point.column, tolerance = 8)
        assertNear(820, anchors.rd.point.row, tolerance = 8)
        assertNear(1130, anchors.rd.point.column, tolerance = 8)
    }

    @Test
    fun reportsDiagnosticsWithoutStallingOnLargeWhiteFrame() {
        val frame = whiteFrame(width = 1280, height = 960)
        val startedAt = System.nanoTime()

        val result = CornerAnchorMatcher.findAnchorsWithDiagnostics(frame)

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertNull(result.anchors)
        assertTrue("large white frame corner scan took ${elapsedMs}ms", elapsedMs < 1_500)
        assertTrue(result.diagnostics.debugInfo().any { it.startsWith("cornerElapsedMs=") })
        assertTrue(result.diagnostics.debugInfo().any { it.startsWith("strongTraceCandidateCount=") })
    }

    @Test
    fun reportsBestAndSelectedCornerCandidates() {
        val frame = whiteFrame(width = 420, height = 300)
        drawCornerAnchor(frame, MiniProgramCornerKind.LU, row = 42, column = 48)
        drawCornerAnchor(frame, MiniProgramCornerKind.LD, row = 250, column = 50)
        drawCornerAnchor(frame, MiniProgramCornerKind.RU, row = 44, column = 360)
        drawCornerAnchor(frame, MiniProgramCornerKind.RD, row = 252, column = 358)

        val result = CornerAnchorMatcher.findAnchorsWithDiagnostics(frame)
        val debugInfo = result.diagnostics.debugInfo()

        assertNotNull(result.anchors)
        assertTrue(debugInfo.any { it.startsWith("bestLU=") })
        assertTrue(debugInfo.any { it.startsWith("bestRU=") })
        assertTrue(debugInfo.any { it.startsWith("bestLD=") })
        assertTrue(debugInfo.any { it.startsWith("bestRD=") })
        assertTrue(debugInfo.any { it.startsWith("selectedLU=") })
        assertTrue(debugInfo.any { it.startsWith("selectedRU=") })
        assertTrue(debugInfo.any { it.startsWith("selectedLD=") })
        assertTrue(debugInfo.any { it.startsWith("selectedRD=") })
    }

    private fun whiteFrame(width: Int, height: Int): MiniProgramFrame =
        MiniProgramFrame(width = width, height = height, pixels = IntArray(width * height) { 230 })

    private fun drawCornerAnchor(
        frame: MiniProgramFrame,
        kind: MiniProgramCornerKind,
        row: Int,
        column: Int,
        armLength: Int = 54,
        thickness: Int = 10,
        value: Int = 20,
    ) {
        when (kind) {
            MiniProgramCornerKind.LU -> {
                fillRect(frame, row, column, thickness, armLength, value)
                fillRect(frame, row, column, armLength, thickness, value)
            }
            MiniProgramCornerKind.LD -> {
                fillRect(frame, row, column, thickness, armLength, value)
                fillRect(frame, row - armLength + thickness, column, armLength, thickness, value)
            }
            MiniProgramCornerKind.RU -> {
                fillRect(frame, row, column - armLength + 1, thickness, armLength, value)
                fillRect(frame, row, column - armLength + 1, armLength, thickness, value)
            }
            MiniProgramCornerKind.RD -> {
                fillRect(frame, row, column - armLength + 1, thickness, armLength, value)
                fillRect(frame, row - armLength + thickness, column - armLength + 1, armLength, thickness, value)
            }
        }
    }

    private fun drawFrameBorderAnchors(frame: MiniProgramFrame) {
        drawCornerAnchor(frame, MiniProgramCornerKind.LU, row = 0, column = 0, armLength = 70)
        drawCornerAnchor(frame, MiniProgramCornerKind.RU, row = 0, column = frame.width - 1, armLength = 70)
        drawCornerAnchor(frame, MiniProgramCornerKind.LD, row = frame.height - 1, column = 0, armLength = 70)
        drawCornerAnchor(frame, MiniProgramCornerKind.RD, row = frame.height - 1, column = frame.width - 1, armLength = 70)
    }

    private fun drawTraceCornerAnchor(
        frame: MiniProgramFrame,
        kind: MiniProgramCornerKind,
        row: Int,
        column: Int,
        armLength: Int,
        thickness: Int,
        value: Int,
    ) {
        when (kind) {
            MiniProgramCornerKind.LU -> {
                fillRect(frame, row, column, thickness, armLength, value)
                fillRect(frame, row, column, armLength, thickness, value)
            }
            MiniProgramCornerKind.LD -> {
                fillRect(frame, row, column, thickness, armLength, value)
                fillRect(frame, row - armLength + thickness, column, armLength, thickness, value)
            }
            MiniProgramCornerKind.RU -> {
                fillRect(frame, row, column - armLength + 1, thickness, armLength, value)
                fillRect(frame, row, column - thickness + 1, armLength, thickness, value)
            }
            MiniProgramCornerKind.RD -> {
                fillRect(frame, row, column - armLength + 1, thickness, armLength, value)
                fillRect(frame, row - armLength + thickness, column - thickness + 1, armLength, thickness, value)
            }
        }
    }

    private fun fillRect(
        frame: MiniProgramFrame,
        row: Int,
        column: Int,
        height: Int,
        width: Int,
        value: Int,
    ) {
        for (y in row.coerceAtLeast(0) until (row + height).coerceAtMost(frame.height)) {
            for (x in column.coerceAtLeast(0) until (column + width).coerceAtMost(frame.width)) {
                frame.pixels[y * frame.width + x] = value
            }
        }
    }

    private fun assertNear(expected: Int, actual: Int, tolerance: Int = 3) {
        assertTrue("expected $actual near $expected", actual in expected - tolerance..expected + tolerance)
    }
}
