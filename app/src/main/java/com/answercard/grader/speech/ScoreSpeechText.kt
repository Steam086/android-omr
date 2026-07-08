package com.answercard.grader.speech

object ScoreSpeechText {
    fun build(totalScore: Int, maxScore: Int, examId: String?): String {
        val scoreText = "得分${totalScore}分，满分${maxScore}分"
        // 扫描结果中未填涂的考号位以 '?' 占位，不适合播报
        return if (examId.isNullOrBlank() || examId.contains('?')) scoreText else "考号${examId}，$scoreText"
    }
}
