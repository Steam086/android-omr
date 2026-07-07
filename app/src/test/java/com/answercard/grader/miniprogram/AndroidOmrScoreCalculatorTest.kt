package com.answercard.grader.miniprogram

import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.QuestionType
import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidOmrScoreCalculatorTest {
    @Test
    fun scoresCorrectSingleChoiceWithFullScore() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(QuestionSetting(number = 1, answer = "A", score = 2)),
            answerArea = answerArea(question(questionIndex = 0, selectedLabels = listOf("A"))),
        )

        assertEquals(2.0, result.totalScore, 0.0)
        assertEquals(2.0, result.maxScore, 0.0)
        assertTrue(result.items.single().isCorrect)
        assertEquals(2.0, result.items.single().earnedScore, 0.0)
    }

    @Test
    fun scoresWrongSingleChoiceAsZero() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(QuestionSetting(number = 1, answer = "A", score = 2)),
            answerArea = answerArea(question(questionIndex = 0, selectedLabels = listOf("B"))),
        )

        assertEquals(0.0, result.totalScore, 0.0)
        assertFalse(result.items.single().isCorrect)
    }

    @Test
    fun scoresBlankSingleChoiceAsZeroAndMarksBlank() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(QuestionSetting(number = 1, answer = "A", score = 2)),
            answerArea = answerArea(question(questionIndex = 0, selectedLabels = emptyList())),
        )

        val item = result.items.single()
        assertEquals(0.0, item.earnedScore, 0.0)
        assertTrue(item.isBlank)
        assertEquals(null, item.warning)
    }

    @Test
    fun scoresMultiMarkedSingleChoiceByBestOption() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(QuestionSetting(number = 1, answer = "A", score = 2)),
            answerArea = answerArea(question(questionIndex = 0, selectedLabels = listOf("A"))),
        )

        val item = result.items.single()
        assertEquals(2.0, item.earnedScore, 0.0)
        assertEquals(null, item.warning)
    }

    @Test
    fun scoresCorrectMultipleChoiceWithFullScore() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(
                QuestionSetting(
                    number = 1,
                    answer = "AC",
                    score = 4,
                    type = QuestionType.MULTIPLE,
                ),
            ),
            answerArea = answerArea(question(questionIndex = 0, selectedLabels = listOf("A", "C"))),
        )

        assertEquals(4.0, result.totalScore, 0.0)
        assertTrue(result.items.single().isCorrect)
    }

    @Test
    fun scoresMultipleChoiceMissingOnlyWithPartialScore() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(
                QuestionSetting(
                    number = 1,
                    answer = "AC",
                    score = 4,
                    type = QuestionType.MULTIPLE,
                    partialScore = 1,
                ),
            ),
            answerArea = answerArea(question(questionIndex = 0, selectedLabels = listOf("A"))),
        )

        val item = result.items.single()
        assertEquals(1.0, item.earnedScore, 0.0)
        assertFalse(item.isCorrect)
    }

    @Test
    fun scoresMultipleChoiceMissingOnlyAsZeroWhenPartialScoreIsZero() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(
                QuestionSetting(
                    number = 1,
                    answer = "AC",
                    score = 4,
                    type = QuestionType.MULTIPLE,
                    partialScore = 0,
                ),
            ),
            answerArea = answerArea(question(questionIndex = 0, selectedLabels = listOf("A"))),
        )

        assertEquals(0.0, result.items.single().earnedScore, 0.0)
    }

    @Test
    fun capsPartialScoreAtQuestionScore() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(
                QuestionSetting(
                    number = 1,
                    answer = "AC",
                    score = 4,
                    type = QuestionType.MULTIPLE,
                    partialScore = 9,
                ),
            ),
            answerArea = answerArea(question(questionIndex = 0, selectedLabels = listOf("A"))),
        )

        assertEquals(4.0, result.items.single().earnedScore, 0.0)
    }

    @Test
    fun scoresMultipleChoiceWrongExtraSelectionAsZero() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(
                QuestionSetting(
                    number = 1,
                    answer = "AC",
                    score = 4,
                    type = QuestionType.MULTIPLE,
                    partialScore = 1,
                ),
            ),
            answerArea = answerArea(question(questionIndex = 0, selectedLabels = listOf("A", "B"))),
        )

        assertEquals(0.0, result.items.single().earnedScore, 0.0)
    }

    @Test
    fun scoresTwoOptionJudgementQuestionLikeSingleChoice() {
        val correct = AndroidOmrScoreCalculator.score(
            template = template(QuestionSetting(number = 1, answer = "T", score = 2, optionCount = 2)),
            answerArea = answerArea(question(questionIndex = 0, selectedLabels = listOf("T"))),
        )
        val wrong = AndroidOmrScoreCalculator.score(
            template = template(QuestionSetting(number = 1, answer = "T", score = 2, optionCount = 2)),
            answerArea = answerArea(question(questionIndex = 0, selectedLabels = listOf("F"))),
        )

        assertEquals(2.0, correct.items.single().earnedScore, 0.0)
        assertEquals(0.0, wrong.items.single().earnedScore, 0.0)
    }

    @Test
    fun scoresTwoOptionQuestionByOptionIndexWhenReaderLabelsAreGeneric() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(QuestionSetting(number = 1, answer = "T", score = 2, optionCount = 2)),
            answerArea = answerArea(
                question(
                    questionIndex = 0,
                    selectedOptions = listOf(0),
                    selectedLabels = listOf("A"),
                ),
            ),
        )

        assertEquals(2.0, result.items.single().earnedScore, 0.0)
    }

    @Test
    fun reportsWarningWhenQuestionHasNoExpectedAnswer() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(QuestionSetting(number = 1, answer = "", score = 2)),
            answerArea = answerArea(question(questionIndex = 0, selectedLabels = listOf("A"))),
        )

        val item = result.items.single()
        assertEquals(0.0, item.earnedScore, 0.0)
        assertEquals("question has no expected answer", item.warning)
        assertTrue(result.warnings.contains("question 1: question has no expected answer"))
    }

    @Test
    fun reportsWarningWhenRecognizedQuestionIsMissing() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(QuestionSetting(number = 1, answer = "A", score = 2)),
            answerArea = answerArea(),
        )

        val item = result.items.single()
        assertEquals(0.0, item.earnedScore, 0.0)
        assertEquals("recognized question is missing", item.warning)
        assertTrue(result.warnings.contains("question 1: recognized question is missing"))
    }

    @Test
    fun carriesAnswerAreaFailureReasonIntoWarnings() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(QuestionSetting(number = 1, answer = "A", score = 2)),
            answerArea = answerArea(
                question(questionIndex = 0, selectedLabels = listOf("A")),
                failureReason = "answer area read failed",
            ),
        )

        assertFalse(result.success)
        assertTrue(result.warnings.contains("answer area: answer area read failed"))
    }

    @Test
    fun reportsWarningForRecognizedQuestionOutsideTemplate() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(QuestionSetting(number = 1, answer = "A", score = 2)),
            answerArea = answerArea(
                question(questionIndex = 0, selectedLabels = listOf("A")),
                question(questionIndex = 5, selectedLabels = listOf("B")),
            ),
        )

        assertFalse(result.success)
        assertTrue(result.warnings.contains("question 6: recognized question is outside template"))
    }

    @Test
    fun sumsTotalScoreAndMaxScore() {
        val result = AndroidOmrScoreCalculator.score(
            template = template(
                QuestionSetting(number = 1, answer = "A", score = 2),
                QuestionSetting(number = 2, answer = "B", score = 3),
                QuestionSetting(number = 3, answer = "C", score = 4),
            ),
            answerArea = answerArea(
                question(questionIndex = 0, selectedLabels = listOf("A")),
                question(questionIndex = 1, selectedLabels = listOf("C")),
                question(questionIndex = 2, selectedLabels = listOf("C")),
            ),
        )

        assertEquals(6.0, result.totalScore, 0.0)
        assertEquals(9.0, result.maxScore, 0.0)
        assertEquals(listOf(2.0, 0.0, 4.0), result.items.map { it.earnedScore })
    }

    private fun template(vararg questions: QuestionSetting): TemplateState =
        TemplateState(name = "test", questions = questions.toList())

    private fun answerArea(
        vararg questions: AndroidQuestionReadResult,
        failureReason: String? = null,
    ): AndroidAnswerAreaReadResult =
        AndroidAnswerAreaReadResult(
            questions = questions.toList(),
            failureReason = failureReason,
            debugInfo = emptyList(),
        )

    private fun question(
        questionIndex: Int,
        selectedLabels: List<String>,
        selectedOptions: List<Int> = selectedLabels.map(::optionIndexForLabel),
    ): AndroidQuestionReadResult =
        AndroidQuestionReadResult(
            questionIndex = questionIndex,
            selectedOptions = selectedOptions,
            selectedLabels = selectedLabels,
            optionResults = emptyList(),
            isBlank = selectedLabels.isEmpty(),
            isMultiMarked = selectedLabels.size > 1,
        )

    private fun optionIndexForLabel(label: String): Int =
        when (label) {
            "A", "T" -> 0
            "B", "F" -> 1
            "C" -> 2
            "D" -> 3
            else -> -1
        }
}
