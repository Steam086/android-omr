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
    private const val RECT_TOLERANCE = 4f

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
        components: List<Component>,
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
        components.forEach { component ->
            val sourcePoint = invert(
                target = MiniProgramGridPoint(row = component.centerRow, column = component.centerColumn),
                anchors = anchors,
                source = source,
            ) ?: return@forEach
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

    private data class CellMatch<T>(
        val key: T,
        val centerDistance: Double,
    )

    private fun denseComponents(frame: MiniProgramFrame): List<Component> {
        val threshold = MiniProgramGeometry.threshold(frame)
        val mask = BooleanArray(frame.width * frame.height) { index ->
            frame.pixels[index] < threshold
        }
        val minArea = maxOf(80, (frame.width * frame.height * MIN_COMPONENT_AREA_RATIO).toInt())
        val minSize = maxOf(8, (minOf(frame.width, frame.height) * MIN_COMPONENT_SIZE_RATIO).toInt())
        return findComponents(mask, frame.width, frame.height)
            .filter { component -> component.area >= minArea }
            .filter { component -> component.rect.width >= minSize && component.rect.height >= minSize }
            .filter { component -> component.fillRatio >= MIN_COMPONENT_FILL_RATIO }
            .filterNot { component -> component.rect.touchesBorder(frame) }
    }

    private fun matchQuestionCell(
        cardLayout: CardLayout,
        questionIndexByNumber: Map<Int, Int>,
        sourcePoint: TemplatePoint,
    ): CellMatch<AndroidPaperQuestionCellKey>? {
        val option = cardLayout.options.firstOrNull { option ->
            TemplateGeometry.renderedRect(option.rect).contains(sourcePoint)
        } ?: return null
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

    private fun matchAdmissionNumberCell(
        cardLayout: CardLayout,
        admissionNumberDigits: Int,
        sourcePoint: TemplatePoint,
    ): CellMatch<AndroidPaperAdmissionNumberCellKey>? {
        if (cardLayout.examIdRows.isEmpty()) return null
        for (digitIndex in 0 until admissionNumberDigits) {
            for (numberValue in 0..9) {
                val rect = TemplateGeometry.renderedRect(
                    TemplateGeometry.examIdDigitBox(
                        layout = cardLayout,
                        column = digitIndex,
                        digit = numberValue,
                    ),
                )
                if (rect.contains(sourcePoint)) {
                    return CellMatch(
                        key = AndroidPaperAdmissionNumberCellKey(digitIndex, numberValue),
                        centerDistance = rect.centerDistance(sourcePoint),
                    )
                }
            }
        }
        return null
    }

    private fun Rect.contains(point: TemplatePoint): Boolean =
        point.x >= x - RECT_TOLERANCE &&
            point.x <= x + w + RECT_TOLERANCE &&
            point.y >= y - RECT_TOLERANCE &&
            point.y <= y + h + RECT_TOLERANCE

    private fun Rect.centerDistance(point: TemplatePoint): Double {
        val dx = (point.x - (x + w / 2f)).toDouble()
        val dy = (point.y - (y + h / 2f)).toDouble()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun sourceReferenceCandidates(
        cardLayout: CardLayout,
        anchors: MiniProgramAnchors,
    ): List<Pair<String, CornerAnchorReferencePoints>> {
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

    private data class ComponentRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    private fun ComponentRect.touchesBorder(frame: MiniProgramFrame): Boolean =
        left <= 0 ||
            top <= 0 ||
            right >= frame.width ||
            bottom >= frame.height

    private data class Component(
        val rect: ComponentRect,
        val area: Int,
    ) {
        val fillRatio: Float get() = area.toFloat() / (rect.width * rect.height).coerceAtLeast(1)
        val centerColumn: Double get() = (rect.left + rect.right - 1) / 2.0
        val centerRow: Double get() = (rect.top + rect.bottom - 1) / 2.0
    }

    private fun findComponents(mask: BooleanArray, width: Int, height: Int): List<Component> {
        val visited = BooleanArray(mask.size)
        val components = mutableListOf<Component>()
        val queue = IntArray(mask.size)
        for (row in 0 until height) {
            for (column in 0 until width) {
                val index = row * width + column
                if (visited[index] || !mask[index]) {
                    visited[index] = true
                    continue
                }
                var minRow = row
                var maxRow = row
                var minColumn = column
                var maxColumn = column
                var count = 0
                var head = 0
                var tail = 0
                queue[tail++] = index
                visited[index] = true
                while (head < tail) {
                    val current = queue[head++]
                    val currentRow = current / width
                    val currentColumn = current % width
                    count += 1
                    minRow = minOf(minRow, currentRow)
                    maxRow = maxOf(maxRow, currentRow)
                    minColumn = minOf(minColumn, currentColumn)
                    maxColumn = maxOf(maxColumn, currentColumn)
                    tail = enqueue(mask, visited, queue, tail, width, height, currentRow - 1, currentColumn)
                    tail = enqueue(mask, visited, queue, tail, width, height, currentRow + 1, currentColumn)
                    tail = enqueue(mask, visited, queue, tail, width, height, currentRow, currentColumn - 1)
                    tail = enqueue(mask, visited, queue, tail, width, height, currentRow, currentColumn + 1)
                }
                components += Component(
                    rect = ComponentRect(
                        left = minColumn,
                        top = minRow,
                        right = maxColumn + 1,
                        bottom = maxRow + 1,
                    ),
                    area = count,
                )
            }
        }
        return components
    }

    private fun enqueue(
        mask: BooleanArray,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int,
        width: Int,
        height: Int,
        row: Int,
        column: Int,
    ): Int {
        if (row !in 0 until height || column !in 0 until width) return tail
        val index = row * width + column
        if (visited[index]) return tail
        visited[index] = true
        if (!mask[index]) return tail
        queue[tail] = index
        return tail + 1
    }
}
