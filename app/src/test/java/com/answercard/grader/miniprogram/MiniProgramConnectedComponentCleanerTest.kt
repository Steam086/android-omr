package com.answercard.grader.miniprogram

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniProgramConnectedComponentCleanerTest {
    @Test
    fun removesSingleBlackPixel() {
        val matrix = matrix(
            "11111",
            "11011",
            "11111",
        )

        val result = MiniProgramConnectedComponentCleaner.clean(matrix, rows = 3, columns = 5)

        assertArrayEquals(matrix("11111", "11111", "11111"), result.cleanedBinary)
        assertEquals(1, result.noiseComponentsRemoved)
        assertEquals(1, result.noisePixelsRemoved)
        assertEquals(0, result.componentsKept)
        assertEquals(1, result.largestComponentArea)
    }

    @Test
    fun removesMultipleSeparatedNoisePixels() {
        val matrix = matrix(
            "01110",
            "11111",
            "10101",
            "11111",
        )

        val result = MiniProgramConnectedComponentCleaner.clean(matrix, rows = 4, columns = 5)

        assertArrayEquals(matrix("11111", "11111", "11111", "11111"), result.cleanedBinary)
        assertEquals(4, result.noiseComponentsRemoved)
        assertEquals(4, result.noisePixelsRemoved)
        assertEquals(0, result.componentsKept)
    }

    @Test
    fun keepsLargeCentralBlackBlock() {
        val matrix = matrix(
            "111111",
            "100001",
            "100001",
            "100001",
            "111111",
        )

        val result = MiniProgramConnectedComponentCleaner.clean(matrix, rows = 5, columns = 6)

        assertArrayEquals(matrix, result.cleanedBinary)
        assertEquals(0, result.noiseComponentsRemoved)
        assertEquals(0, result.noisePixelsRemoved)
        assertEquals(1, result.componentsKept)
        assertEquals(12, result.largestComponentArea)
    }

    @Test
    fun removesNoiseAndKeepsLargeBlock() {
        val matrix = matrix(
            "01111110",
            "11111111",
            "11000011",
            "11000011",
            "11000011",
            "11111111",
            "01111110",
        )

        val result = MiniProgramConnectedComponentCleaner.clean(matrix, rows = 7, columns = 8)

        assertArrayEquals(
            matrix(
                "11111111",
                "11111111",
                "11000011",
                "11000011",
                "11000011",
                "11111111",
                "11111111",
            ),
            result.cleanedBinary,
        )
        assertEquals(4, result.noisePixelsRemoved)
        assertEquals(4, result.noiseComponentsRemoved)
        assertEquals(1, result.componentsKept)
        assertEquals(12, result.largestComponentArea)
    }

    @Test
    fun diagonalPixelsAreSeparateComponentsLikeMiniProgramFourNeighborhood() {
        val matrix = matrix(
            "011",
            "101",
            "110",
        )

        val result = MiniProgramConnectedComponentCleaner.clean(matrix, rows = 3, columns = 3)

        assertArrayEquals(matrix("111", "111", "111"), result.cleanedBinary)
        assertEquals(3, result.noiseComponentsRemoved)
        assertEquals(1, result.largestComponentArea)
    }

    @Test
    fun edgeLineCanBeRemovedWhenItIsBelowNoiseThreshold() {
        val matrix = matrix(
            "01111111",
            "01111111",
            "01111111",
            "11111111",
            "11111111",
            "11111111",
        )

        val result = MiniProgramConnectedComponentCleaner.clean(matrix, rows = 6, columns = 8)

        assertArrayEquals(
            matrix(
                "11111111",
                "11111111",
                "11111111",
                "11111111",
                "11111111",
                "11111111",
            ),
            result.cleanedBinary,
        )
        assertEquals(1, result.noiseComponentsRemoved)
        assertEquals(3, result.noisePixelsRemoved)
    }

    @Test
    fun keepsComponentWhenAreaEqualsNoiseThreshold() {
        val matrix = matrix(
            "00111111",
            "00111111",
            "11111111",
            "11111111",
        )

        val result = MiniProgramConnectedComponentCleaner.clean(matrix, rows = 4, columns = 8)

        assertArrayEquals(matrix, result.cleanedBinary)
        assertEquals(0, result.noiseComponentsRemoved)
        assertEquals(0, result.noisePixelsRemoved)
        assertEquals(1, result.componentsKept)
        assertEquals(4, result.largestComponentArea)
    }

    @Test
    fun removesComponentWhenAreaIsBelowNoiseThreshold() {
        val matrix = matrix(
            "01111111",
            "01111111",
            "01111111",
            "11111111",
        )

        val result = MiniProgramConnectedComponentCleaner.clean(matrix, rows = 4, columns = 8)

        assertArrayEquals(matrix("11111111", "11111111", "11111111", "11111111"), result.cleanedBinary)
        assertEquals(1, result.noiseComponentsRemoved)
        assertEquals(3, result.noisePixelsRemoved)
        assertEquals(0, result.componentsKept)
        assertEquals(3, result.largestComponentArea)
    }

    @Test
    fun rejectsInvalidMatrix() {
        assertRejects {
            MiniProgramConnectedComponentCleaner.clean(IntArray(0), rows = 0, columns = 4)
        }
        assertRejects {
            MiniProgramConnectedComponentCleaner.clean(IntArray(5), rows = 1, columns = 4)
        }
        assertRejects {
            MiniProgramConnectedComponentCleaner.clean(intArrayOf(1, 2, 1, 1), rows = 2, columns = 2)
        }
    }

    private fun matrix(vararg rows: String): IntArray =
        rows.flatMap { row -> row.map { it.digitToInt() } }.toIntArray()

    private fun assertRejects(action: () -> Unit) {
        val error = try {
            action()
            null
        } catch (error: IllegalArgumentException) {
            error
        }
        requireNotNull(error)
    }
}
