package com.answercard.grader.miniprogram

import com.answercard.grader.template.CardLayout
import com.answercard.grader.template.Rect
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplatePoint
import com.answercard.grader.template.TemplateState
import kotlin.math.abs

data class AndroidSolidMarkOverlay(
    val questionCells: Set<AndroidPaperQuestionCellKey>,
    val admissionNumberCells: Set<AndroidPaperAdmissionNumberCellKey>,
    val debugInfo: List<String>,
) {
    val hasQuestionMarks: Boolean get() = questionCells.isNotEmpty()
    val hasAdmissionNumberMarks: Boolean get() = admissionNumberCells.isNotEmpty()

    fun isQuestionMarked(mapping: AndroidPaperQuestionMapping): Boolean =
        AndroidPaperQuestionCellKey(mapping.questionIndex, mapping.optionIndex) in questionCells

    fun isAdmissionNumberMarked(mapping: AndroidPaperAdmissionNumberMapping): Boolean =
        AndroidPaperAdmissionNumberCellKey(mapping.digitIndex, mapping.numberValue) in admissionNumberCells
}

object AndroidSolidMarkDetector {
    private const val MIN_COMPONENT_AREA_RATIO = 0.0001
    private const val MIN_COMPONENT_FILL_RATIO = 0.82f
    private const val MIN_COMPONENT_SIZE_RATIO = 0.008
    private val OPTION_TOLERANCE_X = (TemplateGeometry.OPTION_STEP_X - TemplateGeometry.OPTION_BOX_W) / 2f
    private val OPTION_TOLERANCE_Y = (TemplateGeometry.QUESTION_ROW_STEP_Y - TemplateGeometry.OPTION_BOX_H) / 2f
    private val DIGIT_TOLERANCE_X = (TemplateGeometry.EXAM_DIGIT_STEP_X - TemplateGeometry.EXAM_DIGIT_BOX_W) / 2f
    private val DIGIT_TOLERANCE_Y = (TemplateGeometry.EXAM_ROW_STEP_Y - TemplateGeometry.EXAM_DIGIT_BOX_H) / 2f

    fun detect(
        frame: MiniProgramFrame,
        template: TemplateState,
        anchors: MiniProgramAnchors,
    ): AndroidSolidMarkOverlay {
        val cardLayout = TemplateGeometry.buildLayout(template)
        val components = denseComponents(frame)
        val questionIndexByNumber = template.questions
            .mapIndexed { index, question -> question.number to index }
            .toMap()

        // Trace anchors may sit on the outer or the inner edge of the right corner brackets
        // depending on the capture, so match against both references and keep the one whose
        // inverted mark centers explain more dense components, closest to the cell centers;
        // remaining ties keep the legacy source-based preference (candidate order).
        val candidates = sourceReferenceCandidates(cardLayout, anchors)
        val best = candidates
            .map { (name, source) ->
                matchComponents(
                    components = components,
                    anchors = anchors,
                    source = source,
                    cardLayout = cardLayout,
                    questionIndexByNumber = questionIndexByNumber,
                    admissionNumberDigits = template.examIdDigits,
                    referenceName = name,
                )
            }
            .sortedWith(
                compareByDescending<ComponentMatchResult> { it.matchedComponents }
                    .thenBy { it.totalCenterDistance },
            )
            .first()

        return AndroidSolidMarkOverlay(
            questionCells = best.questionCells,
            admissionNumberCells = best.admissionNumberCells,
            debugInfo = listOf(
                "solidMarkComponents=${components.size}",
                "solidMarkReference=${best.referenceName}",
                "solidQuestionMarks=${best.questionCells.size}",
                "solidAdmissionMarks=${best.admissionNumberCells.size}",
            ),
        )
    }

    private data class ComponentMatchResult(
        val referenceName: String,
        val questionCells: Set<AndroidPaperQuestionCellKey>,
        val admissionNumberCells: Set<AndroidPaperAdmissionNumberCellKey>,
        val matchedComponents: Int,
        val totalCenterDistance: Double,
    )

    private fun matchComponents(
        components: List<MiniProgramComponent>,
        anchors: MiniProgramAnchors,
        source: CornerAnchorReferencePoints,
        cardLayout: CardLayout,
        questionIndexByNumber: Map<Int, Int>,
        admissionNumberDigits: Int,
        referenceName: String,
    ): ComponentMatchResult {
        val questionCells = mutableSetOf<AndroidPaperQuestionCellKey>()
        val admissionNumberCells = mutableSetOf<AndroidPaperAdmissionNumberCellKey>()
        var matchedComponents = 0
        var totalCenterDistance = 0.0
        val mapping = PerspectiveMapping.fromCorrespondences(
            source = listOf(source.lu, source.ru, source.rd, source.ld)
                .map { PerspectivePoint(it.x.toDouble(), it.y.toDouble()) },
            target = listOf(anchors.lu, anchors.ru, anchors.rd, anchors.ld)
                .map { PerspectivePoint(it.point.column.toDouble(), it.point.row.toDouble()) },
        )
        components.forEach { component ->
            val sourcePoint = mapping
                ?.invert(PerspectivePoint(component.centerColumn, component.centerRow))
                ?.let { TemplatePoint(it.x.toFloat(), it.y.toFloat()) }
                ?: invert(
                    target = MiniProgramGridPoint(row = component.centerRow, column = component.centerColumn),
                    anchors = anchors,
                    source = source,
                )
                ?: return@forEach
            val question = matchQuestionCell(cardLayout, questionIndexByNumber, sourcePoint)
            val admission = matchAdmissionNumberCell(cardLayout, admissionNumberDigits, sourcePoint)
            question?.let { match ->
                questionCells += match.key
                totalCenterDistance += match.centerDistance
            }
            admission?.let { match ->
                admissionNumberCells += match.key
                totalCenterDistance += match.centerDistance
            }
            if (question != null || admission != null) matchedComponents += 1
        }
        return ComponentMatchResult(
            referenceName = referenceName,
            questionCells = questionCells,
            admissionNumberCells = admissionNumberCells,
            matchedComponents = matchedComponents,
            totalCenterDistance = totalCenterDistance,
        )
    }

    internal data class CellMatch<T>(
        val key: T,
        val centerDistance: Double,
    )

    private fun denseComponents(frame: MiniProgramFrame): List<MiniProgramComponent> {
        val threshold = MiniProgramGeometry.threshold(frame)
        val minArea = maxOf(80, (frame.width * frame.height * MIN_COMPONENT_AREA_RATIO).toInt())
        val minSize = maxOf(8, (minOf(frame.width, frame.height) * MIN_COMPONENT_SIZE_RATIO).toInt())
        return MiniProgramComponentScanner.scan(frame, threshold)
            .filter { component -> component.area >= minArea }
            .filter { component -> component.rect.width >= minSize && component.rect.height >= minSize }
            .filter { component -> component.fillRatio >= MIN_COMPONENT_FILL_RATIO }
            .filterNot { component ->
                component.rect.left <= 0 || component.rect.top <= 0 ||
                    component.rect.right >= frame.width || component.rect.bottom >= frame.height
            }
    }

    internal fun matchQuestionCell(
        cardLayout: CardLayout,
        questionIndexByNumber: Map<Int, Int>,
        sourcePoint: TemplatePoint,
    ): CellMatch<AndroidPaperQuestionCellKey>? {
        val option = cardLayout.options
            .filter { candidate ->
                TemplateGeometry.renderedRect(candidate.rect)
                    .containsWithTolerance(sourcePoint, OPTION_TOLERANCE_X, OPTION_TOLERANCE_Y)
            }
            .minByOrNull { candidate -> TemplateGeometry.renderedRect(candidate.rect).centerDistance(sourcePoint) }
            ?: return null
        val questionIndex = questionIndexByNumber[option.question] ?: return null
        val optionIndex = cardLayout.options
            .filter { it.question == option.question }
            .indexOfFirst { it.option == option.option }
            .takeIf { it >= 0 } ?: return null
        return CellMatch(
            key = AndroidPaperQuestionCellKey(questionIndex, optionIndex),
            centerDistance = TemplateGeometry.renderedRect(option.rect).centerDistance(sourcePoint),
        )
    }

    internal fun matchAdmissionNumberCell(
        cardLayout: CardLayout,
        admissionNumberDigits: Int,
        sourcePoint: TemplatePoint,
    ): CellMatch<AndroidPaperAdmissionNumberCellKey>? {
        if (cardLayout.examIdRows.isEmpty()) return null
        var best: CellMatch<AndroidPaperAdmissionNumberCellKey>? = null
        for (digitIndex in 0 until admissionNumberDigits) {
            for (numberValue in 0..9) {
                val rect = TemplateGeometry.renderedRect(
                    TemplateGeometry.examIdDigitBox(
                        layout = cardLayout,
                        column = digitIndex,
                        digit = numberValue,
                    ),
                )
                if (!rect.containsWithTolerance(sourcePoint, DIGIT_TOLERANCE_X, DIGIT_TOLERANCE_Y)) continue
                val distance = rect.centerDistance(sourcePoint)
                if (best == null || distance < best.centerDistance) {
                    best = CellMatch(
                        key = AndroidPaperAdmissionNumberCellKey(digitIndex, numberValue),
                        centerDistance = distance,
                    )
                }
            }
        }
        return best
    }

    private fun Rect.containsWithTolerance(point: TemplatePoint, toleranceX: Float, toleranceY: Float): Boolean =
        point.x >= x - toleranceX &&
            point.x <= x + w + toleranceX &&
            point.y >= y - toleranceY &&
            point.y <= y + h + toleranceY

    private fun Rect.centerDistance(point: TemplatePoint): Double {
        val dx = (point.x - (x + w / 2f)).toDouble()
        val dy = (point.y - (y + h / 2f)).toDouble()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun sourceReferenceCandidates(
        cardLayout: CardLayout,
        anchors: MiniProgramAnchors,
    ): List<Pair<String, CornerAnchorReferencePoints>> {
        if (AnchorReferenceResolver.isSolidMarker(anchors)) {
            val centers = TemplateGeometry.cornerMarkerCenters(cardLayout)
            return listOf(
                "markerCenters" to CornerAnchorReferencePoints(
                    lu = centers.lu,
                    ld = centers.ld,
                    ru = centers.ru,
                    rd = centers.rd,
                ),
            )
        }
        val reference = TemplateGeometry.cornerAnchorReference(cardLayout)
        val outer = CornerAnchorReferencePoints(
            lu = reference.lu,
            ld = reference.ld,
            ru = reference.ru,
            rd = reference.rd,
        )

        val adjustedRight = reference.ru.x -
            (TemplateGeometry.CORNER_BRACKET_SIZE - TemplateGeometry.CORNER_BRACKET_THICKNESS)
        val innerRight = CornerAnchorReferencePoints(
            lu = reference.lu,
            ld = reference.ld,
            ru = TemplatePoint(adjustedRight, reference.ru.y),
            rd = TemplatePoint(adjustedRight, reference.rd.y),
        )

        val strongTraceCount = listOf(anchors.lu, anchors.ld, anchors.ru, anchors.rd)
            .count { it.source == "strong-trace" }
        return if (strongTraceCount < 3) {
            listOf("outer" to outer, "innerRight" to innerRight)
        } else {
            listOf("innerRight" to innerRight, "outer" to outer)
        }
    }

    private fun invert(
        target: MiniProgramGridPoint,
        anchors: MiniProgramAnchors,
        source: CornerAnchorReferencePoints,
    ): TemplatePoint? {
        var rowRatio = 0.5
        var columnRatio = 0.5
        repeat(16) {
            val projected = interpolate(anchors, rowRatio, columnRatio)
            val rowError = target.row - projected.row
            val columnError = target.column - projected.column
            if (abs(rowError) + abs(columnError) < 0.001) return@repeat

            val dRow = 0.0001
            val rowStep = interpolate(anchors, rowRatio + dRow, columnRatio)
            val columnStep = interpolate(anchors, rowRatio, columnRatio + dRow)
            val j00 = (rowStep.row - projected.row) / dRow
            val j10 = (rowStep.column - projected.column) / dRow
            val j01 = (columnStep.row - projected.row) / dRow
            val j11 = (columnStep.column - projected.column) / dRow
            val determinant = j00 * j11 - j01 * j10
            if (abs(determinant) < 1e-9) return null

            rowRatio += (rowError * j11 - j01 * columnError) / determinant
            columnRatio += (j00 * columnError - rowError * j10) / determinant
        }

        if (!rowRatio.isFinite() || !columnRatio.isFinite()) return null
        val x = source.lu.x + (source.ru.x - source.lu.x) * columnRatio
        val y = source.lu.y + (source.ld.y - source.lu.y) * rowRatio
        return TemplatePoint(x.toFloat(), y.toFloat())
    }

    private fun interpolate(
        anchors: MiniProgramAnchors,
        rowRatio: Double,
        columnRatio: Double,
    ): MiniProgramGridPoint =
        MiniProgramGridBuilder.interpolate(
            lu = anchors.lu.point.toGridPoint(),
            ld = anchors.ld.point.toGridPoint(),
            ru = anchors.ru.point.toGridPoint(),
            rd = anchors.rd.point.toGridPoint(),
            rowRatio = rowRatio,
            columnRatio = columnRatio,
        )

    private fun MiniProgramPoint.toGridPoint(): MiniProgramGridPoint =
        MiniProgramGridPoint(row = row.toDouble(), column = column.toDouble())

    private data class CornerAnchorReferencePoints(
        val lu: TemplatePoint,
        val ld: TemplatePoint,
        val ru: TemplatePoint,
        val rd: TemplatePoint,
    )
}
