package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Test

class MiniProgramCornerTemplateTest {
    @Test
    fun templatesMatchMiniProgramCoordinatesAtBothScales() {
        val largeExpected = mapOf(
            MiniProgramCornerKind.LU to largeLu,
            MiniProgramCornerKind.LD to largeLd,
            MiniProgramCornerKind.RU to largeRu,
            MiniProgramCornerKind.RD to largeRd,
        )
        val smallExpected = largeExpected.mapValues { (_, points) ->
            points.map { point ->
                point.copy(
                    rowOffset = point.rowOffset / 2,
                    columnOffset = point.columnOffset / 2,
                )
            }
        }

        largeExpected.forEach { (kind, expected) ->
            assertEquals("large $kind", expected, MiniProgramCornerTemplate.points(kind, large = true))
            assertEquals("large $kind point count", 22, expected.size)
            assertEquals("large $kind points must be unique", 22, expected.toSet().size)
        }
        smallExpected.forEach { (kind, expected) ->
            assertEquals("small $kind", expected, MiniProgramCornerTemplate.points(kind, large = false))
            assertEquals("small $kind point count", 22, expected.size)
            assertEquals("small $kind points must be unique", 22, expected.toSet().size)
        }
    }

    private fun p(row: Int, column: Int, expected: Int) =
        MiniProgramCornerTemplatePoint(row, column, expected)

    private val largeLu = listOf(
        p(0, 0, 0), p(-6, 0, 1), p(0, -6, 1), p(6, 6, 1),
        p(0, 6, 0), p(-6, 6, 1), p(6, -6, 1), p(6, 0, 0),
        p(12, 6, 1), p(6, 12, 1), p(0, 2, 0), p(2, 0, 0),
        p(-6, 2, 1), p(2, -6, 1), p(6, 8, 1), p(8, 6, 1),
        p(0, 4, 0), p(4, 0, 0), p(-6, 4, 1), p(4, -6, 1),
        p(6, 10, 1), p(10, 6, 1),
    )

    private val largeLd = listOf(
        p(0, 0, 0), p(-6, 0, 1), p(0, 6, 1), p(6, -6, 1),
        p(6, 0, 0), p(0, -6, 0), p(6, 6, 1), p(-6, -6, 1),
        p(12, -6, 1), p(6, -12, 1), p(2, 0, 0), p(0, -2, 0),
        p(2, 6, 1), p(-6, -2, 1), p(8, -6, 1), p(6, -8, 1),
        p(4, 0, 0), p(0, -4, 0), p(4, 6, 1), p(-6, -4, 1),
        p(10, -6, 1), p(6, -10, 1),
    )

    private val largeRu = listOf(
        p(0, 0, 0), p(6, 0, 1), p(0, -6, 1), p(-6, 6, 1),
        p(0, 6, 0), p(-6, 0, 0), p(6, 6, 1), p(-6, -6, 1),
        p(-6, 12, 1), p(-12, 6, 1), p(0, 2, 0), p(-2, 0, 0),
        p(6, 2, 1), p(-2, -6, 1), p(-6, 8, 1), p(-8, 6, 1),
        p(0, 4, 0), p(-4, 0, 0), p(6, 4, 1), p(-4, -6, 1),
        p(-6, 10, 1), p(-10, 6, 1),
    )

    private val largeRd = listOf(
        p(0, 0, 0), p(0, 6, 1), p(6, 0, 1), p(-6, -6, 1),
        p(-6, 0, 0), p(0, -6, 0), p(-6, 6, 1), p(6, -6, 1),
        p(-12, -6, 1), p(-6, -12, 1), p(-2, 0, 0), p(0, -2, 0),
        p(-2, 6, 1), p(6, -2, 1), p(-8, -6, 1), p(-6, -8, 1),
        p(-4, 0, 0), p(0, -4, 0), p(-4, 6, 1), p(6, -4, 1),
        p(-10, -6, 1), p(-6, -10, 1),
    )
}
