package com.answercard.grader.miniprogram

object AndroidAnswerAreaReader {
    private val OPTION_LABELS = listOf("A", "B", "C", "D")

    fun read(
        frame: MiniProgramFrame,
        grid: MiniProgramGrid,
        layout: AndroidPaperTemplateLayout,
    ): AndroidAnswerAreaReadResult {
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
                            failureReason = "question mapping is outside grid: questionIndex=${mapping.questionIndex}, " +
                                "optionIndex=${mapping.optionIndex}, row=${mapping.row}, column=${mapping.column}",
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
    ): AndroidAnswerAreaReadResult {
        return read(
            frame = frame,
            layout = layout,
            cellResolver = { mapping ->
                val validationFailure = validateMapping(mapping)
                if (validationFailure != null) {
                    CellResolveResult(failureReason = validationFailure)
                } else {
                    val cell = projectedCells.questionCell(mapping)
                    if (cell == null) {
                        CellResolveResult(
                            failureReason = "question projected cell is missing: questionIndex=${mapping.questionIndex}, " +
                                "optionIndex=${mapping.optionIndex}, row=${mapping.row}, column=${mapping.column}",
                        )
                    } else {
                        CellResolveResult(cell = cell)
                    }
                }
            },
            debugSource = "cellSource=templateGeometry",
        )
    }

    private fun read(
        frame: MiniProgramFrame,
        layout: AndroidPaperTemplateLayout,
        cellResolver: (AndroidPaperQuestionMapping) -> CellResolveResult,
        debugSource: String,
    ): AndroidAnswerAreaReadResult {
        val debugInfo = mutableListOf<String>()
        debugInfo += "templateType=${layout.templateType}"
        debugInfo += "questionMappings=${layout.questionMappings.size}"
        debugInfo += "edgeCleanDirections=none"
        debugInfo += debugSource

        val optionResults = mutableListOf<AndroidOptionReadResult>()
        layout.questionMappings.forEach { mapping ->
            val cellResult = cellResolver(mapping)
            val cell = cellResult.cell ?: return failure(cellResult.failureReason ?: "question cell is missing", debugInfo)
            val readResult = MiniProgramBubbleReader.read(frame = frame, cell = cell)
            if (readResult.failureReason != null) {
                return failure(
                    "bubble read failed: questionIndex=${mapping.questionIndex}, " +
                        "optionIndex=${mapping.optionIndex}, reason=${readResult.failureReason}",
                    debugInfo,
                )
            }

            optionResults += AndroidOptionReadResult(
                questionIndex = mapping.questionIndex,
                optionIndex = mapping.optionIndex,
                optionLabel = optionLabel(mapping.optionIndex),
                row = mapping.row,
                column = mapping.column,
                readResult = readResult,
            )
        }

        val questions = optionResults
            .groupBy { it.questionIndex }
            .toSortedMap()
            .map { (questionIndex, options) ->
                val marked = options.filter { it.readResult.isMarked }
                val selected = if (marked.size > 1) {
                    listOf(
                        marked.maxWithOrNull(
                            compareBy<AndroidOptionReadResult> { it.readResult.centralBlackCount }
                                .thenBy { it.readResult.cleanedTotalBlackCount }
                                .thenBy { it.readResult.totalBlackCount },
                        )!!,
                    )
                } else {
                    marked
                }
                AndroidQuestionReadResult(
                    questionIndex = questionIndex,
                    selectedOptions = selected.map { it.optionIndex },
                    selectedLabels = selected.map { it.optionLabel },
                    optionResults = options.sortedBy { it.optionIndex },
                    isBlank = selected.isEmpty(),
                    isMultiMarked = marked.size > 1,
                )
            }

        debugInfo += "questions=${questions.size}"
        return AndroidAnswerAreaReadResult(
            questions = questions,
            failureReason = null,
            debugInfo = debugInfo,
        )
    }

    private data class CellResolveResult(
        val cell: MiniProgramCell? = null,
        val failureReason: String? = null,
    )

    private fun validateMapping(mapping: AndroidPaperQuestionMapping, grid: MiniProgramGrid): String? {
        val validationFailure = validateMapping(mapping)
        if (validationFailure != null) return validationFailure
        if (mapping.row !in 0 until grid.rows || mapping.column !in 0 until grid.columns) {
            return "question mapping is outside grid: questionIndex=${mapping.questionIndex}, " +
                "optionIndex=${mapping.optionIndex}, row=${mapping.row}, column=${mapping.column}"
        }
        return null
    }

    private fun validateMapping(mapping: AndroidPaperQuestionMapping): String? {
        if (mapping.questionIndex < 0) {
            return "questionIndex must be non-negative: ${mapping.questionIndex}"
        }
        if (mapping.optionIndex !in OPTION_LABELS.indices) {
            return "optionIndex must be in 0..3: questionIndex=${mapping.questionIndex}, optionIndex=${mapping.optionIndex}"
        }
        return null
    }

    private fun optionLabel(optionIndex: Int): String = OPTION_LABELS[optionIndex]

    private fun failure(reason: String, debugInfo: List<String>): AndroidAnswerAreaReadResult =
        AndroidAnswerAreaReadResult(
            questions = emptyList(),
            failureReason = reason,
            debugInfo = debugInfo + "failure=$reason",
        )
}
