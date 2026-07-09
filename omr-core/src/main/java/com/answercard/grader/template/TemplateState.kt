package com.answercard.grader.template

data class QuestionSetting(
    val number: Int,
    val answer: String,
    val score: Int,
    val optionCount: Int = 4,
    val options: List<String> = optionLabels(optionCount),
    val type: QuestionType = QuestionType.SINGLE,
    val partialScore: Int = 0,
    val selected: Boolean = false,
)

enum class QuestionType {
    SINGLE,
    MULTIPLE,
}

data class AddQuestionRequest(
    val startNumber: Int,
    val count: Int,
    val score: Int,
    val optionCount: Int,
    val type: QuestionType = QuestionType.SINGLE,
)

data class EditQuestionRequest(
    val number: Int,
    val score: Int,
    val optionCount: Int,
    val type: QuestionType = QuestionType.SINGLE,
)

data class TemplateState(
    val name: String,
    val questions: List<QuestionSetting>,
    val examIdDigits: Int = 4,
    val showHeader: Boolean = true,
) {
    val totalScore: Int
        get() = questions.sumOf { it.score }

    fun withAnswer(question: Int, answer: String): TemplateState =
        copy(questions = questions.map {
            if (it.number == question) it.copy(answer = answer) else it
        })

    fun toggleAnswer(question: Int, answer: String): TemplateState =
        copy(questions = questions.map {
            if (it.number != question) {
                it
            } else if (it.type == QuestionType.MULTIPLE) {
                val normalizedAnswer = normalizeQuestionAnswer(it.answer, it.options, it.type)
                val nextAnswer = if (normalizedAnswer.contains(answer)) {
                    normalizedAnswer.replace(answer, "")
                } else {
                    normalizedAnswer + answer
                }
                it.copy(answer = normalizeQuestionAnswer(nextAnswer, it.options, it.type))
            } else if (it.answer == answer) {
                it.copy(answer = "", score = 0)
            } else {
                it.copy(answer = answer)
            }
        })

    fun withScore(question: Int, score: Int): TemplateState =
        copy(questions = questions.map {
            if (it.number == question) it.copy(score = score.coerceAtLeast(0)) else it
        })

    fun withQuestionOptions(question: Int, optionCount: Int): TemplateState =
        copy(questions = questions.map {
            if (it.number == question) {
                val safeCount = optionCount.coerceIn(2, 4)
                val labels = optionLabels(safeCount)
                it.copy(
                    optionCount = safeCount,
                    options = labels,
                    answer = normalizeQuestionAnswer(it.answer, labels, it.type),
                )
            } else {
                it
            }
        })

    fun withExamIdDigits(digits: Int): TemplateState =
        copy(examIdDigits = digits.coerceIn(1, 12))

    fun withShowHeader(show: Boolean): TemplateState =
        copy(showHeader = show)

    fun withName(name: String): TemplateState =
        copy(name = name.ifBlank { this.name })

    fun withQuestionCount(questionCount: Int): TemplateState {
        val count = questionCount.coerceIn(1, 60)
        val existingByNumber = questions.associateBy { it.number }
        val nextQuestions = (1..count).map { number ->
            existingByNumber[number] ?: QuestionSetting(number = number, answer = "A", score = 2)
        }
        return copy(questions = nextQuestions)
    }

    fun addQuestions(request: AddQuestionRequest): TemplateState {
        val available = (60 - questions.size).coerceAtLeast(0)
        val count = request.count.coerceAtLeast(0).coerceAtMost(available)
        if (count == 0) return this

        val safeOptionCount = request.optionCount.coerceIn(2, 4)
        val labels = optionLabels(safeOptionCount)
        val startNumber = request.startNumber.coerceAtLeast(1)
        val score = request.score.coerceAtLeast(0)
        val added = (0 until count).map { offset ->
            QuestionSetting(
                number = startNumber + offset,
                answer = "",
                score = score,
                optionCount = safeOptionCount,
                options = labels,
                type = request.type,
            )
        }

        return copy(questions = questions + added)
    }

    fun editQuestion(originalNumber: Int, request: EditQuestionRequest): TemplateState {
        val safeOptionCount = request.optionCount.coerceIn(2, 4)
        val labels = optionLabels(safeOptionCount)
        return copy(
            questions = questions.map { question ->
                if (question.number == originalNumber) {
                    question.copy(
                        number = request.number.coerceAtLeast(1),
                        score = request.score.coerceAtLeast(0),
                        optionCount = safeOptionCount,
                        options = labels,
                        answer = normalizeQuestionAnswer(question.answer, labels, request.type),
                        type = request.type,
                    )
                } else {
                    question
                }
            },
        )
    }

    fun deleteQuestion(number: Int): TemplateState =
        if (questions.size <= 1) {
            this
        } else {
            copy(questions = questions.filterNot { it.number == number })
        }

    fun toggleQuestionSelection(number: Int): TemplateState =
        copy(questions = questions.map {
            if (it.number == number) it.copy(selected = !it.selected) else it
        })

    fun clearQuestionSelection(): TemplateState =
        copy(questions = questions.map { it.copy(selected = false) })

    fun batchEditSelectedQuestions(
        score: Int,
        optionCount: Int,
        type: QuestionType = QuestionType.SINGLE,
    ): TemplateState {
        val safeOptionCount = optionCount.coerceIn(2, 4)
        val labels = optionLabels(safeOptionCount)
        return copy(
            questions = questions.map { question ->
                if (question.selected) {
                    question.copy(
                        score = score.coerceAtLeast(0),
                        optionCount = safeOptionCount,
                        options = labels,
                        answer = normalizeQuestionAnswer(question.answer, labels, type),
                        type = type,
                        selected = false,
                    )
                } else {
                    question
                }
            },
        )
    }

    companion object {
        fun default(): TemplateState = TemplateState(
            name = "默认试卷",
            questions = (1..15).map { number ->
                QuestionSetting(number = number, answer = "A", score = 2)
            },
        )
    }
}

fun optionLabels(optionCount: Int): List<String> =
    if (optionCount == 2) {
        listOf("T", "F")
    } else {
        TemplateGeometry.OPTIONS.take(optionCount.coerceIn(2, 4))
    }

fun normalizeQuestionAnswer(answer: String, labels: List<String>, type: QuestionType): String {
    val trimmed = answer.trim()
    return if (type == QuestionType.MULTIPLE) {
        labels.filter { label -> trimmed.contains(label) }.joinToString(separator = "")
    } else {
        labels.firstOrNull { label -> trimmed == label }.orEmpty()
    }
}
