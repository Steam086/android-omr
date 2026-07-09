package com.answercard.grader.miniprogram

import com.answercard.grader.template.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAnswerAreaReaderTest {
    @Test
    fun readsSingleQuestionAOption() {
        val fixture = Fixture(questionCount = 1)
        fixture.mark(questionIndex = 0, optionIndex = 0)

        val result = AndroidAnswerAreaReader.read(fixture.frame(), fixture.grid, fixture.layout)

        assertNull(result.failureReason)
        assertQuestion(result, questionIndex = 0, selectedOptions = listOf(0), selectedLabels = listOf("A"))
    }

    @Test
    fun readsSingleQuestionBcdOptions() {
        listOf(1 to "B", 2 to "C", 3 to "D").forEach { (optionIndex, label) ->
            val fixture = Fixture(questionCount = 1)
            fixture.mark(questionIndex = 0, optionIndex = optionIndex)

            val result = AndroidAnswerAreaReader.read(fixture.frame(), fixture.grid, fixture.layout)

            assertNull(result.failureReason)
            assertQuestion(result, questionIndex = 0, selectedOptions = listOf(optionIndex), selectedLabels = listOf(label))
        }
    }

    @Test
    fun readsBlankQuestion() {
        val fixture = Fixture(questionCount = 1)

        val result = AndroidAnswerAreaReader.read(fixture.frame(), fixture.grid, fixture.layout)

        val question = result.questions.single()
        assertNull(result.failureReason)
        assertTrue(question.selectedOptions.isEmpty())
        assertTrue(question.selectedLabels.isEmpty())
        assertTrue(question.isBlank)
        assertFalse(question.isMultiMarked)
    }

    @Test
    fun usesTemplateOptionLabelsWhenProvided() {
        val fixture = Fixture(questionCount = 1, optionCounts = listOf(2))
        fixture.mark(questionIndex = 0, optionIndex = 0)

        val result = AndroidAnswerAreaReader.read(
            frame = fixture.frame(),
            grid = fixture.grid,
            layout = fixture.layout,
            optionLabelsByQuestion = listOf(listOf("T", "F")),
        )

        assertNull(result.failureReason)
        assertQuestion(result, questionIndex = 0, selectedOptions = listOf(0), selectedLabels = listOf("T"))
    }

    @Test
    fun keepsMultipleMarkedOptionsForMultipleChoiceQuestion() {
        val fixture = Fixture(questionCount = 1)
        fixture.mark(questionIndex = 0, optionIndex = 0)
        fixture.mark(questionIndex = 0, optionIndex = 2)

        val result = AndroidAnswerAreaReader.read(
            frame = fixture.frame(),
            grid = fixture.grid,
            layout = fixture.layout,
            questionTypesByQuestion = listOf(QuestionType.MULTIPLE),
        )

        val question = result.questions.single { it.questionIndex == 0 }
        assertEquals(listOf(0, 2), question.selectedOptions)
        assertEquals(listOf("A", "C"), question.selectedLabels)
        assertFalse(question.isBlank)
        assertTrue(question.isMultiMarked)
    }

    @Test
    fun solidMarksAreUnionedWithBubbleMarksInsteadOfReplacingThem() {
        val fixture = Fixture(questionCount = 1)
        fixture.mark(questionIndex = 0, optionIndex = 0)
        val projectedCells = AndroidPaperProjectedCells(
            questionCells = fixture.layout.questionMappings.associate { mapping ->
                AndroidPaperQuestionCellKey(mapping.questionIndex, mapping.optionIndex) to
                    fixture.grid.cell(mapping.row, mapping.column)
            },
            admissionNumberCells = emptyMap(),
            debugInfo = emptyList(),
        )
        val solidMarks = AndroidSolidMarkOverlay(
            questionCells = setOf(AndroidPaperQuestionCellKey(questionIndex = 0, optionIndex = 2)),
            admissionNumberCells = emptySet(),
            debugInfo = listOf("solid=test"),
        )

        val result = AndroidAnswerAreaReader.read(
            frame = fixture.frame(),
            layout = fixture.layout,
            projectedCells = projectedCells,
            optionLabelsByQuestion = listOf(listOf("A", "B", "C", "D")),
            questionTypesByQuestion = listOf(QuestionType.MULTIPLE),
            solidMarks = solidMarks,
        )

        val question = result.questions.single()
        assertEquals(listOf(0, 2), question.selectedOptions)
        assertTrue(result.debugInfo.contains("solidFusion=union"))
    }

    @Test
    fun downgradesSingleOptionBubbleReadFailuresWithoutFailingTheQuestion() {
        val fixture = Fixture(questionCount = 1)
        fixture.mark(questionIndex = 0, optionIndex = 0)
        val failedCellKey = AndroidPaperQuestionCellKey(questionIndex = 0, optionIndex = 1)
        val projectedCells = AndroidPaperProjectedCells(
            questionCells = fixture.layout.questionMappings.associate { mapping ->
                val key = AndroidPaperQuestionCellKey(mapping.questionIndex, mapping.optionIndex)
                key to if (key == failedCellKey) {
                    MiniProgramCell(
                        row = 10,
                        column = 10,
                        leftTop = MiniProgramGridPoint(row = 10.0, column = 10.0),
                        rightTop = MiniProgramGridPoint(row = 10.0, column = 14.0),
                        leftBottom = MiniProgramGridPoint(row = 14.0, column = 10.0),
                        rightBottom = MiniProgramGridPoint(row = 14.0, column = 14.0),
                    )
                } else {
                    fixture.grid.cell(mapping.row, mapping.column)
                }
            },
            admissionNumberCells = emptyMap(),
            debugInfo = emptyList(),
        )

        val result = AndroidAnswerAreaReader.read(
            frame = fixture.frame(),
            layout = fixture.layout,
            projectedCells = projectedCells,
        )

        assertNull(result.failureReason)
        assertQuestion(result, questionIndex = 0, selectedOptions = listOf(0), selectedLabels = listOf("A"))
        val failedOption = result.questions.single().optionResults.single { it.optionIndex == 1 }
        assertNotNull(failedOption.readResult.failureReason)
        assertTrue(result.debugInfo.contains("optionReadFailures=1"))
    }

    @Test
    fun singleChoiceStillSelectsStrongestMarkedOptionAndRecordsAmbiguity() {
        val fixture = Fixture(questionCount = 1)
        fixture.mark(questionIndex = 0, optionIndex = 0, markSize = 9)
        fixture.mark(questionIndex = 0, optionIndex = 1, markSize = 12)

        val result = AndroidAnswerAreaReader.read(
            frame = fixture.frame(),
            grid = fixture.grid,
            layout = fixture.layout,
            questionTypesByQuestion = listOf(QuestionType.SINGLE),
        )

        val question = result.questions.single()
        assertEquals(listOf(1), question.selectedOptions)
        assertTrue(question.isMultiMarked)
        assertTrue(result.debugInfo.any { it.startsWith("singleChoiceAmbiguous=") })
    }

    @Test
    fun readsMultipleQuestionsAcrossGroupsAndBands() {
        val fixture = Fixture(questionCount = 16)
        fixture.mark(questionIndex = 0, optionIndex = 0)
        fixture.mark(questionIndex = 1, optionIndex = 1)
        fixture.mark(questionIndex = 5, optionIndex = 2)
        fixture.mark(questionIndex = 10, optionIndex = 3)
        fixture.mark(questionIndex = 15, optionIndex = 0)

        val result = AndroidAnswerAreaReader.read(fixture.frame(), fixture.grid, fixture.layout)

        assertQuestion(result, questionIndex = 0, selectedOptions = listOf(0), selectedLabels = listOf("A"))
        assertQuestion(result, questionIndex = 1, selectedOptions = listOf(1), selectedLabels = listOf("B"))
        assertQuestion(result, questionIndex = 5, selectedOptions = listOf(2), selectedLabels = listOf("C"))
        assertQuestion(result, questionIndex = 10, selectedOptions = listOf(3), selectedLabels = listOf("D"))
        assertQuestion(result, questionIndex = 15, selectedOptions = listOf(0), selectedLabels = listOf("A"))
    }

    @Test
    fun doesNotReadAdmissionNumberMappings() {
        val fixture = Fixture(questionCount = 1)
        fixture.mark(questionIndex = 0, optionIndex = 0)
        val layoutWithBadAdmissionMappings = fixture.layout.copy(
            admissionNumberMappings = listOf(
                AndroidPaperAdmissionNumberMapping(
                    digitIndex = 0,
                    numberValue = 0,
                    row = 99,
                    column = 99,
                ),
            ),
        )

        val result = AndroidAnswerAreaReader.read(fixture.frame(), fixture.grid, layoutWithBadAdmissionMappings)

        assertNull(result.failureReason)
        assertQuestion(result, questionIndex = 0, selectedOptions = listOf(0), selectedLabels = listOf("A"))
    }

    @Test
    fun returnsFailureWhenQuestionMappingIsOutsideGrid() {
        val fixture = Fixture(questionCount = 1)
        val badLayout = fixture.layout.copy(
            questionMappings = listOf(
                AndroidPaperQuestionMapping(
                    questionIndex = 0,
                    optionIndex = 0,
                    row = 0,
                    column = fixture.grid.columns,
                ),
            ),
        )

        val result = AndroidAnswerAreaReader.read(fixture.frame(), fixture.grid, badLayout)

        assertTrue(result.questions.isEmpty())
        assertEquals("question mapping is outside grid: questionIndex=0, optionIndex=0, row=0, column=12", result.failureReason)
    }

    @Test
    fun preservesRawOptionResultsForEveryOption() {
        val fixture = Fixture(questionCount = 2)
        fixture.mark(questionIndex = 0, optionIndex = 0)

        val result = AndroidAnswerAreaReader.read(fixture.frame(), fixture.grid, fixture.layout)

        assertEquals(2, result.questions.size)
        result.questions.forEach { question ->
            assertEquals(4, question.optionResults.size)
            question.optionResults.forEach { option ->
                assertEquals(question.questionIndex, option.questionIndex)
                assertNotNull(option.readResult)
            }
        }
    }

    @Test
    fun passesEdgeCleanDirectionsForAnswerAreaBoundaries() {
        val fixture = Fixture(questionCount = 5)

        val result = AndroidAnswerAreaReader.read(fixture.frame(), fixture.grid, fixture.layout)

        val firstOption = result.questions.single { it.questionIndex == 0 }.optionResults.single { it.optionIndex == 0 }
        val lastOption = result.questions.single { it.questionIndex == 0 }.optionResults.single { it.optionIndex == 3 }
        assertTrue(firstOption.readResult.edgeCleanDirections.contains(MiniProgramEdgeCleanDirection.LEFT))
        assertTrue(firstOption.readResult.edgeCleanDirections.contains(MiniProgramEdgeCleanDirection.UP))
        assertTrue(lastOption.readResult.edgeCleanDirections.contains(MiniProgramEdgeCleanDirection.RIGHT))
    }

    private fun assertQuestion(
        result: AndroidAnswerAreaReadResult,
        questionIndex: Int,
        selectedOptions: List<Int>,
        selectedLabels: List<String>,
    ): AndroidQuestionReadResult {
        val question = result.questions.single { it.questionIndex == questionIndex }
        assertEquals(selectedOptions, question.selectedOptions)
        assertEquals(selectedLabels, question.selectedLabels)
        assertEquals(selectedOptions.isEmpty(), question.isBlank)
        assertEquals(selectedOptions.size > 1, question.isMultiMarked)
        return question
    }

    private class Fixture(
        questionCount: Int,
        optionCounts: List<Int> = List(questionCount) { 4 },
    ) {
        val layout: AndroidPaperTemplateLayout = AndroidPaperTemplateBuilder.build(optionCounts)
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

        fun mark(questionIndex: Int, optionIndex: Int, markSize: Int = MARK_SIZE) {
            val mapping = layout.questionMappings.single {
                it.questionIndex == questionIndex && it.optionIndex == optionIndex
            }
            val top = mapping.row * CELL_SIZE + MARK_MARGIN
            val left = mapping.column * CELL_SIZE + MARK_MARGIN
            for (row in top until top + markSize) {
                for (column in left until left + markSize) {
                    pixels[row * width + column] = 0
                }
            }
        }

        fun frame(): MiniProgramFrame = MiniProgramFrame(width = width, height = height, pixels = pixels)

        companion object {
            private const val CELL_SIZE = 24
            private const val MARK_MARGIN = 6
            private const val MARK_SIZE = 12
        }
    }
}
