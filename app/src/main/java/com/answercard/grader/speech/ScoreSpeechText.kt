package com.answercard.grader.speech

object ScoreSpeechText {
    fun build(totalScore: Int, maxScore: Int, examId: String?): String {
        val scoreText = "得分${totalScore}分，满分${maxScore}分"
        return if (examId.isNullOrBlank()) scoreText else "考号${examId}，$scoreText"
    }
}
