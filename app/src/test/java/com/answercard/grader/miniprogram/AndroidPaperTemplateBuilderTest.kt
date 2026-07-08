package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPaperTemplateBuilderTest {
    @Test
    fun buildsAndroidFiveByThreeLayoutForValidQuestions() {
        val layout = AndroidPaperTemplateBuilder.build(questionOptionCounts = List(15) { 4 })

        assertEquals(AndroidPaperTemplateType.ANDROID_5X3, layout.templateType)
        assertEquals(15, layout.questionCount)
        assertEquals(4, layout.optionCount)
        assertEquals(10, layout.gridRows)
        assertEquals(12, layout.gridColumns)
        assertEquals(60, layout.questionMappings.size)
        assertEquals("ANDROID_5X3", layout.debugInfo["templateType"])
    }

    @Test
    fun rejectsInvalidQuestionAndOptionCounts() {
        assertRejects("questionOptionCounts must not be empty") {
            AndroidPaperTemplateBuilder.build(questionOptionCounts = emptyList())
        }
        assertRejects("questionOptionCounts must not exceed 60") {
            AndroidPaperTemplateBuilder.build(questionOptionCounts = List(61) { 4 })
        }
        assertRejects("question option counts must be between 2 and 4") {
            AndroidPaperTemplateBuilder.build(questionOptionCounts = listOf(4, 1))
        }
        assertRejects("question option counts must be between 2 and 4") {
            AndroidPaperTemplateBuilder.build(questionOptionCounts = listOf(5))
        }
        assertRejects("admissionNumberDigits must be 4, or 0 for headerless cards") {
            AndroidPaperTemplateBuilder.build(questionOptionCounts = listOf(4), admissionNumberDigits = 3)
        }
        assertRejects("admissionNumberDigits must be 4, or 0 for headerless cards") {
            AndroidPaperTemplateBuilder.build(questionOptionCounts = listOf(4), admissionNumberDigits = 5)
        }
    }

    @Test
    fun questionMappingsStayInsideGrid() {
        val layout = AndroidPaperTemplateBuilder.build(questionOptionCounts = List(16) { 4 })

        layout.questionMappings.forEach { mapping ->
            assertTrue(mapping.row in 0 until layout.gridRows)
            assertTrue(mapping.column in 0 until layout.gridColumns)
        }
        assertInside(layout.answerArea, layout)
        assertInside(layout.admissionNumberArea, layout)
    }

    @Test
    fun firstThreeQuestionsMoveVerticallyWithinLeftGroup() {
        val layout = AndroidPaperTemplateBuilder.build(questionOptionCounts = List(15) { 4 })

        assertMapping(layout, questionIndex = 0, optionIndex = 0, row = 5, column = 0)
        assertMapping(layout, questionIndex = 1, optionIndex = 0, row = 6, column = 0)
        assertMapping(layout, questionIndex = 2, optionIndex = 0, row = 7, column = 0)
    }

    @Test
    fun questionsCrossToNextHorizontalGroupAfterFiveRows() {
        val layout = AndroidPaperTemplateBuilder.build(questionOptionCounts = List(16) { 4 })

        assertMapping(layout, questionIndex = 4, optionIndex = 0, row = 9, column = 0)
        assertMapping(layout, questionIndex = 5, optionIndex = 0, row = 5, column = 4)
        assertMapping(layout, questionIndex = 9, optionIndex = 0, row = 9, column = 4)
        assertMapping(layout, questionIndex = 10, optionIndex = 0, row = 5, column = 8)
        assertMapping(layout, questionIndex = 14, optionIndex = 0, row = 9, column = 8)
        assertMapping(layout, questionIndex = 15, optionIndex = 0, row = 10, column = 0)
    }

    @Test
    fun optionIndexesMoveHorizontallyWithinQuestion() {
        val layout = AndroidPaperTemplateBuilder.build(questionOptionCounts = listOf(4, 3, 2))

        assertEquals(listOf(0, 1, 2, 3), layout.questionMappings.filter { it.questionIndex == 0 }.map { it.optionIndex })
        assertMapping(layout, questionIndex = 0, optionIndex = 0, row = 5, column = 0)
        assertMapping(layout, questionIndex = 0, optionIndex = 1, row = 5, column = 1)
        assertMapping(layout, questionIndex = 0, optionIndex = 2, row = 5, column = 2)
        assertMapping(layout, questionIndex = 0, optionIndex = 3, row = 5, column = 3)
        assertEquals(listOf(0, 1, 2), layout.questionMappings.filter { it.questionIndex == 1 }.map { it.optionIndex })
        assertEquals(listOf(0, 1), layout.questionMappings.filter { it.questionIndex == 2 }.map { it.optionIndex })
    }

    @Test
    fun rowColumnDirectionIsExplicit() {
        val layout = AndroidPaperTemplateBuilder.build(questionOptionCounts = List(6) { 4 })

        val questionOneA = layout.questionMappings.single { it.questionIndex == 0 && it.optionIndex == 0 }
        val questionTwoA = layout.questionMappings.single { it.questionIndex == 1 && it.optionIndex == 0 }
        val questionOneB = layout.questionMappings.single { it.questionIndex == 0 && it.optionIndex == 1 }

        assertTrue(questionTwoA.row > questionOneA.row)
        assertEquals(questionOneA.column, questionTwoA.column)
        assertEquals(questionOneA.row, questionOneB.row)
        assertTrue(questionOneB.column > questionOneA.column)
    }

    @Test
    fun admissionNumberMappingsStayInsideGrid() {
        val layout = AndroidPaperTemplateBuilder.build(questionOptionCounts = List(15) { 4 }, admissionNumberDigits = 4)

        assertEquals(4 * 10, layout.admissionNumberMappings.size)
        layout.admissionNumberMappings.forEach { mapping ->
            assertTrue(mapping.row in 0 until layout.gridRows)
            assertTrue(mapping.column in 0 until layout.gridColumns)
        }
        assertEquals(0, layout.admissionNumberMappings.single { it.digitIndex == 0 && it.numberValue == 0 }.row)
        assertEquals(0, layout.admissionNumberMappings.single { it.digitIndex == 0 && it.numberValue == 0 }.column)
        assertEquals(9, layout.admissionNumberMappings.single { it.digitIndex == 0 && it.numberValue == 9 }.column)
    }

    @Test
    fun debugInfoDoesNotUseMiniProgramColumnModes() {
        val layout = AndroidPaperTemplateBuilder.build(questionOptionCounts = List(15) { 4 })

        assertEquals("ANDROID_5X3", layout.debugInfo["templateType"])
        assertEquals("5", layout.debugInfo["questionsPerGroup"])
        assertEquals("3", layout.debugInfo["groupsPerBand"])
        assertTrue(layout.debugInfo.values.none { it.contains("25") || it.contains("30") })
    }

    @Test
    fun sixtyQuestionLayoutCoversLastBandWithoutOverflow() {
        val layout = AndroidPaperTemplateBuilder.build(questionOptionCounts = List(60) { 4 })

        assertEquals(25, layout.gridRows)
        assertMapping(layout, questionIndex = 59, optionIndex = 0, row = 24, column = 8)
        assertMapping(layout, questionIndex = 59, optionIndex = 3, row = 24, column = 11)
        layout.questionMappings.forEach { mapping ->
            assertTrue(mapping.row in 0 until layout.gridRows)
            assertTrue(mapping.column in 0 until layout.gridColumns)
        }
    }

    private fun assertMapping(
        layout: AndroidPaperTemplateLayout,
        questionIndex: Int,
        optionIndex: Int,
        row: Int,
        column: Int,
    ) {
        val mapping = layout.questionMappings.single {
            it.questionIndex == questionIndex && it.optionIndex == optionIndex
        }
        assertEquals(row, mapping.row)
        assertEquals(column, mapping.column)
    }

    private fun assertInside(area: AndroidPaperGridArea, layout: AndroidPaperTemplateLayout) {
        assertTrue(area.startRow >= 0)
        assertTrue(area.endRow <= layout.gridRows)
        assertTrue(area.startColumn >= 0)
        assertTrue(area.endColumn <= layout.gridColumns)
        assertTrue(area.startRow < area.endRow)
        assertTrue(area.startColumn < area.endColumn)
    }

    private fun assertRejects(expectedMessage: String, action: () -> Unit) {
        val error = try {
            action()
            null
        } catch (error: IllegalArgumentException) {
            error
        }
        assertEquals(expectedMessage, error?.message)
    }
}
