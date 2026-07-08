package com.answercard.grader.template

import org.json.JSONArray
import org.json.JSONObject

object TemplateJson {
    fun toJson(template: TemplateState): String {
        val questions = JSONArray()
        template.questions.forEach { question ->
            questions.put(
                JSONObject()
                    .put("number", question.number)
                    .put("answer", question.answer)
                    .put("score", question.score)
                    .put("optionCount", question.optionCount)
                    .put("options", JSONArray(question.options))
                    .put("type", question.type.name)
                    .put("partialScore", question.partialScore)
                    .put("selected", false),
            )
        }
        return JSONObject()
            .put("name", template.name)
            .put("examIdDigits", template.examIdDigits)
            .put("showHeader", template.showHeader)
            .put("questions", questions)
            .toString()
    }

    fun fromJson(json: String?): TemplateState {
        if (json.isNullOrBlank()) return TemplateState.default()
        return runCatching {
            val root = JSONObject(json)
            val name = root.optString("name", "默认试卷").ifBlank { "默认试卷" }
            val examIdDigits = root.optInt("examIdDigits", 4).coerceIn(1, 12)
            val showHeader = root.optBoolean("showHeader", true)
            val rawQuestions = root.getJSONArray("questions")
            val questions = (0 until rawQuestions.length()).map { index ->
                val item = rawQuestions.getJSONObject(index)
                val optionCount = item.optInt("optionCount", 4).coerceIn(2, 4)
                val labels = item.optJSONArray("options")?.let { rawOptions ->
                    (0 until rawOptions.length()).map { optionIndex -> rawOptions.optString(optionIndex) }
                        .filter { it.isNotBlank() }
                        .take(4)
                }.takeUnless { it.isNullOrEmpty() } ?: optionLabels(optionCount)
                val type = runCatching {
                    QuestionType.valueOf(item.optString("type", QuestionType.SINGLE.name))
                }.getOrDefault(QuestionType.SINGLE)

                QuestionSetting(
                    number = item.getInt("number"),
                    answer = item.optString("answer", "A").takeIf { it in labels }.orEmpty(),
                    score = item.optInt("score", 2).coerceAtLeast(0),
                    optionCount = optionCount,
                    options = labels,
                    type = type,
                    partialScore = item.optInt("partialScore", 0).coerceAtLeast(0),
                    selected = false,
                )
            }.filter { it.number in 1..60 }
                .sortedBy { it.number }

            if (questions.isEmpty()) TemplateState.default() else TemplateState(name, questions, examIdDigits, showHeader)
        }.getOrDefault(TemplateState.default())
    }
}
