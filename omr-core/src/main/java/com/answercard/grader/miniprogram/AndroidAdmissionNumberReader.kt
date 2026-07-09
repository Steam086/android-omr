package com.answercard.grader.miniprogram

object AndroidAdmissionNumberReader {
    private const val DIGIT_VALUES = 10
    private const val BLANK_PLACEHOLDER = '?'
    private const val ABSOLUTE_DARK_MEAN_THRESHOLD = 80.0
    private const val MIN_PEER_GRAY_CONTRAST = 18.0

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
        val solidMarkedCandidatesByDigit = mutableMapOf<Int, MutableSet<Int>>()
        var solidOnlyMarks = 0
        var bubbleOnlyMarks = 0
        var bothMarks = 0
        var candidateReadFailures = 0
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
                candidateReadFailures += 1
                debugInfo += "candidateReadFailure=digitIndex=${mapping.digitIndex},numberValue=${mapping.numberValue},reason=${readResult.failureReason}"
            }

            val bubbleReadResult = if (readResult.failureReason == null) readResult else readResult.copy(isMarked = false)
            val solidMarked = solidMarkResolver?.invoke(mapping) == true
            val bubbleMarked = bubbleReadResult.isMarked
            if (solidMarked && bubbleMarked) bothMarks += 1
            if (solidMarked && !bubbleMarked) solidOnlyMarks += 1
            if (!solidMarked && bubbleMarked) bubbleOnlyMarks += 1
            if (solidMarked) {
                solidMarkedCandidatesByDigit.getOrPut(mapping.digitIndex) { mutableSetOf() } += mapping.numberValue
            }
            val effectiveReadResult = bubbleReadResult.copy(isMarked = bubbleMarked || solidMarked)

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
                    val solidNumbers = solidMarkedCandidatesByDigit[digitIndex].orEmpty()
                    val digitResult = buildDigitResult(digitIndex, sortedCandidates, solidNumbers)
                    val preferredSolidCandidate = solidNumbers
                        .takeIf { it.isNotEmpty() }
                        ?.let { solidNumbers ->
                            sortedCandidates
                                .filter { it.numberValue in solidNumbers }
                                .minWithOrNull(
                                    compareBy<AndroidAdmissionNumberCandidate> { it.readResult.centralMeanGray }
                                        .thenByDescending { it.readResult.centralBlackCount }
                                        .thenByDescending { it.readResult.cleanedTotalBlackCount }
                                        .thenByDescending { it.readResult.totalBlackCount },
                                )
                        }
                    if (preferredSolidCandidate != null && digitResult.selectedNumber != preferredSolidCandidate.numberValue) {
                        digitResult.copy(selectedNumber = preferredSolidCandidate.numberValue)
                    } else {
                        digitResult
                    }
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
        debugInfo += "solidFusion=union"
        debugInfo += "solidOnlyMarks=$solidOnlyMarks"
        debugInfo += "bubbleOnlyMarks=$bubbleOnlyMarks"
        debugInfo += "bothMarks=$bothMarks"
        debugInfo += "candidateReadFailures=$candidateReadFailures"
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
        solidNumbers: Set<Int>,
    ): AndroidAdmissionDigitReadResult {
        val medianGray = candidates.map { it.readResult.centralMeanGray }.sorted()[candidates.size / 2]
        val marked = candidates.filter { candidate ->
            candidate.readResult.isMarked &&
                (candidate.numberValue in solidNumbers ||
                    candidate.readResult.centralMeanGray <= ABSOLUTE_DARK_MEAN_THRESHOLD ||
                    medianGray - candidate.readResult.centralMeanGray >= MIN_PEER_GRAY_CONTRAST)
        }
        val selected = marked.minWithOrNull(
            compareBy<AndroidAdmissionNumberCandidate> { it.readResult.centralMeanGray }
                .thenByDescending { it.readResult.centralBlackCount }
                .thenByDescending { it.readResult.cleanedTotalBlackCount }
                .thenByDescending { it.readResult.totalBlackCount },
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
