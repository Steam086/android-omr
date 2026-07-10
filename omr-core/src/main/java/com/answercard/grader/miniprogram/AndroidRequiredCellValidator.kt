package com.answercard.grader.miniprogram

enum class RequiredCellFailure {
    CLIPPED,
    TOO_SMALL,
}

data class RequiredCellValidation(
    val failure: RequiredCellFailure?,
    val metrics: List<MiniProgramCellSourceMetrics>,
    val failureReason: String?,
) {
    fun debugInfo(): List<String> = listOf(
        "requiredCellCount=${metrics.size}",
        "minRequiredCellWidth=${format(metrics.minOfOrNull { it.width })}",
        "minRequiredCellHeight=${format(metrics.minOfOrNull { it.height })}",
        "minRequiredCellArea=${format(metrics.minOfOrNull { it.area })}",
        "requiredCellFailure=${failure?.name ?: "none"}",
        "requiredCellFailureDetail=${failureReason ?: "none"}",
    )

    private fun format(value: Double?): String =
        value?.let { "%.1f".format(java.util.Locale.US, it) } ?: "none"
}

object AndroidRequiredCellValidator {
    fun validate(
        frame: MiniProgramFrame,
        cells: AndroidPaperProjectedCells,
    ): RequiredCellValidation {
        val allCells = cells.questionCells.values + cells.admissionNumberCells.values
        val inspections = allCells.map { cell ->
            val metrics = MiniProgramCellSampler.sourceMetrics(frame, cell)
            CellInspection(
                metrics = metrics,
                failureReason = MiniProgramCellSampler.validateSource(frame, cell, metrics),
            )
        }
        val clipped = inspections.firstOrNull { !it.metrics.insideFrame }
        if (clipped != null) {
            return RequiredCellValidation(
                failure = RequiredCellFailure.CLIPPED,
                metrics = inspections.map { it.metrics },
                failureReason = clipped.failureReason ?: "required cell crosses interpolation margin",
            )
        }
        val tooSmall = inspections.firstOrNull {
            !it.metrics.hasEnoughSourceInformation || it.failureReason != null
        }
        return RequiredCellValidation(
            failure = if (tooSmall == null) null else RequiredCellFailure.TOO_SMALL,
            metrics = inspections.map { it.metrics },
            failureReason = tooSmall?.failureReason,
        )
    }

    private data class CellInspection(
        val metrics: MiniProgramCellSourceMetrics,
        val failureReason: String?,
    )
}
