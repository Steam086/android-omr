package com.answercard.grader.miniprogram

import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopHeaderlessTemplateScanTest {
    @Test
    fun scansJvmRenderedHeaderCardAsParityBaseline() {
        val template = sampleTemplate(showHeader = true)
        val renderer = DesktopTemplateCardRenderer(template)
        renderer.markAdmissionNumber("1234")
        markSampleAnswers(renderer)

        val result = AndroidOmrEngine.scan(frame = renderer.frame(), template = template)

        assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertNotNull(result.anchors)
        assertEquals("1234", result.admissionNumber?.digits)
        assertSampleAnswers(result)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun scansJvmRenderedHeaderlessCardWithCompactLayout() {
        val template = sampleTemplate(showHeader = false)
        val renderer = DesktopTemplateCardRenderer(template)
        markSampleAnswers(renderer)

        val result = AndroidOmrEngine.scan(frame = renderer.frame(), template = template)

        assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertNotNull(result.anchors)
        assertEquals("", result.admissionNumber?.digits)
        assertEquals(true, result.admissionNumber?.success)
        assertSampleAnswers(result)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun headerlessCardIsRenderedShorterThanHeaderCard() {
        val header = DesktopTemplateCardRenderer(sampleTemplate(showHeader = true)).frame()
        val headerless = DesktopTemplateCardRenderer(sampleTemplate(showHeader = false)).frame()

        assertEquals(header.width, headerless.width)
        assertTrue(
            "headerless card should be shorter: ${headerless.height} vs ${header.height}",
            headerless.height < header.height,
        )
    }

    @Test
    fun scansJvmRenderedHeaderlessSingleBandCard() {
        val template = TemplateState(
            name = "headerless single band",
            questions = (1..15).map { number ->
                QuestionSetting(number = number, answer = "A", score = if (number <= 5) 2 else 0)
            },
            showHeader = false,
        )
        val renderer = DesktopTemplateCardRenderer(template)
        (1..5).forEach { renderer.markAnswer(it, "A") }

        val result = AndroidOmrEngine.scan(frame = renderer.frame(), template = template)

        assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        (0 until 5).forEach { questionIndex ->
            assertEquals(
                listOf("A"),
                result.answerArea?.questions?.single { it.questionIndex == questionIndex }?.selectedLabels,
            )
        }
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    private fun markSampleAnswers(renderer: DesktopTemplateCardRenderer) {
        renderer.markAnswer(1, "A")
        renderer.markAnswer(2, "B")
        renderer.markAnswer(6, "C")
        renderer.markAnswer(11, "D")
        renderer.markAnswer(16, "A")
    }

    private fun assertSampleAnswers(result: AndroidOmrResult) {
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 0 }?.selectedLabels)
        assertEquals(listOf("B"), result.answerArea?.questions?.single { it.questionIndex == 1 }?.selectedLabels)
        assertEquals(listOf("C"), result.answerArea?.questions?.single { it.questionIndex == 5 }?.selectedLabels)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 15 }?.selectedLabels)
    }

    private fun sampleTemplate(showHeader: Boolean): TemplateState =
        TemplateState(
            name = "headerless scan regression",
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
            showHeader = showHeader,
        )
}
