package com.answercard.grader.ui

import com.answercard.grader.template.Rect
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState

data class ScanTemplateOverlay(
    val renderedWidth: Float,
    val renderedHeight: Float,
    val answerRects: List<ScanAnswerOverlayRect>,
    val admissionRects: List<ScanAdmissionOverlayRect>,
) {
    companion object {
        fun from(template: TemplateState, result: ScanDisplayResult?): ScanTemplateOverlay {
            val layout = TemplateGeometry.buildLayout(template)
            if (result == null) {
                return ScanTemplateOverlay(
                    renderedWidth = TemplateGeometry.renderedWidth(layout),
                    renderedHeight = TemplateGeometry.renderedHeight(layout),
                    answerRects = emptyList(),
                    admissionRects = emptyList(),
                )
            }

            val answerRects = result.answerMarks.mapNotNull { mark ->
                val question = template.questions.getOrNull(mark.questionIndex) ?: return@mapNotNull null
                val option = layout.options.firstOrNull {
                    it.question == question.number && it.option == mark.optionLabel
                } ?: return@mapNotNull null
                ScanAnswerOverlayRect(
                    rect = TemplateGeometry.renderedRect(option.rect),
                    state = mark.state,
                )
            }

            val admissionRects = result.admissionMarks.mapNotNull { mark ->
                if (!template.showHeader || mark.digitIndex !in layout.examIdRows.indices) {
                    return@mapNotNull null
                }
                ScanAdmissionOverlayRect(
                    rect = TemplateGeometry.renderedRect(
                        TemplateGeometry.examIdDigitBox(
                            layout = layout,
                            column = mark.digitIndex,
                            digit = mark.numberValue,
                        ),
                    ),
                    state = mark.state,
                )
            }

            return ScanTemplateOverlay(
                renderedWidth = TemplateGeometry.renderedWidth(layout),
                renderedHeight = TemplateGeometry.renderedHeight(layout),
                answerRects = answerRects,
                admissionRects = admissionRects,
            )
        }
    }
}

data class ScanAnswerOverlayRect(
    val rect: Rect,
    val state: ScanAnswerMarkState,
)

data class ScanAdmissionOverlayRect(
    val rect: Rect,
    val state: ScanAdmissionMarkState,
)
