package com.answercard.grader.miniprogram

import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

object AndroidOmrEngine {
    private const val MIN_CARD_WIDTH = 160.0
    private const val MIN_CARD_HEIGHT = 120.0
    private const val MIN_CARD_AREA = 18_000.0
    private const val MAX_ASPECT_RELATIVE_DEVIATION = 0.55
    private const val MIN_ANCHOR_BORDER_INSET = 4
    private const val MIN_CARD_INTERIOR_BRIGHTNESS = 65.0
    private const val MIN_CARD_INTERIOR_ANCHOR_CONTRAST = 20.0

    fun scan(
        frame: MiniProgramFrame,
        template: TemplateState,
        anchorMode: AnchorMode = AnchorMode.CODED_ONLY,
    ): AndroidOmrResult {
        val layoutResult = buildLayout(template)
        val layout = layoutResult.layout ?: return layoutResult.toResult()
        val debugInfo = mutableListOf<String>()
        debugInfo += layoutResult.debugInfo
        debugInfo += "anchorMode=${anchorMode.name}"

        val anchorDecision = locateAnchors(frame, template, anchorMode)
        val cornerDebugInfo = anchorDecision.debugInfo
        if (anchorDecision.normalizationQuarterTurns > 0) {
            val normalized = scan(
                frame = rotateCounterClockwise(frame, anchorDecision.normalizationQuarterTurns),
                template = template,
                anchorMode = anchorMode,
            )
            return normalized.copy(
                debugInfo = debugInfo + cornerDebugInfo +
                    "codedFrameNormalizedQuarterTurns=${anchorDecision.normalizationQuarterTurns}" +
                    normalized.debugInfo,
            )
        }
        val anchors = anchorDecision.anchors ?: return AndroidOmrResult.rejected(
            reason = anchorDecision.rejectionReason ?: ScanRejectionReason.RETAKE_CODED_MARKERS,
            message = anchorDecision.failureReason ?: "corner anchors not found",
            layout = layout,
            debugInfo = debugInfo + cornerDebugInfo + "failureStage=corner",
        )

        val grid = try {
            MiniProgramGridBuilder.build(
                lu = anchors.lu.point,
                ld = anchors.ld.point,
                ru = anchors.ru.point,
                rd = anchors.rd.point,
                rows = layout.gridRows,
                columns = layout.gridColumns,
            )
        } catch (error: IllegalArgumentException) {
            val reason = "grid failed: ${error.message}"
            return AndroidOmrResult.rejected(
                reason = ScanRejectionReason.RETAKE_CARD_GEOMETRY,
                message = reason,
                layout = layout,
                anchors = anchors,
                debugInfo = debugInfo + cornerDebugInfo + "failureStage=grid" + reason,
            )
        }
        val cardGeometry = CardGeometry.from(template = template, anchors = anchors, frame = frame)
        val requireAnchorBorderInset = CodedCardFramePolicy.requiresAnchorBorderInset(
            anchorMode = anchorMode,
            inferredCodedMarkerCount = anchorDecision.inferredCodedMarkerCount,
        )
        val geometryDebugInfo = cardGeometry.debugInfo(requireAnchorBorderInset)
        val geometryFailure = cardGeometry.failureReason(requireAnchorBorderInset)
        if (geometryFailure != null) {
            val reason = if (geometryFailure == "anchors touch frame border") {
                "invalid card geometry: anchors touch frame border"
            } else {
                "invalid card geometry: possible false anchors"
            }
            return AndroidOmrResult.rejected(
                reason = ScanRejectionReason.RETAKE_CARD_GEOMETRY,
                message = reason,
                layout = layout,
                anchors = anchors,
                grid = grid,
                debugInfo = debugInfo + cornerDebugInfo + "grid=${layout.gridRows}x${layout.gridColumns}" +
                    geometryDebugInfo + "failureStage=geometry validation" + "failureDetail=$geometryFailure" +
                    "geometryRejectionReason=$geometryFailure",
            )
        }
        val cardQuality = CARD_QUALITY_EVALUATOR.evaluate(frame, anchors)
        val cardQualityDebugInfo = cardQuality.debugInfo("card")
        if (!cardQuality.accepted) {
            val rejection = cardQuality.rejectionReason ?: ScanRejectionReason.RETAKE_BLUR
            return AndroidOmrResult.rejected(
                reason = rejection,
                message = qualityFailureMessage(rejection),
                layout = layout,
                anchors = anchors,
                grid = grid,
                debugInfo = debugInfo + cornerDebugInfo + geometryDebugInfo +
                    cardQualityDebugInfo + "failureStage=card quality",
            )
        }
        val projectedCells = AndroidPaperProjectedCellBuilder.build(
            template = template,
            layout = layout,
            anchors = anchors,
        )
        val solidMarks = AndroidSolidMarkDetector.detect(
            frame = frame,
            template = template,
            anchors = anchors,
        )
        if (solidMarks.isReferenceAmbiguous) {
            return AndroidOmrResult.rejected(
                reason = ScanRejectionReason.LEGACY_ANCHOR_AMBIGUOUS,
                message = "legacy anchor reference is ambiguous",
                layout = layout,
                anchors = anchors,
                grid = grid,
                debugInfo = debugInfo + cornerDebugInfo + geometryDebugInfo + cardQualityDebugInfo +
                    solidMarks.debugInfo + "failureStage=legacy reference ambiguity",
            )
        }
        val cellValidation = AndroidRequiredCellValidator.validate(frame, projectedCells)
        val cellValidationDebugInfo = cellValidation.debugInfo()
        if (cellValidation.failure != null) {
            val clipped = cellValidation.failure == RequiredCellFailure.CLIPPED
            val reason = if (clipped) {
                "required cells are clipped by analysis frame: ${cellValidation.failureReason}"
            } else {
                "projected cell too small: ${cellValidation.failureReason}"
            }
            return AndroidOmrResult.rejected(
                reason = if (clipped) {
                    ScanRejectionReason.RETAKE_CARD_CLIPPED
                } else {
                    ScanRejectionReason.RETAKE_CELL_SIZE
                },
                message = reason,
                layout = layout,
                anchors = anchors,
                grid = grid,
                debugInfo = debugInfo + cornerDebugInfo + "grid=${layout.gridRows}x${layout.gridColumns}" +
                    geometryDebugInfo + cardQualityDebugInfo + projectedCells.debugInfo + cellValidationDebugInfo +
                    "failureStage=cell size validation" + reason,
            )
        }

        return scanWithLayoutAndGrid(
            frame = frame,
            template = template,
            layout = layout,
            anchors = anchors,
            grid = grid,
            projectedCells = projectedCells,
            solidMarks = solidMarks,
            debugInfo = debugInfo + cornerDebugInfo + "grid=${layout.gridRows}x${layout.gridColumns}" +
                geometryDebugInfo + cardQualityDebugInfo + projectedCells.debugInfo +
                solidMarks.debugInfo + cellValidationDebugInfo,
        )
    }

    fun scanWithPrecomputedGridForTest(
        frame: MiniProgramFrame,
        template: TemplateState,
        grid: MiniProgramGrid,
    ): AndroidOmrResult {
        val layoutResult = buildLayout(template)
        val layout = layoutResult.layout ?: return layoutResult.toResult()
        return scanWithLayoutAndGrid(
            frame = frame,
            template = template,
            layout = layout,
            anchors = null,
            grid = grid,
            projectedCells = null,
            debugInfo = layoutResult.debugInfo + "grid=precomputed",
        )
    }

    private fun scanWithLayoutAndGrid(
        frame: MiniProgramFrame,
        template: TemplateState,
        layout: AndroidPaperTemplateLayout,
        anchors: MiniProgramAnchors?,
        grid: MiniProgramGrid,
        projectedCells: AndroidPaperProjectedCells?,
        solidMarks: AndroidSolidMarkOverlay? = null,
        debugInfo: List<String>,
    ): AndroidOmrResult {
        val optionLabelsByQuestion = template.questions.map { it.options }
        val questionTypesByQuestion = template.questions.map { it.type }
        val answerArea = if (projectedCells == null) {
            AndroidAnswerAreaReader.read(
                frame = frame,
                grid = grid,
                layout = layout,
                optionLabelsByQuestion = optionLabelsByQuestion,
                questionTypesByQuestion = questionTypesByQuestion,
            )
        } else {
            AndroidAnswerAreaReader.read(
                frame = frame,
                layout = layout,
                projectedCells = projectedCells,
                optionLabelsByQuestion = optionLabelsByQuestion,
                questionTypesByQuestion = questionTypesByQuestion,
                solidMarks = solidMarks,
            )
        }
        if (answerArea.failureReason != null) {
            val reason = "answer area failed: ${answerArea.failureReason}"
            return AndroidOmrResult(
                success = false,
                failureReason = reason,
                layout = layout,
                anchors = anchors,
                grid = grid,
                answerArea = answerArea,
                admissionNumber = null,
                score = null,
                warnings = emptyList(),
                debugInfo = debugInfo + "failureStage=answer" + reason,
                rejectionReason = ScanRejectionReason.RETAKE_READ,
            )
        }

        val score = AndroidOmrScoreCalculator.score(template = template, answerArea = answerArea)
        val admissionNumber = if (projectedCells == null) {
            AndroidAdmissionNumberReader.read(frame = frame, grid = grid, layout = layout)
        } else {
            AndroidAdmissionNumberReader.read(
                frame = frame,
                layout = layout,
                projectedCells = projectedCells,
                solidMarks = solidMarks,
            )
        }
        val warnings = score.warnings.toMutableList()
        if (admissionNumber.failureReason != null) {
            warnings += "admission number: ${admissionNumber.failureReason}"
        }
        if (admissionNumber.digitResults.any { it.isBlank }) {
            warnings += "admission number contains blank digit"
        }
        val failureReason = when {
            admissionNumber.failureReason != null -> "admission number failed: ${admissionNumber.failureReason}"
            score.warnings.isNotEmpty() -> "score warnings: ${score.warnings.joinToString()}"
            else -> null
        }

        return AndroidOmrResult(
            success = failureReason == null,
            failureReason = failureReason,
            layout = layout,
            anchors = anchors,
            grid = grid,
            answerArea = answerArea,
            admissionNumber = admissionNumber,
            score = score.takeIf { failureReason == null },
            warnings = warnings,
            debugInfo = if (failureReason == null) {
                debugInfo + "scan success"
            } else {
                debugInfo + failureStage(failureReason) + failureReason
            },
            rejectionReason = if (failureReason == null) null else ScanRejectionReason.RETAKE_READ,
        )
    }

    private fun locateAnchors(
        frame: MiniProgramFrame,
        template: TemplateState,
        anchorMode: AnchorMode,
    ): AnchorLocationDecision =
        when (anchorMode) {
            AnchorMode.CODED_ONLY -> {
                val match = CodedCornerMarkerDetector.findAnchorsWithDiagnostics(
                    frame,
                    TemplateGeometry.buildLayout(template),
                )
                val debugInfo = match.diagnostics.debugInfo() + if (match.anchors == null) {
                    "anchorPath=coded-marker-rejected"
                } else {
                    "anchorPath=coded-marker"
                }
                if (match.anchors == null) {
                    AnchorLocationDecision(
                        anchors = null,
                        rejectionReason = ScanRejectionReason.RETAKE_CODED_MARKERS,
                        failureReason = "coded markers not reliable",
                        debugInfo = debugInfo,
                        inferredCodedMarkerCount = match.diagnostics.inferredIds.size,
                    )
                } else {
                    val rotation = match.diagnostics.rotations.values.distinct().singleOrNull() ?: 0
                    AnchorLocationDecision(
                        anchors = match.anchors,
                        rejectionReason = null,
                        failureReason = null,
                        debugInfo = debugInfo,
                        normalizationQuarterTurns = rotation,
                        inferredCodedMarkerCount = match.diagnostics.inferredIds.size,
                    )
                }
            }
            AnchorMode.LEGACY -> locateLegacyAnchors(frame, template)
        }

    private fun locateLegacyAnchors(
        frame: MiniProgramFrame,
        template: TemplateState,
    ): AnchorLocationDecision {
        val solid = SolidCornerMarkerDetector.findAnchorsWithDiagnostics(
            frame = frame,
            expectedAspectRatio = expectedMarkerCenterAspectRatio(template),
        )
        val solidDebugInfo = solid.diagnostics.debugInfo()
        if (solid.diagnostics.ambiguous) {
            return AnchorLocationDecision(
                anchors = null,
                rejectionReason = ScanRejectionReason.LEGACY_ANCHOR_AMBIGUOUS,
                failureReason = "legacy solid marker candidates are ambiguous",
                debugInfo = solidDebugInfo + "anchorPath=solid-marker-ambiguous",
            )
        }
        solid.anchors?.let {
            return AnchorLocationDecision(it, null, null, solidDebugInfo + "anchorPath=solid-marker")
        }

        val bracket = CornerAnchorMatcher.findAnchorsWithDiagnostics(
            frame = frame,
            expectedAspectRatio = expectedRenderedAspectRatio(template),
        )
        val debugInfo = solidDebugInfo + bracket.diagnostics.debugInfo()
        if (bracket.diagnostics.ambiguous) {
            return AnchorLocationDecision(
                anchors = null,
                rejectionReason = ScanRejectionReason.LEGACY_ANCHOR_AMBIGUOUS,
                failureReason = "legacy L bracket candidates are ambiguous",
                debugInfo = debugInfo + "anchorPath=l-bracket-ambiguous",
            )
        }
        val anchors = bracket.anchors ?: return AnchorLocationDecision(
            anchors = null,
            rejectionReason = ScanRejectionReason.RETAKE_LEGACY_MARKERS,
            failureReason = "legacy corner anchors not found",
            debugInfo = debugInfo + "anchorPath=l-bracket-rejected",
        )
        return AnchorLocationDecision(anchors, null, null, debugInfo + "anchorPath=l-bracket")
    }

    private fun qualityFailureMessage(reason: ScanRejectionReason): String =
        when (reason) {
            ScanRejectionReason.RETAKE_EXPOSURE -> "card exposure is outside the safe range"
            else -> "card image is too blurry"
        }

    private fun buildLayout(template: TemplateState): LayoutBuildResult =
        try {
            LayoutBuildResult(
                layout = AndroidPaperTemplateBuilder.build(
                    questionOptionCounts = template.questions.map { it.optionCount },
                    admissionNumberDigits = if (template.showHeader) template.examIdDigits else 0,
                ),
                failureReason = null,
                debugInfo = listOf("layout built"),
            )
        } catch (error: IllegalArgumentException) {
            LayoutBuildResult(
                layout = null,
                failureReason = "layout failed: ${error.message}",
                debugInfo = listOf("layout failed: ${error.message}"),
            )
        }

    private fun expectedRenderedAspectRatio(template: TemplateState): Double {
        val cardLayout = TemplateGeometry.buildLayout(template)
        return TemplateGeometry.renderedWidth(cardLayout).toDouble() /
            TemplateGeometry.renderedHeight(cardLayout).toDouble()
    }

    private fun expectedMarkerCenterAspectRatio(template: TemplateState): Double {
        val centers = TemplateGeometry.cornerMarkerCenters(TemplateGeometry.buildLayout(template))
        return (centers.ru.x - centers.lu.x).toDouble() /
            (centers.ld.y - centers.lu.y).toDouble().coerceAtLeast(1.0)
    }

    private data class LayoutBuildResult(
        val layout: AndroidPaperTemplateLayout?,
        val failureReason: String?,
        val debugInfo: List<String>,
    ) {
        fun toResult(): AndroidOmrResult =
            AndroidOmrResult(
                success = false,
                failureReason = failureReason,
                layout = layout,
                anchors = null,
                grid = null,
                answerArea = null,
                admissionNumber = null,
                score = null,
                warnings = emptyList(),
                debugInfo = debugInfo,
                rejectionReason = ScanRejectionReason.INVALID_TEMPLATE,
            )
    }

    private data class AnchorLocationDecision(
        val anchors: MiniProgramAnchors?,
        val rejectionReason: ScanRejectionReason?,
        val failureReason: String?,
        val debugInfo: List<String>,
        val normalizationQuarterTurns: Int = 0,
        val inferredCodedMarkerCount: Int = 0,
    )

    private val CARD_QUALITY_EVALUATOR = FrameQualityEvaluator(FrameQualityThresholds.CARD_ROI)

    private fun rotateCounterClockwise(frame: MiniProgramFrame, quarterTurns: Int): MiniProgramFrame {
        var rotated = frame
        repeat(quarterTurns.mod(4)) {
            val pixels = IntArray(rotated.width * rotated.height)
            val newWidth = rotated.height
            val newHeight = rotated.width
            for (row in 0 until rotated.height) {
                for (column in 0 until rotated.width) {
                    val newRow = rotated.width - 1 - column
                    val newColumn = row
                    pixels[newRow * newWidth + newColumn] = rotated[row, column]
                }
            }
            rotated = MiniProgramFrame(newWidth, newHeight, pixels)
        }
        return rotated
    }

    private data class CardGeometry(
        val width: Int,
        val height: Int,
        val area: Int,
        val aspectRatio: Double,
        val expectedTemplateRatio: Double,
        val frameWidth: Int,
        val frameHeight: Int,
        val lu: MiniProgramPoint,
        val ru: MiniProgramPoint,
        val ld: MiniProgramPoint,
        val rd: MiniProgramPoint,
        val cardInteriorBrightness: Double,
        val cornerAnchorBrightness: Double,
    ) {
        private val cardInteriorContrast: Double get() = cardInteriorBrightness - cornerAnchorBrightness
        private val cardInteriorValidationPassed: Boolean get() =
            cardInteriorBrightness >= MIN_CARD_INTERIOR_BRIGHTNESS &&
                cardInteriorContrast >= MIN_CARD_INTERIOR_ANCHOR_CONTRAST

        fun failureReason(requireAnchorBorderInset: Boolean = true): String? {
            if (
                requireAnchorBorderInset &&
                listOf(lu, ru, ld, rd).any { it.touchesFrameBorder(frameWidth, frameHeight) }
            ) {
                return "anchors touch frame border"
            }
            if (width < MIN_CARD_WIDTH || height < MIN_CARD_HEIGHT || area < MIN_CARD_AREA) {
                return "card quad too small: width=$width, height=$height, area=$area"
            }
            if (lu.column >= ru.column || ld.column >= rd.column || lu.row >= ld.row || ru.row >= rd.row) {
                return "anchor order is invalid"
            }
            val relativeDeviation = abs(aspectRatio - expectedTemplateRatio) / expectedTemplateRatio
            if (relativeDeviation > MAX_ASPECT_RELATIVE_DEVIATION) {
                return "card aspect ratio out of range: actual=$aspectRatio, expected=$expectedTemplateRatio"
            }
            if (!cardInteriorValidationPassed) {
                return "card interior too dark or low contrast"
            }
            return null
        }

        fun debugInfo(requireAnchorBorderInset: Boolean = true): List<String> =
            listOf(
                "anchors=found",
                "anchorLU=${lu.column},${lu.row}",
                "anchorRU=${ru.column},${ru.row}",
                "anchorLD=${ld.column},${ld.row}",
                "anchorRD=${rd.column},${rd.row}",
                "anchorQuadWidth=$width",
                "anchorQuadHeight=$height",
                "anchorQuadArea=$area",
                "anchorAspectRatio=${format(aspectRatio)}",
                "expectedTemplateRatio=${format(expectedTemplateRatio)}",
                "anchorBorderInset=$MIN_ANCHOR_BORDER_INSET",
                "anchorBorderInsetRequired=$requireAnchorBorderInset",
                "cardInteriorBrightness=${format(cardInteriorBrightness)}",
                "cornerAnchorBrightness=${format(cornerAnchorBrightness)}",
                "cardInteriorContrast=${format(cardInteriorContrast)}",
                "cardInteriorValidationPassed=$cardInteriorValidationPassed",
            )

        companion object {
            fun from(template: TemplateState, anchors: MiniProgramAnchors, frame: MiniProgramFrame): CardGeometry {
                val cardLayout = TemplateGeometry.buildLayout(template)
                val lu = anchors.lu.point
                val ru = anchors.ru.point
                val ld = anchors.ld.point
                val rd = anchors.rd.point
                val width = averageDistance(lu, ru, ld, rd).roundToInt()
                val height = averageDistance(lu, ld, ru, rd).roundToInt()
                val area = quadArea(lu, ru, rd, ld).roundToInt()
                return CardGeometry(
                    width = width,
                    height = height,
                    area = area,
                    aspectRatio = width.toDouble() / height.coerceAtLeast(1).toDouble(),
                    expectedTemplateRatio = TemplateGeometry.renderedWidth(cardLayout).toDouble() /
                        TemplateGeometry.renderedHeight(cardLayout).toDouble(),
                    frameWidth = frame.width,
                    frameHeight = frame.height,
                    lu = lu,
                    ru = ru,
                    ld = ld,
                    rd = rd,
                    cardInteriorBrightness = cardInteriorMean(frame = frame, lu = lu, ru = ru, ld = ld, rd = rd),
                    cornerAnchorBrightness = cornerAnchorMean(frame = frame, anchors = anchors),
                )
            }
        }
    }

    private fun cardInteriorMean(
        frame: MiniProgramFrame,
        lu: MiniProgramPoint,
        ru: MiniProgramPoint,
        ld: MiniProgramPoint,
        rd: MiniProgramPoint,
    ): Double {
        val left = ((lu.column + ld.column) / 2.0 + (averageDistance(lu, ru, ld, rd) * 0.35)).roundToInt()
        val right = ((ru.column + rd.column) / 2.0 - (averageDistance(lu, ru, ld, rd) * 0.35)).roundToInt()
        val top = ((lu.row + ru.row) / 2.0 + (averageDistance(lu, ld, ru, rd) * 0.35)).roundToInt()
        val bottom = ((ld.row + rd.row) / 2.0 - (averageDistance(lu, ld, ru, rd) * 0.35)).roundToInt()
        return rectMean(frame, left = left, top = top, right = right, bottom = bottom)
    }

    private fun cornerAnchorMean(frame: MiniProgramFrame, anchors: MiniProgramAnchors): Double {
        val means = listOf(anchors.lu, anchors.ld, anchors.ru, anchors.rd).map { candidate ->
            pointMean(frame = frame, point = candidate.point, radius = 6)
        }
        return means.average()
    }

    private fun MiniProgramPoint.touchesFrameBorder(frameWidth: Int, frameHeight: Int): Boolean =
        column <= MIN_ANCHOR_BORDER_INSET ||
            row <= MIN_ANCHOR_BORDER_INSET ||
            column >= frameWidth - 1 - MIN_ANCHOR_BORDER_INSET ||
            row >= frameHeight - 1 - MIN_ANCHOR_BORDER_INSET

    private fun pointMean(frame: MiniProgramFrame, point: MiniProgramPoint, radius: Int): Double =
        rectMean(
            frame = frame,
            left = point.column - radius,
            top = point.row - radius,
            right = point.column + radius + 1,
            bottom = point.row + radius + 1,
        )

    private fun rectMean(
        frame: MiniProgramFrame,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Double {
        val clampedLeft = left.coerceIn(0, frame.width)
        val clampedRight = right.coerceIn(0, frame.width)
        val clampedTop = top.coerceIn(0, frame.height)
        val clampedBottom = bottom.coerceIn(0, frame.height)
        var sum = 0L
        var count = 0
        for (row in clampedTop until clampedBottom) {
            for (column in clampedLeft until clampedRight) {
                sum += frame[row, column]
                count += 1
            }
        }
        return if (count == 0) 0.0 else sum.toDouble() / count.toDouble()
    }

    private fun averageDistance(a1: MiniProgramPoint, a2: MiniProgramPoint, b1: MiniProgramPoint, b2: MiniProgramPoint): Double =
        (distance(a1, a2) + distance(b1, b2)) / 2.0

    private fun distance(a: MiniProgramPoint, b: MiniProgramPoint): Double =
        sqrt((a.column - b.column).toDouble() * (a.column - b.column).toDouble() + (a.row - b.row).toDouble() * (a.row - b.row).toDouble())

    private fun distance(a: MiniProgramGridPoint, b: MiniProgramGridPoint): Double =
        sqrt((a.column - b.column) * (a.column - b.column) + (a.row - b.row) * (a.row - b.row))

    private fun quadArea(vararg points: MiniProgramPoint): Double {
        var twiceArea = 0.0
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            twiceArea += current.column * next.row - current.row * next.column
        }
        return abs(twiceArea) / 2.0
    }

    private fun format(value: Double): String =
        "%.3f".format(java.util.Locale.US, value)

    private fun failureStage(failureReason: String): String =
        when {
            failureReason.startsWith("admission number failed:") -> "failureStage=admission"
            failureReason.startsWith("score warnings:") -> "failureStage=score"
            else -> "failureStage=unknown"
        }
}
