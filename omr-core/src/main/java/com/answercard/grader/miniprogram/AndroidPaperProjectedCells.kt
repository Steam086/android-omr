package com.answercard.grader.miniprogram

import com.answercard.grader.template.Rect
import com.answercard.grader.template.CardLayout
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplatePoint
import com.answercard.grader.template.TemplateState

data class AndroidPaperProjectedCells(
    val questionCells: Map<AndroidPaperQuestionCellKey, MiniProgramCell>,
    val admissionNumberCells: Map<AndroidPaperAdmissionNumberCellKey, MiniProgramCell>,
    val debugInfo: List<String>,
) {
    fun questionCell(mapping: AndroidPaperQuestionMapping): MiniProgramCell? =
        questionCells[AndroidPaperQuestionCellKey(mapping.questionIndex, mapping.optionIndex)]

    fun admissionNumberCell(mapping: AndroidPaperAdmissionNumberMapping): MiniProgramCell? =
        admissionNumberCells[AndroidPaperAdmissionNumberCellKey(mapping.digitIndex, mapping.numberValue)]
}

data class AndroidPaperQuestionCellKey(
    val questionIndex: Int,
    val optionIndex: Int,
)

data class AndroidPaperAdmissionNumberCellKey(
    val digitIndex: Int,
    val numberValue: Int,
)

object AndroidPaperProjectedCellBuilder {
    fun build(
        template: TemplateState,
        layout: AndroidPaperTemplateLayout,
        anchors: MiniProgramAnchors,
    ): AndroidPaperProjectedCells {
        val cardLayout = TemplateGeometry.buildLayout(template)
        val projector = AndroidPaperCoordinateProjector(cardLayout = cardLayout, anchors = anchors)
        val questionCells = layout.questionMappings.mapNotNull { mapping ->
            val question = template.questions.getOrNull(mapping.questionIndex) ?: return@mapNotNull null
            val optionLabel = question.options.getOrNull(mapping.optionIndex) ?: return@mapNotNull null
            val option = cardLayout.options.singleOrNull {
                it.question == question.number && it.option == optionLabel
            } ?: return@mapNotNull null

            AndroidPaperQuestionCellKey(mapping.questionIndex, mapping.optionIndex) to
                projector.cell(mapping.row, mapping.column, TemplateGeometry.renderedRect(option.rect))
        }.toMap()

        val admissionNumberCells = layout.admissionNumberMappings.associate { mapping ->
            val rect = TemplateGeometry.examIdDigitBox(
                layout = cardLayout,
                column = mapping.digitIndex,
                digit = mapping.numberValue,
            )
            AndroidPaperAdmissionNumberCellKey(mapping.digitIndex, mapping.numberValue) to
                projector.cell(mapping.row, mapping.column, TemplateGeometry.renderedRect(rect))
        }

        return AndroidPaperProjectedCells(
            questionCells = questionCells,
            admissionNumberCells = admissionNumberCells,
            debugInfo = listOf(
                "projection=templateGeometry",
                "questionCells=${questionCells.size}",
                "admissionNumberCells=${admissionNumberCells.size}",
            ),
        )
    }
}

private class AndroidPaperCoordinateProjector(
    cardLayout: CardLayout,
    anchors: MiniProgramAnchors,
) {
    private val source = AnchorReferenceResolver.projectionReference(cardLayout, anchors)
    private val targetLu = anchors.lu.point.toGridPoint()
    private val targetLd = anchors.ld.point.toGridPoint()
    private val targetRu = anchors.ru.point.toGridPoint()
    private val targetRd = anchors.rd.point.toGridPoint()
    private val mapping: PerspectiveMapping? = PerspectiveMapping.fromCorrespondences(
        source = listOf(source.lu, source.ru, source.rd, source.ld)
            .map { PerspectivePoint(it.x.toDouble(), it.y.toDouble()) },
        target = listOf(targetLu, targetRu, targetRd, targetLd)
            .map { PerspectivePoint(it.column, it.row) },
    )

    fun cell(row: Int, column: Int, rect: Rect): MiniProgramCell =
        MiniProgramCell(
            row = row,
            column = column,
            leftTop = project(TemplatePoint(rect.x, rect.y)),
            rightTop = project(TemplatePoint(rect.x + rect.w, rect.y)),
            leftBottom = project(TemplatePoint(rect.x, rect.y + rect.h)),
            rightBottom = project(TemplatePoint(rect.x + rect.w, rect.y + rect.h)),
        )

    private fun project(point: TemplatePoint): MiniProgramGridPoint {
        val mapped = mapping?.map(PerspectivePoint(point.x.toDouble(), point.y.toDouble()))
        if (mapped != null) return MiniProgramGridPoint(row = mapped.y, column = mapped.x)
        // Degenerate anchors: fall back to bilinear interpolation.
        val rowRatio = ((point.y - source.lu.y) / (source.ld.y - source.lu.y)).toDouble()
        val columnRatio = ((point.x - source.lu.x) / (source.ru.x - source.lu.x)).toDouble()
        return MiniProgramGridBuilder.interpolate(
            lu = targetLu,
            ld = targetLd,
            ru = targetRu,
            rd = targetRd,
            rowRatio = rowRatio,
            columnRatio = columnRatio,
        )
    }

    private fun MiniProgramPoint.toGridPoint(): MiniProgramGridPoint =
        MiniProgramGridPoint(row = row.toDouble(), column = column.toDouble())
}
