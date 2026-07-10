package com.answercard.grader.miniprogram

import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateDistanceMatrixTest {
    @Test
    fun recognizesSupportedTemplateLengthsAtNearAndFarAcceptedScales() {
        val questionCounts = listOf(1, 15, 16, 30, 45, 60)

        questionCounts.forEach { questionCount ->
            listOf(true, false).forEach headerLoop@{ showHeader ->
                if (questionCount == 1 && !showHeader) return@headerLoop
                acceptedScales(questionCount, showHeader).forEach { scale ->
                    val template = template(questionCount, showHeader)
                    val result = AndroidOmrEngine.scan(renderedFrame(template, scale), template)
                    val label = "questions=$questionCount,header=$showHeader,scale=$scale"

                    assertTrue("$label: ${result.failureReason ?: result.debugInfo.joinToString()}", result.success)
                    assertEquals(label, if (showHeader) "1234" else "", result.admissionNumber?.digits)
                    assertEquals(label, listOf("A"), result.answerArea?.questions?.first()?.selectedLabels)
                    if (questionCount > 1) {
                        assertEquals(label, listOf("D"), result.answerArea?.questions?.last()?.selectedLabels)
                    }
                    assertEquals(label, if (questionCount == 1) 2.0 else 4.0, result.score?.totalScore ?: -1.0, 0.0)
                }
            }
        }
    }

    @Test
    fun singleQuestionHeaderlessCardRemainsFailClosedAtExistingQualityLimit() {
        val template = template(questionCount = 1, showHeader = false)

        val result = AndroidOmrEngine.scan(renderedFrame(template, 1.5f), template)

        assertFalse(result.success)
        assertNull(result.score)
    }

    @Test
    fun everyTemplateLengthFailsClosedBelowInformationRange() {
        listOf(1, 15, 16, 30, 45, 60).forEach { questionCount ->
            listOf(true, false).forEach { showHeader ->
                val template = template(questionCount, showHeader)
                val result = AndroidOmrEngine.scan(renderedFrame(template, 0.6f), template)
                val label = "questions=$questionCount,header=$showHeader,scale=0.6"

                assertFalse("$label unexpectedly succeeded", result.success)
                assertNull(label, result.score)
            }
        }
    }

    private fun renderedFrame(template: TemplateState, scale: Float): MiniProgramFrame {
        val renderer = DesktopTemplateCardRenderer(template, scale)
        if (template.showHeader) renderer.markAdmissionNumber("1234")
        renderer.markAnswer(1, "A")
        if (template.questions.size > 1) renderer.markAnswer(template.questions.size, "D")
        return renderer.frame()
    }

    private fun acceptedScales(questionCount: Int, showHeader: Boolean): List<Float> =
        if (questionCount <= 15 && !showHeader) listOf(1.5f, 1.2f) else listOf(1.0f, 0.85f)

    private fun template(questionCount: Int, showHeader: Boolean): TemplateState =
        TemplateState(
            name = "distance matrix",
            showHeader = showHeader,
            questions = (1..questionCount).map { number ->
                QuestionSetting(
                    number = number,
                    answer = when (number) {
                        1 -> "A"
                        questionCount -> "D"
                        else -> "A"
                    },
                    score = if (number == 1 || number == questionCount) 2 else 0,
                )
            },
        )
}
