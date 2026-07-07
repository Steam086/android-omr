package com.answercard.grader.miniprogram

import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.QuestionType
import com.answercard.grader.template.TemplateState

object AndroidOmrScoreCalculator {
    fun score(
        template: TemplateState,
        answerArea: AndroidAnswerAreaReadResult,
    ): AndroidOmrScoreResult {
        val recognizedByIndex = answerArea.questions.associateBy { it.questionIndex }
        val templateIndexes = template.questions.map { it.number - 1 }.toSet()
        val items = template.questions.map { question ->
            scoreQuestion(question, recognizedByIndex[question.number - 1])
        }
        val itemWarnings = items.mapNotNull { item ->
            item.warning?.let { warning -> "question ${item.questionIndex + 1}: $warning" }
        }
        val answerAreaWarnings = answerArea.failureReason?.let { listOf("answer area: $it") }.orEmpty()
        val outsideQuestionWarnings = answerArea.questions
            .filter { it.questionIndex !in templateIndexes }
            .map { "question ${it.questionIndex + 1}: recognized question is outside template" }
        val warnings = answerAreaWarnings + itemWarnings + outsideQuestionWarnings

        return AndroidOmrScoreResult(
            totalScore = items.sumOf { it.earnedScore },
            maxScore = template.questions.sumOf { it.score }.toDouble(),
            items = items,
            success = warnings.isEmpty(),
            warnings = warnings,
        )
    }

    private fun scoreQuestion(
        question: QuestionSetting,
        recognized: AndroidQuestionReadResult?,
    ): AndroidOmrScoreItem {
        val questionIndex = question.number - 1
        val expectedLabels = expectedLabels(question)
        val expectedOptions = expectedOptionIndexes(question)
        val selectedOptions = selectedOptionIndexes(question, recognized)
        val selectedLabels = recognized?.selectedLabels.orEmpty()
        val score = question.score.toDouble()
        val warning = when {
            expectedOptions.isEmpty() -> "question has no expected answer"
            recognized == null -> "recognized question is missing"
            else -> null
        }
        val earnedScore = when {
            warning == "question has no expected answer" -> 0.0
            warning == "recognized question is missing" -> 0.0
            recognized == null -> 0.0
            question.type == QuestionType.MULTIPLE -> scoreMultipleChoice(question, expectedOptions, selectedOptions)
            selectedOptions == expectedOptions -> score
            else -> 0.0
        }

        return AndroidOmrScoreItem(
            questionIndex = questionIndex,
            expectedLabels = expectedLabels,
            selectedLabels = selectedLabels,
            score = score,
            earnedScore = earnedScore,
            isCorrect = earnedScore == score && expectedLabels.isNotEmpty(),
            isBlank = recognized?.isBlank ?: false,
            isMultiMarked = recognized?.isMultiMarked ?: false,
            warning = warning,
        )
    }

    private fun scoreMultipleChoice(
        question: QuestionSetting,
        expectedOptions: List<Int>,
        selectedOptions: List<Int>,
    ): Double {
        val expected = expectedOptions.toSet()
        val selected = selectedOptions.toSet()
        if (selected == expected) return question.score.toDouble()
        if (selected.isEmpty()) return 0.0
        if (!expected.containsAll(selected)) return 0.0
        return question.partialScore.coerceIn(0, question.score).toDouble()
    }

    private fun expectedLabels(question: QuestionSetting): List<String> {
        val answer = question.answer.trim()
        if (answer.isEmpty()) return emptyList()
        return question.options.filter { label -> answer.contains(label) }
    }

    private fun expectedOptionIndexes(question: QuestionSetting): List<Int> {
        val answer = question.answer.trim()
        if (answer.isEmpty()) return emptyList()
        return question.options.mapIndexedNotNull { index, label ->
            index.takeIf { answer.contains(label) }
        }
    }

    private fun selectedOptionIndexes(
        question: QuestionSetting,
        recognized: AndroidQuestionReadResult?,
    ): List<Int> {
        if (recognized == null) return emptyList()
        if (recognized.selectedOptions.isNotEmpty() || recognized.selectedLabels.isEmpty()) {
            return recognized.selectedOptions
        }
        return recognized.selectedLabels.mapNotNull { label ->
            question.options.indexOf(label).takeIf { it >= 0 }
        }
    }
}
