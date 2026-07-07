package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAdmissionNumberReaderTest {
    @Test
    fun readsFourDigitAdmissionNumber() {
        val fixture = Fixture()
        fixture.mark(digitIndex = 0, numberValue = 1)
        fixture.mark(digitIndex = 1, numberValue = 2)
        fixture.mark(digitIndex = 2, numberValue = 3)
        fixture.mark(digitIndex = 3, numberValue = 4)

        val result = AndroidAdmissionNumberReader.read(fixture.frame(), fixture.grid, fixture.layout)

        assertTrue(result.success)
        assertNull(result.failureReason)
        assertEquals("1234", result.digits)
        assertEquals(listOf(1, 2, 3, 4), result.digitResults.map { it.selectedNumber })
    }

    @Test
    fun readsZeroAndNineFromLeftToRight() {
        val fixture = Fixture()
        listOf(0, 9, 0, 9).forEachIndexed { digitIndex, numberValue ->
            fixture.mark(digitIndex = digitIndex, numberValue = numberValue)
        }

        val result = AndroidAdmissionNumberReader.read(fixture.frame(), fixture.grid, fixture.layout)

        assertTrue(result.success)
        assertEquals("0909", result.digits)
        result.digitResults.forEach { digit ->
            assertEquals(digit.digitIndex, digit.candidates.first { it.numberValue == 0 }.row)
            assertEquals(0, digit.candidates.first { it.numberValue == 0 }.column)
            assertEquals(9, digit.candidates.first { it.numberValue == 9 }.column)
        }
    }

    @Test
    fun reportsBlankDigitWithPlaceholder() {
        val fixture = Fixture()
        fixture.mark(digitIndex = 0, numberValue = 1)
        fixture.mark(digitIndex = 2, numberValue = 3)
        fixture.mark(digitIndex = 3, numberValue = 4)

        val result = AndroidAdmissionNumberReader.read(fixture.frame(), fixture.grid, fixture.layout)

        assertFalse(result.success)
        assertEquals("1?34", result.digits)
        assertEquals("admission number contains blank digit", result.failureReason)
        assertTrue(result.digitResults[1].isBlank)
        assertNull(result.digitResults[1].selectedNumber)
    }

    @Test
    fun reportsMultiMarkedDigitAndKeepsRawCandidates() {
        val fixture = Fixture()
        fixture.mark(digitIndex = 0, numberValue = 1)
        fixture.mark(digitIndex = 0, numberValue = 7)
        fixture.mark(digitIndex = 1, numberValue = 2)
        fixture.mark(digitIndex = 2, numberValue = 3)
        fixture.mark(digitIndex = 3, numberValue = 4)

        val result = AndroidAdmissionNumberReader.read(fixture.frame(), fixture.grid, fixture.layout)

        assertTrue(result.success)
        assertTrue(result.digitResults[0].isMultiMarked)
        assertEquals(10, result.digitResults[0].candidates.size)
        result.digitResults[0].candidates.forEach { candidate ->
            assertEquals(0, candidate.digitIndex)
            assertNotNull(candidate.readResult)
        }
    }

    @Test
    fun choosesDarkestCandidateWhenDigitIsMultiMarked() {
        val fixture = Fixture()
        fixture.mark(digitIndex = 0, numberValue = 4, markSize = 9)
        fixture.mark(digitIndex = 0, numberValue = 8, markSize = 14)
        fixture.mark(digitIndex = 1, numberValue = 2)
        fixture.mark(digitIndex = 2, numberValue = 3)
        fixture.mark(digitIndex = 3, numberValue = 4)

        val result = AndroidAdmissionNumberReader.read(fixture.frame(), fixture.grid, fixture.layout)

        val digit = result.digitResults[0]
        assertTrue(digit.isMultiMarked)
        assertEquals(8, digit.selectedNumber)
    }

    @Test
    fun doesNotReadQuestionMappings() {
        val fixture = Fixture()
        listOf(1, 2, 3, 4).forEachIndexed { digitIndex, numberValue ->
            fixture.mark(digitIndex = digitIndex, numberValue = numberValue)
        }
        val layoutWithBadQuestionMappings = fixture.layout.copy(
            questionMappings = listOf(
                AndroidPaperQuestionMapping(
                    questionIndex = 0,
                    optionIndex = 0,
                    row = 99,
                    column = 99,
                ),
            ),
        )

        val result = AndroidAdmissionNumberReader.read(fixture.frame(), fixture.grid, layoutWithBadQuestionMappings)

        assertTrue(result.success)
        assertEquals("1234", result.digits)
    }

    @Test
    fun returnsFailureWhenAdmissionMappingIsOutsideGrid() {
        val fixture = Fixture()
        val badLayout = fixture.layout.copy(
            admissionNumberMappings = listOf(
                AndroidPaperAdmissionNumberMapping(
                    digitIndex = 0,
                    numberValue = 0,
                    row = 0,
                    column = fixture.grid.columns,
                ),
            ),
        )

        val result = AndroidAdmissionNumberReader.read(fixture.frame(), fixture.grid, badLayout)

        assertFalse(result.success)
        assertTrue(result.digitResults.isEmpty())
        assertEquals("admission mapping is outside grid: digitIndex=0, numberValue=0, row=0, column=12", result.failureReason)
    }

    @Test
    fun returnsFailureWhenDigitCandidatesAreIncomplete() {
        val fixture = Fixture()
        val incompleteLayout = fixture.layout.copy(
            admissionNumberMappings = fixture.layout.admissionNumberMappings.filterNot { it.digitIndex == 3 },
        )

        val result = AndroidAdmissionNumberReader.read(fixture.frame(), fixture.grid, incompleteLayout)

        assertFalse(result.success)
        assertEquals("admission number has incomplete digit candidates", result.failureReason)
        assertEquals("???", result.digits.takeLast(3))
        assertEquals(0, result.digitResults[3].candidates.size)
        assertEquals("digit must contain 10 candidates", result.digitResults[3].failureReason)
    }

    @Test
    fun preservesTenRawCandidatesForEveryDigit() {
        val fixture = Fixture()
        listOf(1, 2, 3, 4).forEachIndexed { digitIndex, numberValue ->
            fixture.mark(digitIndex = digitIndex, numberValue = numberValue)
        }

        val result = AndroidAdmissionNumberReader.read(fixture.frame(), fixture.grid, fixture.layout)

        assertEquals(4, result.digitResults.size)
        result.digitResults.forEach { digit ->
            assertEquals(10, digit.candidates.size)
            digit.candidates.forEach { candidate ->
                assertEquals(digit.digitIndex, candidate.digitIndex)
                assertEquals(candidate.numberValue, candidate.column)
                assertNotNull(candidate.readResult)
            }
        }
    }

    private class Fixture {
        val layout: AndroidPaperTemplateLayout = AndroidPaperTemplateBuilder.build(List(15) { 4 })
        val grid: MiniProgramGrid
        private val width = layout.gridColumns * CELL_SIZE
        private val height = layout.gridRows * CELL_SIZE
        private val pixels = IntArray(width * height) { 255 }

        init {
            grid = MiniProgramGridBuilder.build(
                lu = MiniProgramPoint(row = 0, column = 0),
                ld = MiniProgramPoint(row = height - 1, column = 0),
                ru = MiniProgramPoint(row = 0, column = width - 1),
                rd = MiniProgramPoint(row = height - 1, column = width - 1),
                rows = layout.gridRows,
                columns = layout.gridColumns,
            )
        }

        fun mark(
            digitIndex: Int,
            numberValue: Int,
            markSize: Int = DEFAULT_MARK_SIZE,
        ) {
            val mapping = layout.admissionNumberMappings.single {
                it.digitIndex == digitIndex && it.numberValue == numberValue
            }
            val top = mapping.row * CELL_SIZE + (CELL_SIZE - markSize) / 2
            val left = mapping.column * CELL_SIZE + (CELL_SIZE - markSize) / 2
            for (row in top until top + markSize) {
                for (column in left until left + markSize) {
                    pixels[row * width + column] = 0
                }
            }
        }

        fun frame(): MiniProgramFrame = MiniProgramFrame(width = width, height = height, pixels = pixels)

        companion object {
            private const val CELL_SIZE = 24
            private const val DEFAULT_MARK_SIZE = 12
        }
    }
}
