package com.answercard.grader.template

object TemplateSummary {
    fun detail(template: TemplateState): String =
        "${template.questions.size}题 · ${template.totalScore}分"
}
