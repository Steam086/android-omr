package com.answercard.grader.miniprogram

import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPaperProjectedCellBuilderRefinementTest {
    @Test
    fun builderRunsEdgeRefinementAndFallsBackOnWhiteFrame() {
        val template = TemplateState(
            name = "projected-cell refinement",
            questions = listOf(QuestionSetting(number = 1, answer = "A", score = 2)),
        ).withShowHeader(false)
        val cardLayout = TemplateGeometry.buildLayout(template)
        val anchors = codedAnchors(cardLayout)
        val layout = AndroidPaperTemplateBuilder.build(
            questionOptionCounts = template.questions.map { it.optionCount },
            admissionNumberDigits = 0,
        )
        val frame = MiniProgramFrame(
            width = TemplateGeometry.renderedWidth(cardLayout).roundToInt(),
            height = TemplateGeometry.renderedHeight(cardLayout).roundToInt(),
            pixels = IntArray(
                TemplateGeometry.renderedWidth(cardLayout).roundToInt() *
                    TemplateGeometry.renderedHeight(cardLayout).roundToInt(),
            ) { 255 },
        )

        val cells = AndroidPaperProjectedCellBuilder.build(
            frame = frame,
            template = template,
            layout = layout,
            anchors = anchors,
        )

        assertEquals(4, cells.questionCells.size)
        assertTrue(cells.debugInfo.contains("edgeRefinement=fallback"))
        assertTrue(cells.debugInfo.contains("edgeRefinementFallbackGroups=1"))
    }

    private fun codedAnchors(layout: com.answercard.grader.template.CardLayout): MiniProgramAnchors {
        val centers = TemplateGeometry.cornerMarkerCenters(layout)
        val lu = candidate(MiniProgramCornerKind.LU, centers.lu.y.roundToInt(), centers.lu.x.roundToInt())
        val ld = candidate(MiniProgramCornerKind.LD, centers.ld.y.roundToInt(), centers.ld.x.roundToInt())
        val ru = candidate(MiniProgramCornerKind.RU, centers.ru.y.roundToInt(), centers.ru.x.roundToInt())
        val rd = candidate(MiniProgramCornerKind.RD, centers.rd.y.roundToInt(), centers.rd.x.roundToInt())
        return MiniProgramAnchors(
            lu = lu,
            ld = ld,
            ru = ru,
            rd = rd,
            quadCheck = MiniProgramGeometry.isQuad(lu.point, ld.point, ru.point, rd.point),
        )
    }

    private fun candidate(kind: MiniProgramCornerKind, row: Int, column: Int) =
        MiniProgramCornerCandidate(
            kind = kind,
            point = MiniProgramPoint(row, column),
            length = 26,
            source = CodedCornerMarkerDetector.SOURCE,
        )
}
