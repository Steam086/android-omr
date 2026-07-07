package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniProgramGridBuilderTest {
    @Test
    fun buildsUniformGridForRectangleCorners() {
        val grid = MiniProgramGridBuilder.build(
            lu = MiniProgramPoint(row = 0, column = 0),
            ld = MiniProgramPoint(row = 50, column = 0),
            ru = MiniProgramPoint(row = 0, column = 100),
            rd = MiniProgramPoint(row = 50, column = 100),
            rows = 5,
            columns = 10,
        )

        assertEquals(5, grid.rows)
        assertEquals(10, grid.columns)
        assertEquals(66, grid.points.size)
        assertPoint(0.0, 0.0, grid.point(row = 0, column = 0))
        assertPoint(0.0, 100.0, grid.point(row = 0, column = 10))
        assertPoint(50.0, 0.0, grid.point(row = 5, column = 0))
        assertPoint(50.0, 100.0, grid.point(row = 5, column = 10))
        assertPoint(25.0, 50.0, grid.point(row = 2.5, column = 5.0))
    }

    @Test
    fun buildsInteriorPointsForMildPerspectiveCorners() {
        val grid = MiniProgramGridBuilder.build(
            lu = MiniProgramPoint(row = 10, column = 10),
            ld = MiniProgramPoint(row = 60, column = 0),
            ru = MiniProgramPoint(row = 5, column = 110),
            rd = MiniProgramPoint(row = 70, column = 120),
            rows = 5,
            columns = 10,
        )

        assertPoint(10.0, 10.0, grid.point(row = 0, column = 0))
        assertPoint(5.0, 110.0, grid.point(row = 0, column = 10))
        assertPoint(60.0, 0.0, grid.point(row = 5, column = 0))
        assertPoint(70.0, 120.0, grid.point(row = 5, column = 10))

        for (row in 0..5) {
            for (column in 0..10) {
                val point = grid.point(row, column)
                assertTrue(point.row.isFinite())
                assertTrue(point.column.isFinite())
            }
        }

        for (row in 0..5) {
            assertTrue(grid.point(row, 0).column < grid.point(row, 10).column)
        }
        for (column in 0..10) {
            assertTrue(grid.point(0, column).row < grid.point(5, column).row)
        }
    }

    @Test
    fun cellReturnsFourAdjacentGridPoints() {
        val grid = MiniProgramGridBuilder.build(
            lu = MiniProgramPoint(row = 0, column = 0),
            ld = MiniProgramPoint(row = 50, column = 0),
            ru = MiniProgramPoint(row = 0, column = 100),
            rd = MiniProgramPoint(row = 50, column = 100),
            rows = 5,
            columns = 10,
        )

        val cell = grid.cell(row = 2, column = 4)

        assertEquals(2, cell.row)
        assertEquals(4, cell.column)
        assertEquals(grid.point(row = 2, column = 4), cell.leftTop)
        assertEquals(grid.point(row = 2, column = 5), cell.rightTop)
        assertEquals(grid.point(row = 3, column = 4), cell.leftBottom)
        assertEquals(grid.point(row = 3, column = 5), cell.rightBottom)
        assertFailsWithMessage("row must be in 0 until rows") { grid.cell(row = 5, column = 0) }
        assertFailsWithMessage("column must be in 0 until columns") { grid.cell(row = 0, column = 10) }
    }

    @Test
    fun rejectsInvalidGridParameters() {
        val lu = MiniProgramPoint(row = 0, column = 0)
        val ld = MiniProgramPoint(row = 50, column = 0)
        val ru = MiniProgramPoint(row = 0, column = 100)
        val rd = MiniProgramPoint(row = 50, column = 100)

        assertFailsWithMessage("rows must be positive") {
            MiniProgramGridBuilder.build(lu, ld, ru, rd, rows = 0, columns = 10)
        }
        assertFailsWithMessage("columns must be positive") {
            MiniProgramGridBuilder.build(lu, ld, ru, rd, rows = 5, columns = 0)
        }
        assertFailsWithMessage("corner points must form a valid quadrilateral") {
            MiniProgramGridBuilder.build(
                lu = MiniProgramPoint(row = 0, column = 0),
                ld = MiniProgramPoint(row = 0, column = 0),
                ru = MiniProgramPoint(row = 0, column = 100),
                rd = MiniProgramPoint(row = 50, column = 100),
                rows = 5,
                columns = 10,
            )
        }
    }

    private fun assertPoint(expectedRow: Double, expectedColumn: Double, actual: MiniProgramGridPoint) {
        assertEquals(expectedRow, actual.row, 0.001)
        assertEquals(expectedColumn, actual.column, 0.001)
    }

    private fun assertFailsWithMessage(expectedMessage: String, action: () -> Unit) {
        val error = try {
            action()
            null
        } catch (error: IllegalArgumentException) {
            error
        }
        assertEquals(expectedMessage, error?.message)
    }
}
