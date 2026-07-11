package com.answercard.grader.miniprogram

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ProjectedCellEdgeRefiner {
    private const val MAX_OFFSET_RATIO = 0.30
    private const val MIN_EDGE_CONTRAST = 24.0
    private const val MIN_WINNER_GAP = 6.0
    private const val MIN_VALID_SAMPLE_RATIO = 0.70
    private const val MIN_GROUP_SPAN_RATIO = 0.45
    private const val MIN_REFINED_WIDTH_RATIO = 0.70
    private const val MAX_REFINED_WIDTH_RATIO = 1.30
    private const val EDGE_SAMPLE_COUNT = 11
    private const val EDGE_SAMPLE_START = 0.10
    private const val EDGE_SAMPLE_END = 0.90
    private const val MIN_OBSERVATIONS = 3
    private const val MIN_SOURCE_CELLS = 2
    private const val MIN_INTERPOLATION_MARGIN = 1.0

    fun refine(
        frame: MiniProgramFrame,
        cells: AndroidPaperProjectedCells,
    ): AndroidPaperProjectedCells {
        val questionCells = cells.questionCells.toMutableMap()
        val admissionCells = cells.admissionNumberCells.toMutableMap()
        var refinedQuestionGroups = 0
        var refinedAdmissionGroups = 0
        var fallbackGroups = 0
        var unsafeGroups = 0
        var acceptedObservations = 0
        var maxOffset = 0.0
        val evaluatedGroups = cells.questionCells.keys.map { it.questionIndex }.distinct().size +
            cells.admissionNumberCells.keys.map { it.digitIndex }.distinct().size

        cells.questionCells.entries
            .groupBy { it.key.questionIndex }
            .values
            .forEach { unsortedEntries ->
                val entries = unsortedEntries.sortedBy { it.key.optionIndex }
                val result = refineGroup(frame, entries.map { it.value })
                val refined = result.cells
                if (refined == null) {
                    fallbackGroups += 1
                    if (result.unsafe) unsafeGroups += 1
                } else {
                    entries.zip(refined).forEach { (entry, cell) -> questionCells[entry.key] = cell }
                    refinedQuestionGroups += 1
                    acceptedObservations += result.acceptedObservations
                    maxOffset = maxOf(maxOffset, result.maxOffset)
                }
            }

        cells.admissionNumberCells.entries
            .groupBy { it.key.digitIndex }
            .values
            .forEach { unsortedEntries ->
                val entries = unsortedEntries.sortedBy { it.key.numberValue }
                val result = refineGroup(frame, entries.map { it.value })
                val refined = result.cells
                if (refined == null) {
                    fallbackGroups += 1
                    if (result.unsafe) unsafeGroups += 1
                } else {
                    entries.zip(refined).forEach { (entry, cell) -> admissionCells[entry.key] = cell }
                    refinedAdmissionGroups += 1
                    acceptedObservations += result.acceptedObservations
                    maxOffset = maxOf(maxOffset, result.maxOffset)
                }
            }

        val active = refinedQuestionGroups + refinedAdmissionGroups > 0
        return AndroidPaperProjectedCells(
            questionCells = questionCells,
            admissionNumberCells = admissionCells,
            edgeRefinementUnsafeGroups = cells.edgeRefinementUnsafeGroups + unsafeGroups,
            edgeRefinementEvaluatedGroups = cells.edgeRefinementEvaluatedGroups + evaluatedGroups,
            debugInfo = cells.debugInfo + listOf(
                "edgeRefinement=${if (active) "active" else "fallback"}",
                "edgeRefinementQuestionGroups=$refinedQuestionGroups",
                "edgeRefinementAdmissionGroups=$refinedAdmissionGroups",
                "edgeRefinementFallbackGroups=$fallbackGroups",
                "edgeRefinementUnsafeGroups=${cells.edgeRefinementUnsafeGroups + unsafeGroups}",
                "edgeRefinementEvaluatedGroups=${cells.edgeRefinementEvaluatedGroups + evaluatedGroups}",
                "edgeRefinementObservations=$acceptedObservations",
                "edgeRefinementMaxOffset=${format(maxOffset)}",
            ),
        )
    }

    private fun refineGroup(frame: MiniProgramFrame, cells: List<MiniProgramCell>): GroupRefinement {
        if (cells.size < MIN_SOURCE_CELLS) return GroupRefinement.rejected()
        val medianWidth = median(cells.map(::averageWidth))
        if (!medianWidth.isFinite() || medianWidth <= 0.0) return GroupRefinement.rejected()

        val observations = cells.flatMapIndexed { cellIndex, cell ->
            buildList {
                observeEdge(
                    frame = frame,
                    start = cell.leftTop,
                    end = cell.leftBottom,
                    outsideDirection = -1,
                    sourceCell = cellIndex,
                    cellWidth = medianWidth,
                )?.let(::add)
                observeEdge(
                    frame = frame,
                    start = cell.rightTop,
                    end = cell.rightBottom,
                    outsideDirection = 1,
                    sourceCell = cellIndex,
                    cellWidth = medianWidth,
                )?.let(::add)
            }
        }
        if (observations.size < MIN_OBSERVATIONS) return GroupRefinement.rejected()
        if (observations.map { it.sourceCell }.distinct().size < MIN_SOURCE_CELLS) return GroupRefinement.rejected()

        val filtered = rejectOffsetOutliers(observations)
        if (hasStrongEvidenceWithoutConsistentPair(filtered, medianWidth)) {
            return GroupRefinement.rejected(unsafe = true)
        }
        if (filtered.size < MIN_OBSERVATIONS) return GroupRefinement.rejected()
        if (filtered.map { it.sourceCell }.distinct().size < MIN_SOURCE_CELLS) return GroupRefinement.rejected()

        val groupLeft = cells.minOf { minOf(it.leftTop.column, it.leftBottom.column) }
        val groupRight = cells.maxOf { maxOf(it.rightTop.column, it.rightBottom.column) }
        val groupSpan = groupRight - groupLeft
        if (!groupSpan.isFinite() || groupSpan <= 0.0) return GroupRefinement.rejected()
        val observationSpan = filtered.maxOf { it.expectedColumn } - filtered.minOf { it.expectedColumn }
        if (observationSpan / groupSpan < MIN_GROUP_SPAN_RATIO) return GroupRefinement.rejected()

        val model = fit(filtered) ?: return GroupRefinement.rejected()
        val rmse = sqrt(filtered.sumOf { observation ->
            val residual = observation.offset - model.offsetAt(observation.expectedColumn)
            residual * residual
        } / filtered.size.toDouble())
        if (rmse > maxOf(1.5, medianWidth * 0.12)) return GroupRefinement.rejected()

        val endpointOffsets = listOf(model.offsetAt(groupLeft), model.offsetAt(groupRight))
        if (endpointOffsets.any { !it.isFinite() || abs(it) > medianWidth * MAX_OFFSET_RATIO }) {
            return GroupRefinement.rejected()
        }
        val refined = cells.map { refineCell(it, model) }
        if (refined.zip(cells).any { (candidate, original) -> !isSafe(frame, original, candidate) }) {
            return GroupRefinement.rejected()
        }
        return GroupRefinement(
            cells = refined,
            acceptedObservations = filtered.size,
            maxOffset = endpointOffsets.maxOf(::abs),
            unsafe = false,
        )
    }

    private fun observeEdge(
        frame: MiniProgramFrame,
        start: MiniProgramGridPoint,
        end: MiniProgramGridPoint,
        outsideDirection: Int,
        sourceCell: Int,
        cellWidth: Double,
    ): EdgeObservation? {
        if (!start.row.isFinite() || !start.column.isFinite() || !end.row.isFinite() || !end.column.isFinite()) {
            return null
        }
        val maxOffset = floor(cellWidth * MAX_OFFSET_RATIO).toInt().coerceAtLeast(1)
        val outsideDistance = maxOf(2, (cellWidth * 0.10).roundToInt())
        val candidates = (-maxOffset..maxOffset).mapNotNull { offset ->
            scoreCandidate(frame, start, end, offset, outsideDirection, outsideDistance)
        }.sortedByDescending { it.contrast }
        val best = candidates.firstOrNull() ?: return null
        val runnerUp = candidates.firstOrNull { abs(it.offset - best.offset) >= 2 }
        val winnerGap = best.contrast - (runnerUp?.contrast ?: 0.0)
        if (best.validSamples.toDouble() / EDGE_SAMPLE_COUNT < MIN_VALID_SAMPLE_RATIO) return null
        if (best.contrast < MIN_EDGE_CONTRAST || winnerGap < MIN_WINNER_GAP) return null
        return EdgeObservation(
            expectedColumn = (start.column + end.column) / 2.0,
            offset = best.offset.toDouble(),
            sourceCell = sourceCell,
        )
    }

    private fun scoreCandidate(
        frame: MiniProgramFrame,
        start: MiniProgramGridPoint,
        end: MiniProgramGridPoint,
        offset: Int,
        outsideDirection: Int,
        outsideDistance: Int,
    ): EdgeCandidate? {
        var lineSum = 0L
        var outsideSum = 0L
        var count = 0
        for (sample in 0 until EDGE_SAMPLE_COUNT) {
            val ratio = if (EDGE_SAMPLE_COUNT == 1) {
                0.5
            } else {
                EDGE_SAMPLE_START +
                    (EDGE_SAMPLE_END - EDGE_SAMPLE_START) * sample.toDouble() / (EDGE_SAMPLE_COUNT - 1).toDouble()
            }
            val row = (start.row + (end.row - start.row) * ratio).roundToInt()
            val column = (start.column + (end.column - start.column) * ratio).roundToInt() + offset
            val outsideColumn = column + outsideDirection * outsideDistance
            if (row !in 0 until frame.height || column !in 0 until frame.width || outsideColumn !in 0 until frame.width) {
                continue
            }
            lineSum += frame[row, column]
            outsideSum += frame[row, outsideColumn]
            count += 1
        }
        if (count == 0) return null
        return EdgeCandidate(
            offset = offset,
            contrast = outsideSum.toDouble() / count - lineSum.toDouble() / count,
            validSamples = count,
        )
    }

    private fun rejectOffsetOutliers(observations: List<EdgeObservation>): List<EdgeObservation> {
        val offsetMedian = median(observations.map { it.offset })
        val mad = median(observations.map { abs(it.offset - offsetMedian) })
        val limit = maxOf(2.0, mad * 2.5)
        return observations.filter { abs(it.offset - offsetMedian) <= limit }
    }

    private fun hasStrongEvidenceWithoutConsistentPair(
        observations: List<EdgeObservation>,
        medianCellWidth: Double,
    ): Boolean {
        if (observations.size < MIN_OBSERVATIONS) return false
        val sourceCells = observations.map { it.sourceCell }.toSet()
        if (sourceCells.size < MIN_SOURCE_CELLS) return false
        val maximumPairDifference = maxOf(2.0, medianCellWidth * 0.12)
        val hasConsistentPair = observations
            .groupBy { it.sourceCell }
            .values
            .any { sameCell ->
                sameCell.size >= 2 &&
                    sameCell.maxOf { it.offset } - sameCell.minOf { it.offset } <= maximumPairDifference
            }
        return !hasConsistentPair
    }

    private fun fit(observations: List<EdgeObservation>): ResidualModel? {
        val meanColumn = observations.sumOf { it.expectedColumn } / observations.size.toDouble()
        val meanOffset = observations.sumOf { it.offset } / observations.size.toDouble()
        var numerator = 0.0
        var denominator = 0.0
        observations.forEach { observation ->
            val centeredColumn = observation.expectedColumn - meanColumn
            numerator += centeredColumn * (observation.offset - meanOffset)
            denominator += centeredColumn * centeredColumn
        }
        if (denominator <= 1e-9) return null
        val slope = numerator / denominator
        val intercept = meanOffset - slope * meanColumn
        if (!slope.isFinite() || !intercept.isFinite()) return null
        return ResidualModel(intercept, slope)
    }

    private fun refineCell(cell: MiniProgramCell, model: ResidualModel): MiniProgramCell =
        cell.copy(
            leftTop = refinePoint(cell.leftTop, model),
            rightTop = refinePoint(cell.rightTop, model),
            leftBottom = refinePoint(cell.leftBottom, model),
            rightBottom = refinePoint(cell.rightBottom, model),
        )

    private fun refinePoint(point: MiniProgramGridPoint, model: ResidualModel): MiniProgramGridPoint =
        point.copy(column = point.column + model.offsetAt(point.column))

    private fun isSafe(frame: MiniProgramFrame, original: MiniProgramCell, refined: MiniProgramCell): Boolean {
        val points = listOf(refined.leftTop, refined.rightTop, refined.leftBottom, refined.rightBottom)
        if (points.any { !it.row.isFinite() || !it.column.isFinite() }) return false
        if (points.any {
                it.row < MIN_INTERPOLATION_MARGIN ||
                    it.column < MIN_INTERPOLATION_MARGIN ||
                    it.row > frame.height - 1.0 - MIN_INTERPOLATION_MARGIN ||
                    it.column > frame.width - 1.0 - MIN_INTERPOLATION_MARGIN
            }
        ) {
            return false
        }
        if (refined.rightTop.column <= refined.leftTop.column || refined.rightBottom.column <= refined.leftBottom.column) {
            return false
        }
        if (refined.leftBottom.row <= refined.leftTop.row || refined.rightBottom.row <= refined.rightTop.row) return false
        val topWidthRatio = distance(refined.leftTop, refined.rightTop) / distance(original.leftTop, original.rightTop)
        val bottomWidthRatio = distance(refined.leftBottom, refined.rightBottom) / distance(original.leftBottom, original.rightBottom)
        return topWidthRatio in MIN_REFINED_WIDTH_RATIO..MAX_REFINED_WIDTH_RATIO &&
            bottomWidthRatio in MIN_REFINED_WIDTH_RATIO..MAX_REFINED_WIDTH_RATIO
    }

    private fun averageWidth(cell: MiniProgramCell): Double =
        (distance(cell.leftTop, cell.rightTop) + distance(cell.leftBottom, cell.rightBottom)) / 2.0

    private fun distance(first: MiniProgramGridPoint, second: MiniProgramGridPoint): Double =
        hypot(first.row - second.row, first.column - second.column)

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private fun format(value: Double): String = "%.1f".format(Locale.US, value)

    private data class EdgeCandidate(
        val offset: Int,
        val contrast: Double,
        val validSamples: Int,
    )

    private data class EdgeObservation(
        val expectedColumn: Double,
        val offset: Double,
        val sourceCell: Int,
    )

    private data class ResidualModel(
        val intercept: Double,
        val slope: Double,
    ) {
        fun offsetAt(column: Double): Double = intercept + slope * column
    }

    private data class GroupRefinement(
        val cells: List<MiniProgramCell>?,
        val acceptedObservations: Int,
        val maxOffset: Double,
        val unsafe: Boolean,
    ) {
        companion object {
            fun rejected(unsafe: Boolean = false) = GroupRefinement(null, 0, 0.0, unsafe)
        }
    }
}
