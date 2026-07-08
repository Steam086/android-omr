package com.answercard.grader.miniprogram

import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidOmrEngineTest {
    @Test
    fun returnsFailureWhenLayoutCannotBeBuilt() {
        val template = TemplateState(
            name = "too many",
            questions = (1..61).map { QuestionSetting(number = it, answer = "A", score = 1) },
        )

        val result = AndroidOmrEngine.scan(whiteFrame(width = 320, height = 240), template)

        assertFalse(result.success)
        assertEquals("layout failed: questionOptionCounts must not exceed 60", result.failureReason)
        assertNull(result.layout)
        assertTrue(result.debugInfo.any { it.contains("layout failed") })
    }

    @Test
    fun returnsFailureWhenCornerAnchorsAreMissing() {
        val template = templateForSuccessfulPath()

        val result = AndroidOmrEngine.scan(whiteFrame(width = 420, height = 300), template)

        assertFalse(result.success)
        assertEquals("corner anchors not found", result.failureReason)
        assertNotNull(result.layout)
        assertNull(result.grid)
    }

    @Test
    fun scansSuccessfulPathWithPrecomputedGrid() {
        val template = templateForSuccessfulPath()
        val synthetic = AndroidOmrSyntheticFrameFactory(template)
        synthetic.markAdmissionNumber("1234")
        synthetic.markAnswer(questionIndex = 0, optionIndex = 0)
        synthetic.markAnswer(questionIndex = 1, optionIndex = 1)
        synthetic.markAnswer(questionIndex = 5, optionIndex = 2)
        synthetic.markAnswer(questionIndex = 10, optionIndex = 3)
        synthetic.markAnswer(questionIndex = 15, optionIndex = 0)

        val result = AndroidOmrEngine.scanWithPrecomputedGridForTest(
            frame = synthetic.frame(),
            template = template,
            grid = synthetic.grid,
        )

        assertTrue(result.success)
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 0 }?.selectedLabels)
        assertEquals(listOf("B"), result.answerArea?.questions?.single { it.questionIndex == 1 }?.selectedLabels)
        assertEquals(listOf("C"), result.answerArea?.questions?.single { it.questionIndex == 5 }?.selectedLabels)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 15 }?.selectedLabels)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun keepsDebugWhenAnswerAreaFails() {
        val template = templateForSuccessfulPath()
        val synthetic = AndroidOmrSyntheticFrameFactory(template)
        val badGrid = MiniProgramGridBuilder.build(
            lu = MiniProgramPoint(row = 0, column = 0),
            ld = MiniProgramPoint(row = synthetic.frameHeight - 1, column = 0),
            ru = MiniProgramPoint(row = 0, column = synthetic.frameWidth - 1),
            rd = MiniProgramPoint(row = synthetic.frameHeight - 1, column = synthetic.frameWidth - 1),
            rows = synthetic.layout.gridRows,
            columns = 1,
        )

        val result = AndroidOmrEngine.scanWithPrecomputedGridForTest(
            frame = synthetic.frame(),
            template = template,
            grid = badGrid,
        )

        assertFalse(result.success)
        assertEquals("answer area failed: question mapping is outside grid: questionIndex=0, optionIndex=1, row=5, column=1", result.failureReason)
        assertNull(result.score)
        assertTrue(result.debugInfo.any { it.contains("answer area failed") })
    }

    @Test
    fun scansCardWithBlankAdmissionNumberAsSuccess() {
        val template = templateForSuccessfulPath()
        val synthetic = AndroidOmrSyntheticFrameFactory(template)
        synthetic.markAnswer(questionIndex = 0, optionIndex = 0)
        synthetic.markAnswer(questionIndex = 1, optionIndex = 1)
        synthetic.markAnswer(questionIndex = 5, optionIndex = 2)
        synthetic.markAnswer(questionIndex = 10, optionIndex = 3)
        synthetic.markAnswer(questionIndex = 15, optionIndex = 0)

        val result = AndroidOmrEngine.scanWithPrecomputedGridForTest(
            frame = synthetic.frame(),
            template = template,
            grid = synthetic.grid,
        )

        assertTrue(result.success)
        assertNull(result.failureReason)
        assertEquals("????", result.admissionNumber?.digits)
        assertTrue(result.warnings.any { it.contains("admission number contains blank digit") })
        assertNotNull(result.answerArea)
        assertNotNull(result.score)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun scoresTwoOptionQuestionWithTemplateLabels() {
        val template = TemplateState(
            name = "two option",
            questions = listOf(
                QuestionSetting(number = 1, answer = "T", score = 2, optionCount = 2),
            ),
        )
        val synthetic = AndroidOmrSyntheticFrameFactory(template)
        synthetic.markAdmissionNumber("1234")
        synthetic.markAnswer(questionIndex = 0, optionIndex = 0)

        val result = AndroidOmrEngine.scanWithPrecomputedGridForTest(
            frame = synthetic.frame(),
            template = template,
            grid = synthetic.grid,
        )

        assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertEquals(listOf("T"), result.answerArea?.questions?.single()?.selectedLabels)
        assertEquals(2.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun toleratesMultiMarkedQuestionBySelectingDarkest() {
        val template = templateForSuccessfulPath()
        val synthetic = AndroidOmrSyntheticFrameFactory(template)
        synthetic.markAdmissionNumber("1234")
        synthetic.markAnswer(questionIndex = 0, optionIndex = 0)
        synthetic.markAnswer(questionIndex = 0, optionIndex = 1)
        synthetic.markAnswer(questionIndex = 1, optionIndex = 1)
        synthetic.markAnswer(questionIndex = 5, optionIndex = 2)
        synthetic.markAnswer(questionIndex = 10, optionIndex = 3)
        synthetic.markAnswer(questionIndex = 15, optionIndex = 0)

        val result = AndroidOmrEngine.scanWithPrecomputedGridForTest(
            frame = synthetic.frame(),
            template = template,
            grid = synthetic.grid,
        )

        assertTrue(result.success)
        assertTrue(result.answerArea!!.questions.single { it.questionIndex == 0 }.isMultiMarked)
    }

    private fun templateForSuccessfulPath(): TemplateState =
        TemplateState(
            name = "stage 9A",
            questions = (1..16).map { number ->
                val answer = when (number) {
                    1 -> "A"
                    2 -> "B"
                    6 -> "C"
                    11 -> "D"
                    16 -> "A"
                    else -> "A"
                }
                val score = if (number in listOf(1, 2, 6, 11, 16)) 2 else 0
                QuestionSetting(number = number, answer = answer, score = score)
            },
        )

    private fun whiteFrame(width: Int, height: Int): MiniProgramFrame =
        MiniProgramFrame(width = width, height = height, pixels = IntArray(width * height) { 255 })
}
