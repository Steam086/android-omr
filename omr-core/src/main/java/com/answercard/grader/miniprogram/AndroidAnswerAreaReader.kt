package com.answercard.grader.miniprogram

import com.answercard.grader.template.QuestionType

object AndroidAnswerAreaReader {
    private val OPTION_LABELS = listOf("A", "B", "C", "D")
    private const val SINGLE_CHOICE_KEEP_RATIO = 0.84
    private const val ABSOLUTE_DARK_MEAN_THRESHOLD = 80.0
    private const val MIN_PEER_GRAY_CONTRAST = 18.0

    fun read(
        frame: MiniProgramFrame,
        grid: MiniProgramGrid,
        layout: AndroidPaperTemplateLayout,
        optionLabelsByQuestion: List<List<String>> = emptyList(),
        questionTypesByQuestion: List<QuestionType> = emptyList(),
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
            optionLabelResolver = { mapping ->
                optionLabelsByQuestion.labelFor(mapping)
            },
            questionTypeResolver = { mapping ->
                questionTypesByQuestion.getOrNull(mapping.questionIndex) ?: QuestionType.SINGLE
            },
        )
    }

    fun read(
        frame: MiniProgramFrame,
        layout: AndroidPaperTemplateLayout,
        projectedCells: AndroidPaperProjectedCells,
        optionLabelsByQuestion: List<List<String>> = emptyList(),
        questionTypesByQuestion: List<QuestionType> = emptyList(),
        solidMarks: AndroidSolidMarkOverlay? = null,
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
            optionLabelResolver = { mapping ->
                optionLabelsByQuestion.labelFor(mapping)
            },
            questionTypeResolver = { mapping ->
                questionTypesByQuestion.getOrNull(mapping.questionIndex) ?: QuestionType.SINGLE
            },
            solidMarkResolver = solidMarks
                ?.takeIf { it.hasQuestionMarks }
                ?.let { marks -> { mapping -> marks.isQuestionMarked(mapping) } },
            solidMarkDebugInfo = solidMarks?.debugInfo.orEmpty(),
        )
    }

    private fun read(
        frame: MiniProgramFrame,
        layout: AndroidPaperTemplateLayout,
        cellResolver: (AndroidPaperQuestionMapping) -> CellResolveResult,
        debugSource: String,
        optionLabelResolver: (AndroidPaperQuestionMapping) -> String,
        questionTypeResolver: (AndroidPaperQuestionMapping) -> QuestionType,
        solidMarkResolver: (((AndroidPaperQuestionMapping) -> Boolean))? = null,
        solidMarkDebugInfo: List<String> = emptyList(),
    ): AndroidAnswerAreaReadResult {
        val debugInfo = mutableListOf<String>()
        debugInfo += "templateType=${layout.templateType}"
        debugInfo += "questionMappings=${layout.questionMappings.size}"
        debugInfo += "edgeCleanDirections=active"
        debugInfo += debugSource
        debugInfo += solidMarkDebugInfo

        val optionResults = mutableListOf<AndroidOptionReadResult>()
        var solidOnlyMarks = 0
        var bubbleOnlyMarks = 0
        var bothMarks = 0
        var optionReadFailures = 0
        val solidMarkedOptionKeys = mutableSetOf<AndroidPaperQuestionCellKey>()
        layout.questionMappings.forEach { mapping ->
            val cellResult = cellResolver(mapping)
            val cell = cellResult.cell ?: return failure(cellResult.failureReason ?: "question cell is missing", debugInfo)
            val edgeCleanDirections = AndroidPaperEdgeCleanDirections.forQuestion(mapping, layout)
            val readResult = MiniProgramBubbleReader.read(
                frame = frame,
                cell = cell,
                edgeCleanDirections = edgeCleanDirections,
            )
            if (readResult.failureReason != null) {
                optionReadFailures += 1
                debugInfo += "optionReadFailure=questionIndex=${mapping.questionIndex},optionIndex=${mapping.optionIndex},reason=${readResult.failureReason}"
            }
            val bubbleReadResult = if (readResult.failureReason == null) {
                readResult
            } else {
                readResult.copy(isMarked = false)
            }
            val solidMarked = solidMarkResolver?.invoke(mapping) == true
            if (solidMarked) {
                solidMarkedOptionKeys += AndroidPaperQuestionCellKey(mapping.questionIndex, mapping.optionIndex)
            }
            val bubbleMarked = bubbleReadResult.isMarked
            if (solidMarked && bubbleMarked) bothMarks += 1
            if (solidMarked && !bubbleMarked) solidOnlyMarks += 1
            if (!solidMarked && bubbleMarked) bubbleOnlyMarks += 1
            val effectiveReadResult = bubbleReadResult.copy(isMarked = bubbleMarked || solidMarked)

            optionResults += AndroidOptionReadResult(
                questionIndex = mapping.questionIndex,
                optionIndex = mapping.optionIndex,
                optionLabel = optionLabelResolver(mapping),
                row = mapping.row,
                column = mapping.column,
                readResult = effectiveReadResult,
            )
        }

        var ambiguousSingleChoiceCount = 0
        var lowContrastMarksRejected = 0
        val questions = optionResults
            .groupBy { it.questionIndex }
            .toSortedMap()
            .map { (questionIndex, options) ->
                val marked = options.filter { option ->
                    if (!option.readResult.isMarked) return@filter false
                    val key = AndroidPaperQuestionCellKey(option.questionIndex, option.optionIndex)
                    val credible = key in solidMarkedOptionKeys || isCredibleBubbleMark(option, options)
                    if (!credible) lowContrastMarksRejected += 1
                    credible
                }
                val questionType = questionTypeResolver(options.first().mapping())
                val selected = selectedOptionsForQuestion(questionType, marked)
                if (isAmbiguousSingleChoice(questionType, marked)) ambiguousSingleChoiceCount += 1
                AndroidQuestionReadResult(
                    questionIndex = questionIndex,
                    selectedOptions = selected.map { it.optionIndex },
                    selectedLabels = selected.map { it.optionLabel },
                    optionResults = options.sortedBy { it.optionIndex },
                    isBlank = selected.isEmpty(),
                    isMultiMarked = marked.size > 1,
                )
            }

        debugInfo += "solidFusion=union"
        debugInfo += "solidOnlyMarks=$solidOnlyMarks"
        debugInfo += "bubbleOnlyMarks=$bubbleOnlyMarks"
        debugInfo += "bothMarks=$bothMarks"
        debugInfo += "optionReadFailures=$optionReadFailures"
        debugInfo += "lowContrastMarksRejected=$lowContrastMarksRejected"
        debugInfo += "singleChoiceAmbiguous=$ambiguousSingleChoiceCount"
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

    private fun List<List<String>>.labelFor(mapping: AndroidPaperQuestionMapping): String =
        getOrNull(mapping.questionIndex)?.getOrNull(mapping.optionIndex)
            ?: optionLabel(mapping.optionIndex)

    private fun selectedOptionsForQuestion(
        questionType: QuestionType,
        marked: List<AndroidOptionReadResult>,
    ): List<AndroidOptionReadResult> {
        if (questionType == QuestionType.MULTIPLE || marked.size <= 1) {
            return marked.sortedBy { it.optionIndex }
        }
        val best = marked.minWithOrNull(
            compareBy<AndroidOptionReadResult> { it.readResult.centralMeanGray }
                .thenByDescending { it.readResult.containCount }
                .thenByDescending { it.readResult.cleanedTotalBlackCount }
                .thenByDescending { it.readResult.totalBlackCount },
        ) ?: return emptyList()
        return listOf(best)
    }

    private fun isCredibleBubbleMark(
        option: AndroidOptionReadResult,
        peers: List<AndroidOptionReadResult>,
    ): Boolean {
        val mean = option.readResult.centralMeanGray
        if (mean <= ABSOLUTE_DARK_MEAN_THRESHOLD) return true
        val peerMeans = peers.filterNot { it === option }.map { it.readResult.centralMeanGray }.sorted()
        if (peerMeans.isEmpty()) return true
        val peerMedian = peerMeans[peerMeans.size / 2]
        return peerMedian - mean >= MIN_PEER_GRAY_CONTRAST
    }

    private fun markStrength(readResult: MiniProgramBubbleReadResult): Double =
        (readResult.blackThreshold.toDouble() - readResult.centralMeanGray).coerceAtLeast(0.0)

    private fun isAmbiguousSingleChoice(
        questionType: QuestionType,
        marked: List<AndroidOptionReadResult>,
    ): Boolean {
        if (questionType != QuestionType.SINGLE || marked.size <= 1) return false
        val bestStrength = marked.maxOf { markStrength(it.readResult) }
        if (bestStrength <= 0.0) return marked.size > 1
        return marked.count { markStrength(it.readResult) >= bestStrength * SINGLE_CHOICE_KEEP_RATIO } > 1
    }

    private fun AndroidOptionReadResult.mapping(): AndroidPaperQuestionMapping =
        AndroidPaperQuestionMapping(
            questionIndex = questionIndex,
            optionIndex = optionIndex,
            row = row,
            column = column,
        )

    private fun failure(reason: String, debugInfo: List<String>): AndroidAnswerAreaReadResult =
        AndroidAnswerAreaReadResult(
            questions = emptyList(),
            failureReason = reason,
            debugInfo = debugInfo + "failure=$reason",
        )
}
