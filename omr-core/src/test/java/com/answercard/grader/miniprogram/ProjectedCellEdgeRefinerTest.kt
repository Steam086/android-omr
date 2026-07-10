package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectedCellEdgeRefinerTest {
    @Test
    fun shiftsQuestionGroupsToTheirPrintedRectangleEdges() {
        val original = projectedCells(
            questionGroups = mapOf(
                0 to listOf(rectangularCell(row = 0, column = 0, left = 30.0, top = 20.0), rectangularCell(0, 1, 58.0, 20.0)),
                1 to listOf(rectangularCell(row = 1, column = 0, left = 122.0, top = 70.0), rectangularCell(1, 1, 150.0, 70.0)),
            ),
        )
        val frame = whiteFrame()
        original.questionCells.filterKeys { it.questionIndex == 0 }.values.forEach { drawVerticalBorders(frame, it, shift = 4, value = 0) }
        original.questionCells.filterKeys { it.questionIndex == 1 }.values.forEach { drawVerticalBorders(frame, it, shift = -3, value = 0) }

        val refined = ProjectedCellEdgeRefiner.refine(frame, original)

        assertGroupShift(original, refined, questionIndex = 0, expectedShift = 4.0)
        assertGroupShift(original, refined, questionIndex = 1, expectedShift = -3.0)
        assertTrue(refined.debugInfo.contains("edgeRefinement=active"))
        assertTrue(refined.debugInfo.contains("edgeRefinementQuestionGroups=2"))
    }

    @Test
    fun shiftsAdmissionGroupWithoutChangingRows() {
        val original = projectedCells(
            admissionGroups = mapOf(
                0 to listOf(
                    rectangularCell(row = 0, column = 0, left = 24.0, top = 110.0),
                    rectangularCell(row = 0, column = 1, left = 52.0, top = 110.0),
                    rectangularCell(row = 0, column = 2, left = 80.0, top = 110.0),
                ),
            ),
        )
        val frame = whiteFrame()
        original.admissionNumberCells.values.forEach { drawVerticalBorders(frame, it, shift = 3, value = 0) }

        val refined = ProjectedCellEdgeRefiner.refine(frame, original)

        original.admissionNumberCells.forEach { (key, cell) ->
            assertCellShift(cell, requireNotNull(refined.admissionNumberCells[key]), 3.0)
        }
        assertTrue(refined.debugInfo.contains("edgeRefinementAdmissionGroups=1"))
    }

    @Test
    fun oneRectangleIsInsufficientAndFallsBackExactly() {
        val original = projectedCells(
            questionGroups = mapOf(0 to listOf(rectangularCell(row = 0, column = 0, left = 30.0, top = 20.0))),
        )
        val frame = whiteFrame()
        original.questionCells.values.forEach { drawVerticalBorders(frame, it, shift = 4, value = 0) }

        val refined = ProjectedCellEdgeRefiner.refine(frame, original)

        assertEquals(original.questionCells, refined.questionCells)
        assertEquals(original.admissionNumberCells, refined.admissionNumberCells)
        assertTrue(refined.debugInfo.contains("edgeRefinement=fallback"))
        assertTrue(refined.debugInfo.contains("edgeRefinementFallbackGroups=1"))
    }

    @Test
    fun lowContrastEdgesFallBackExactly() {
        val original = projectedCells(
            questionGroups = mapOf(
                0 to listOf(rectangularCell(row = 0, column = 0, left = 30.0, top = 20.0), rectangularCell(0, 1, 58.0, 20.0)),
            ),
        )
        val frame = whiteFrame()
        original.questionCells.values.forEach { drawVerticalBorders(frame, it, shift = 4, value = 238) }

        val refined = ProjectedCellEdgeRefiner.refine(frame, original)

        assertEquals(original.questionCells, refined.questionCells)
        assertEquals(original.admissionNumberCells, refined.admissionNumberCells)
        assertTrue(refined.debugInfo.contains("edgeRefinementFallbackGroups=1"))
    }

    @Test
    fun equalCompetingEdgesAreAmbiguousAndFallBackExactly() {
        val original = projectedCells(
            questionGroups = mapOf(
                0 to listOf(rectangularCell(row = 0, column = 0, left = 30.0, top = 20.0), rectangularCell(0, 1, 58.0, 20.0)),
            ),
        )
        val frame = whiteFrame()
        original.questionCells.values.forEach { cell ->
            drawVerticalBorders(frame, cell, shift = -3, value = 0)
            drawVerticalBorders(frame, cell, shift = 3, value = 0)
        }

        val refined = ProjectedCellEdgeRefiner.refine(frame, original)

        assertEquals(original.questionCells, refined.questionCells)
        assertEquals(original.admissionNumberCells, refined.admissionNumberCells)
        assertTrue(refined.debugInfo.contains("edgeRefinementFallbackGroups=1"))
    }

    @Test
    fun oneOutlyingRectangleDoesNotDragTheGroupAwayFromTheConsensus() {
        val original = projectedCells(
            questionGroups = mapOf(
                0 to listOf(
                    rectangularCell(row = 0, column = 0, left = 30.0, top = 20.0),
                    rectangularCell(row = 0, column = 1, left = 58.0, top = 20.0),
                    rectangularCell(row = 0, column = 2, left = 86.0, top = 20.0),
                ),
            ),
        )
        val frame = whiteFrame()
        original.questionCells.entries.forEach { (key, cell) ->
            drawVerticalBorders(frame, cell, shift = if (key.optionIndex < 2) 4 else -4, value = 0)
        }

        val refined = ProjectedCellEdgeRefiner.refine(frame, original)

        assertGroupShift(original, refined, questionIndex = 0, expectedShift = 4.0)
        assertEquals(0, refined.edgeRefinementUnsafeGroups)
        assertTrue(refined.debugInfo.contains("edgeRefinementQuestionGroups=1"))
    }

    private fun assertGroupShift(
        original: AndroidPaperProjectedCells,
        refined: AndroidPaperProjectedCells,
        questionIndex: Int,
        expectedShift: Double,
    ) {
        original.questionCells.filterKeys { it.questionIndex == questionIndex }.forEach { (key, cell) ->
            assertCellShift(cell, requireNotNull(refined.questionCells[key]), expectedShift)
        }
    }

    private fun assertCellShift(original: MiniProgramCell, refined: MiniProgramCell, expectedShift: Double) {
        assertEquals(original.leftTop.row, refined.leftTop.row, 0.0)
        assertEquals(original.rightTop.row, refined.rightTop.row, 0.0)
        assertEquals(original.leftBottom.row, refined.leftBottom.row, 0.0)
        assertEquals(original.rightBottom.row, refined.rightBottom.row, 0.0)
        assertEquals(original.leftTop.column + expectedShift, refined.leftTop.column, 1.0)
        assertEquals(original.rightTop.column + expectedShift, refined.rightTop.column, 1.0)
        assertEquals(original.leftBottom.column + expectedShift, refined.leftBottom.column, 1.0)
        assertEquals(original.rightBottom.column + expectedShift, refined.rightBottom.column, 1.0)
    }

    private fun projectedCells(
        questionGroups: Map<Int, List<MiniProgramCell>> = emptyMap(),
        admissionGroups: Map<Int, List<MiniProgramCell>> = emptyMap(),
    ) = AndroidPaperProjectedCells(
        questionCells = questionGroups.flatMap { (questionIndex, cells) ->
            cells.mapIndexed { optionIndex, cell -> AndroidPaperQuestionCellKey(questionIndex, optionIndex) to cell }
        }.toMap(),
        admissionNumberCells = admissionGroups.flatMap { (digitIndex, cells) ->
            cells.mapIndexed { numberValue, cell -> AndroidPaperAdmissionNumberCellKey(digitIndex, numberValue) to cell }
        }.toMap(),
        debugInfo = listOf("projection=templateGeometry"),
    )

    private fun rectangularCell(row: Int, column: Int, left: Double, top: Double) =
        MiniProgramCell(
            row = row,
            column = column,
            leftTop = MiniProgramGridPoint(top, left),
            rightTop = MiniProgramGridPoint(top, left + CELL_WIDTH),
            leftBottom = MiniProgramGridPoint(top + CELL_HEIGHT, left),
            rightBottom = MiniProgramGridPoint(top + CELL_HEIGHT, left + CELL_WIDTH),
        )

    private fun whiteFrame() = MiniProgramFrame(FRAME_WIDTH, FRAME_HEIGHT, IntArray(FRAME_WIDTH * FRAME_HEIGHT) { 255 })

    private fun drawVerticalBorders(frame: MiniProgramFrame, cell: MiniProgramCell, shift: Int, value: Int) {
        val top = cell.leftTop.row.toInt()
        val bottom = cell.leftBottom.row.toInt()
        val left = cell.leftTop.column.toInt() + shift
        val right = cell.rightTop.column.toInt() + shift
        for (row in top..bottom) {
            frame.pixels[row * frame.width + left] = value
            frame.pixels[row * frame.width + right] = value
        }
    }

    companion object {
        private const val FRAME_WIDTH = 220
        private const val FRAME_HEIGHT = 160
        private const val CELL_WIDTH = 20.0
        private const val CELL_HEIGHT = 16.0
    }
}
