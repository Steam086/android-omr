package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PerspectiveMappingTest {
    private val unitSquare = listOf(
        PerspectivePoint(0.0, 0.0),
        PerspectivePoint(1.0, 0.0),
        PerspectivePoint(1.0, 1.0),
        PerspectivePoint(0.0, 1.0),
    )

    @Test
    fun identityMappingReturnsInput() {
        val mapping = PerspectiveMapping.fromCorrespondences(unitSquare, unitSquare)!!
        val mapped = mapping.map(PerspectivePoint(0.3, 0.7))
        assertEquals(0.3, mapped.x, 1e-9)
        assertEquals(0.7, mapped.y, 1e-9)
    }

    @Test
    fun mapsCorrespondenceCornersExactly() {
        val target = listOf(
            PerspectivePoint(10.0, 20.0),
            PerspectivePoint(110.0, 24.0),
            PerspectivePoint(104.0, 130.0),
            PerspectivePoint(6.0, 118.0),
        )
        val mapping = PerspectiveMapping.fromCorrespondences(unitSquare, target)!!
        unitSquare.zip(target).forEach { (source, expected) ->
            val mapped = mapping.map(source)
            assertEquals(expected.x, mapped.x, 1e-6)
            assertEquals(expected.y, mapped.y, 1e-6)
        }
    }

    @Test
    fun invertIsInverseOfMap() {
        val target = listOf(
            PerspectivePoint(10.0, 20.0),
            PerspectivePoint(110.0, 24.0),
            PerspectivePoint(104.0, 130.0),
            PerspectivePoint(6.0, 118.0),
        )
        val mapping = PerspectiveMapping.fromCorrespondences(unitSquare, target)!!
        val original = PerspectivePoint(0.25, 0.6)
        val roundTrip = mapping.invert(mapping.map(original))
        assertEquals(original.x, roundTrip.x, 1e-6)
        assertEquals(original.y, roundTrip.y, 1e-6)
    }

    @Test
    fun leastSquaresMappingUsesMoreThanFourCorrespondences() {
        val source = buildList {
            for (row in 0..2) for (column in 0..2) add(PerspectivePoint(column.toDouble(), row.toDouble()))
        }
        val exact = PerspectiveMapping.fromCorrespondences(
            unitSquare,
            listOf(
                PerspectivePoint(10.0, 20.0),
                PerspectivePoint(110.0, 24.0),
                PerspectivePoint(104.0, 130.0),
                PerspectivePoint(6.0, 118.0),
            ),
        )!!
        val target = source.map(exact::map)

        val fitted = PerspectiveMapping.fromCorrespondences(source, target)!!

        source.zip(target).forEach { (point, expected) ->
            val actual = fitted.map(point)
            assertEquals(expected.x, actual.x, 1e-6)
            assertEquals(expected.y, actual.y, 1e-6)
        }
    }

    @Test
    fun perspectiveMappingIsNotAffine() {
        // A true perspective target: parallel source lines must not stay parallel.
        val target = listOf(
            PerspectivePoint(0.0, 0.0),
            PerspectivePoint(100.0, 10.0),
            PerspectivePoint(90.0, 90.0),
            PerspectivePoint(10.0, 80.0),
        )
        val mapping = PerspectiveMapping.fromCorrespondences(unitSquare, target)!!
        val midTop = mapping.map(PerspectivePoint(0.5, 0.0))
        val midBottom = mapping.map(PerspectivePoint(0.5, 1.0))
        val naiveMidTop = PerspectivePoint(50.0, 5.0)
        // Bilinear would land exactly at the edge midpoint; homography must differ.
        assertNotNull(mapping)
        val differs = Math.abs(midTop.x - naiveMidTop.x) > 1e-6 || Math.abs(midTop.y - naiveMidTop.y) > 1e-6 ||
            Math.abs(midBottom.x - 50.0) > 1e-6
        org.junit.Assert.assertTrue(differs)
    }

    @Test
    fun degenerateCollinearPointsReturnNull() {
        val collinear = listOf(
            PerspectivePoint(0.0, 0.0),
            PerspectivePoint(1.0, 0.0),
            PerspectivePoint(2.0, 0.0),
            PerspectivePoint(3.0, 0.0),
        )
        assertNull(PerspectiveMapping.fromCorrespondences(collinear, unitSquare))
    }

    @Test(expected = IllegalArgumentException::class)
    fun requiresAtLeastFourPoints() {
        PerspectiveMapping.fromCorrespondences(unitSquare.take(3), unitSquare.take(3))
    }
}
