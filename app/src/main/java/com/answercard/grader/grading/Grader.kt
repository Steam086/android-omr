package com.answercard.grader.grading

import com.answercard.grader.template.TemplateState

data class GradeItem(
    val question: Int,
    val expectedAnswer: String,
    val recognizedAnswer: String?,
    val score: Int,
    val earnedScore: Int,
) {
    val isCorrect: Boolean
        get() = earnedScore == score
}

data class GradeResult(
    val totalScore: Int,
    val maxScore: Int,
    val items: List<GradeItem>,
)

object Grader {
    fun grade(template: TemplateState, recognizedAnswers: Map<Int, String>): GradeResult {
        val items = template.questions.map { question ->
            val recognized = recognizedAnswers[question.number]
            val earned = if (recognized == question.answer) question.score else 0
            GradeItem(
                question = question.number,
                expectedAnswer = question.answer,
                recognizedAnswer = recognized,
                score = question.score,
                earnedScore = earned,
            )
        }
        return GradeResult(
            totalScore = items.sumOf { it.earnedScore },
            maxScore = template.totalScore,
            items = items,
        )
    }
}
