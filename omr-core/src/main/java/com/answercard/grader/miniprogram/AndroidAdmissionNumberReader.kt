package com.answercard.grader.miniprogram

object AndroidAdmissionNumberReader {
    private const val DIGIT_VALUES = 10
    private const val BLANK_PLACEHOLDER = '?'

    fun read(
        frame: MiniProgramFrame,
        grid: MiniProgramGrid,
        layout: AndroidPaperTemplateLayout,
    ): AndroidAdmissionNumberReadResult {
        return read(
            frame = frame,
            layout = layout,
            cellResolver = { mapping ->
                val validationFailure = validateMapping(mapping, grid)
                if (validationFailure != null) {
                    CellResolveResult(failureReason = validationFailure)
                } else {
                    try {
                        CellResolveResult(cell = grid.cell(mapping.row, mapping.column))
                    } catch (error: IllegalArgumentException) {
                        CellResolveResult(
                            failureReason = "admission mapping is outside grid: digitIndex=${mapping.digitIndex}, " +
                                "numberValue=${mapping.numberValue}, row=${mapping.row}, column=${mapping.column}",
                        )
                    }
                }
            },
            debugSource = "cellSource=grid",
        )
    }

    fun read(
        frame: MiniProgramFrame,
        layout: AndroidPaperTemplateLayout,
        projectedCells: AndroidPaperProjectedCells,
        solidMarks: AndroidSolidMarkOverlay? = null,
    ): AndroidAdmissionNumberReadResult {
        return read(
            frame = frame,
            layout = layout,
            cellResolver = { mapping ->
                val validationFailure = validateMapping(mapping)
                if (validationFailure != null) {
                    CellResolveResult(failureReason = validationFailure)
                } else {
                    val cell = projectedCells.admissionNumberCell(mapping)
                    if (cell == null) {
                        CellResolveResult(
                            failureReason = "admission projected cell is missing: digitIndex=${mapping.digitIndex}, " +
                                "numberValue=${mapping.numberValue}, row=${mapping.row}, column=${mapping.column}",
                        )
                    } else {
                        CellResolveResult(cell = cell)
                    }
                }
            },
            debugSource = "cellSource=templateGeometry",
            solidMarkResolver = solidMarks
                ?.takeIf { it.hasAdmissionNumberMarks }
                ?.let { marks -> { mapping -> marks.isAdmissionNumberMarked(mapping) } },
            solidMarkDebugInfo = solidMarks?.debugInfo.orEmpty(),
        )
    }

    private fun read(
        frame: MiniProgramFrame,
        layout: AndroidPaperTemplateLayout,
        cellResolver: (AndroidPaperAdmissionNumberMapping) -> CellResolveResult,
        debugSource: String,
        solidMarkResolver: (((AndroidPaperAdmissionNumberMapping) -> Boolean))? = null,
        solidMarkDebugInfo: List<String> = emptyList(),
    ): AndroidAdmissionNumberReadResult {
        val debugInfo = mutableListOf<String>()
        debugInfo += "templateType=${layout.templateType}"
        debugInfo += "admissionNumberMappings=${layout.admissionNumberMappings.size}"
        debugInfo += "edgeCleanDirections=active"
        debugInfo += debugSource
        debugInfo += solidMarkDebugInfo

        val candidates = mutableListOf<AndroidAdmissionNumberCandidate>()
        layout.admissionNumberMappings.forEach { mapping ->
            val cellResult = cellResolver(mapping)
            val cell = cellResult.cell ?: return failure(cellResult.failureReason ?: "admission cell is missing", debugInfo)
            val edgeCleanDirections = AndroidPaperEdgeCleanDirections.forAdmission(mapping, layout)
            val readResult = MiniProgramBubbleReader.read(
                frame = frame,
                cell = cell,
                edgeCleanDirections = edgeCleanDirections,
            )
            if (readResult.failureReason != null) {
                return failure(
                    "bubble read failed: digitIndex=${mapping.digitIndex}, " +
                        "numberValue=${mapping.numberValue}, reason=${readResult.failureReason}",
                    debugInfo,
                )
            }

            val effectiveReadResult = solidMarkResolver?.let { resolver ->
                readResult.copy(isMarked = resolver(mapping))
            } ?: readResult

            candidates += AndroidAdmissionNumberCandidate(
                digitIndex = mapping.digitIndex,
                numberValue = mapping.numberValue,
                row = mapping.row,
                column = mapping.column,
                readResult = effectiveReadResult,
            )
        }

        val candidatesByDigit = candidates.groupBy { it.digitIndex }
        val digitResults = (0 until layout.admissionNumberDigits)
            .map { digitIndex ->
                val digitCandidates = candidatesByDigit[digitIndex].orEmpty()
                val sortedCandidates = digitCandidates.sortedBy { it.numberValue }
                if (sortedCandidates.size != DIGIT_VALUES ||
                    sortedCandidates.map { it.numberValue } != (0 until DIGIT_VALUES).toList()
                ) {
                    AndroidAdmissionDigitReadResult(
                        digitIndex = digitIndex,
                        selectedNumber = null,
                        candidates = sortedCandidates,
                        isBlank = false,
                        isMultiMarked = false,
                        failureReason = "digit must contain 10 candidates",
                    )
                } else {
                    buildDigitResult(digitIndex, sortedCandidates)
                }
            }

        val digits = digitResults.joinToString(separator = "") { digit ->
            digit.selectedNumber?.toString() ?: BLANK_PLACEHOLDER.toString()
        }
        val failureReason = when {
            digitResults.any { it.failureReason == "digit must contain 10 candidates" } ->
                "admission number has incomplete digit candidates"
            else -> null
        }

        debugInfo += "digits=$digits"
        debugInfo += "digitResults=${digitResults.size}"
        return AndroidAdmissionNumberReadResult(
            digits = digits,
            digitResults = digitResults,
            success = failureReason == null,
            failureReason = failureReason,
            debugInfo = if (failureReason == null) debugInfo else debugInfo + "failure=$failureReason",
        )
    }

    private fun buildDigitResult(
        digitIndex: Int,
        candidates: List<AndroidAdmissionNumberCandidate>,
    ): AndroidAdmissionDigitReadResult {
        val marked = candidates.filter { it.readResult.isMarked }
        val selected = marked.maxWithOrNull(
            compareBy<AndroidAdmissionNumberCandidate> { it.readResult.centralBlackCount }
                .thenBy { it.readResult.cleanedTotalBlackCount }
                .thenBy { it.readResult.totalBlackCount },
        )

        return AndroidAdmissionDigitReadResult(
            digitIndex = digitIndex,
            selectedNumber = selected?.numberValue,
            candidates = candidates,
            isBlank = marked.isEmpty(),
            isMultiMarked = marked.size > 1,
            failureReason = if (marked.isEmpty()) "digit is blank" else null,
        )
    }

    private data class CellResolveResult(
        val cell: MiniProgramCell? = null,
        val failureReason: String? = null,
    )

    private fun validateMapping(mapping: AndroidPaperAdmissionNumberMapping, grid: MiniProgramGrid): String? {
        val validationFailure = validateMapping(mapping)
        if (validationFailure != null) return validationFailure
        if (mapping.row !in 0 until grid.rows || mapping.column !in 0 until grid.columns) {
            return "admission mapping is outside grid: digitIndex=${mapping.digitIndex}, " +
                "numberValue=${mapping.numberValue}, row=${mapping.row}, column=${mapping.column}"
        }
        return null
    }

    private fun validateMapping(mapping: AndroidPaperAdmissionNumberMapping): String? {
        if (mapping.digitIndex < 0) {
            return "digitIndex must be non-negative: ${mapping.digitIndex}"
        }
        if (mapping.numberValue !in 0 until DIGIT_VALUES) {
            return "numberValue must be in 0..9: digitIndex=${mapping.digitIndex}, numberValue=${mapping.numberValue}"
        }
        return null
    }

    private fun failure(reason: String, debugInfo: List<String>): AndroidAdmissionNumberReadResult =
        AndroidAdmissionNumberReadResult(
            digits = "",
            digitResults = emptyList(),
            success = false,
            failureReason = reason,
            debugInfo = debugInfo + "failure=$reason",
        )
}
