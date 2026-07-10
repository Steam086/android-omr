package com.answercard.grader.miniprogram

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class AndroidOmrImageAnalyzer(
    private val templateProvider: () -> TemplateState,
    private val onResult: (AndroidOmrResult) -> Unit,
    private val onError: (Throwable) -> Unit = {},
    private val processor: AndroidOmrFrameProcessor = AndroidOmrFrameProcessor(),
    private val options: AndroidOmrAnalyzerOptions = AndroidOmrAnalyzerOptions(),
    private val frameAdapter: ((ImageProxy) -> MiniProgramFrame)? = null,
    private val nowMsProvider: () -> Long = { System.nanoTime() / 1_000_000L },
    private val nanoTimeProvider: () -> Long = System::nanoTime,
    private val captureMetadataProvider: (Long) -> FrameCaptureMetadata? = { null },
    private val isDeviceStableProvider: () -> Boolean = { true },
    private val qualityEvaluator: FrameQualityEvaluator = FrameQualityEvaluator(FrameQualityThresholds.PRE_OMR),
) : ImageAnalysis.Analyzer {
    private val busy = AtomicBoolean(false)
    private val intervalLock = Any()
    private var lastAnalyzeStartedAtMs: Long? = null
    private val frameIndex = AtomicLong(0L)
    private val droppedFrameCount = AtomicLong(0L)
    private val throttledFrameCount = AtomicLong(0L)
    private val busyFrameCount = AtomicLong(0L)
    private val lastDroppedReason = AtomicReference("none")
    private val lastLaplacianVariance = AtomicReference(Double.NaN)
    private var candidateWindowStartedAtMs: Long? = null
    private var bestCandidate: FrameCandidate? = null

    override fun analyze(image: ImageProxy) {
        val analysisStartedAtNs = nanoTimeProvider()
        val currentFrameIndex = frameIndex.incrementAndGet()
        val processedAtMs = nowMsProvider()
        when (val gateDecision = tryEnterAnalysis(processedAtMs)) {
            FrameGateDecision.Process -> Unit
            FrameGateDecision.Busy -> {
                droppedFrameCount.incrementAndGet()
                busyFrameCount.incrementAndGet()
                lastDroppedReason.set("busy")
                image.close()
                return
            }
            FrameGateDecision.Throttled -> {
                droppedFrameCount.incrementAndGet()
                throttledFrameCount.incrementAndGet()
                lastDroppedReason.set("throttled")
                image.close()
                return
            }
        }
        try {
            val metadata = captureMetadataProvider(image.imageInfo.timestamp)
            val imageDebugInfo = image.debugInfo(
                currentFrameIndex = currentFrameIndex,
                processedAtMs = processedAtMs,
                droppedReason = "processed",
            )
            val deviceStable = isDeviceStableProvider()
            val captureGate = CaptureStateEvaluator.evaluate(metadata)
            val captureDebugInfo =
                (metadata?.debugInfo() ?: listOf("captureMetadata=unavailable")) +
                    listOf(
                        "deviceStable=$deviceStable",
                        "captureGateAccepted=${captureGate.accepted}",
                        "captureGateRejection=${captureGate.rejectionReason?.name ?: "none"}",
                    )
            val frameAdapterStartedAtNs = nanoTimeProvider()
            val frame = frameAdapter?.invoke(image)
                ?: CameraImageProxyFrameAdapter.fromImageProxy(
                    image = image,
                    orientationMode = options.analysisOrientationMode,
                )
            val frameAdapterElapsedMs = elapsedMs(frameAdapterStartedAtNs)
            val template = templateProvider()
            val frameDebugInfo = frame.debugInfo(template)
            val minimumResolution = options.minimumAnalysisResolution
            if (minimumResolution != null && !minimumResolution.accepts(frame.width, frame.height)) {
                clearCandidateWindow()
                emitRejection(
                    reason = ScanRejectionReason.RETAKE_LOW_RESOLUTION,
                    message = "analysis resolution is below minimum",
                    debugInfo = imageDebugInfo + captureDebugInfo + frameDebugInfo +
                        "frameAdapterElapsedMs=$frameAdapterElapsedMs" +
                        "actualAnalysisResolution=${frame.width}x${frame.height}" +
                        "minimumAnalysisResolution=$minimumResolution" +
                        "omrElapsedMs=skipped" +
                        "failureStage=analysis resolution" +
                        analyzerTimingDebug(analysisStartedAtNs),
                )
                return
            }
            val frameQualityStartedAtNs = nanoTimeProvider()
            val quality = qualityEvaluator.evaluate(frame)
            val frameQualityElapsedMs = elapsedMs(frameQualityStartedAtNs)
            lastLaplacianVariance.set(quality.metrics.rawLaplacianVariance)
            val qualityDebugInfo = quality.debugInfo("frame") + listOf(
                "frameAdapterElapsedMs=$frameAdapterElapsedMs",
                "frameQualityElapsedMs=$frameQualityElapsedMs",
            )
            if (options.enableFrameQualityGate && !quality.accepted) {
                clearCandidateWindow()
                val reason = quality.rejectionReason ?: ScanRejectionReason.RETAKE_BLUR
                emitRejection(
                    reason = reason,
                    message = when (reason) {
                        ScanRejectionReason.RETAKE_EXPOSURE -> "frame exposure is outside the safe range"
                        else -> "frame is too blurry"
                    },
                    debugInfo = imageDebugInfo + captureDebugInfo + frameDebugInfo + qualityDebugInfo +
                        "omrElapsedMs=skipped" + "failureStage=frame quality" +
                        analyzerTimingDebug(analysisStartedAtNs),
                )
                return
            }
            val candidate = FrameCandidate(
                frame = frame,
                template = template,
                qualityScore = quality.metrics.qualityScore,
                frameIndex = currentFrameIndex,
                debugInfo = imageDebugInfo + captureDebugInfo + frameDebugInfo + qualityDebugInfo,
            )
            val selected = selectCandidate(candidate, processedAtMs) ?: return
            val omrStartedAtNs = nanoTimeProvider()
            val result = processor.process(frame = selected.frame, template = selected.template)
            val omrElapsedMs = elapsedMs(omrStartedAtNs)
            onResult(
                result.copy(
                    debugInfo = selected.debugInfo +
                        "candidateWindowMs=${options.candidateWindowMs}" +
                        "selectedFrameIndex=${selected.frameIndex}" +
                        "omrElapsedMs=$omrElapsedMs" +
                        analyzerTimingDebug(analysisStartedAtNs) + result.debugInfo,
                ),
            )
        } catch (error: Throwable) {
            onError(error)
        } finally {
            busy.set(false)
            image.close()
        }
    }

    private fun selectCandidate(candidate: FrameCandidate, nowMs: Long): FrameCandidate? {
        if (options.candidateWindowMs == 0L) return candidate
        val startedAt = candidateWindowStartedAtMs
        if (startedAt == null) {
            candidateWindowStartedAtMs = nowMs
            bestCandidate = candidate
            return null
        }
        if (candidate.qualityScore > (bestCandidate?.qualityScore ?: Double.NEGATIVE_INFINITY)) {
            bestCandidate = candidate
        }
        if (nowMs - startedAt < options.candidateWindowMs) return null
        return bestCandidate.also { clearCandidateWindow() }
    }

    private fun clearCandidateWindow() {
        candidateWindowStartedAtMs = null
        bestCandidate = null
    }

    private fun emitRejection(
        reason: ScanRejectionReason,
        message: String,
        debugInfo: List<String>,
    ) {
        onResult(AndroidOmrResult.rejected(reason = reason, message = message, debugInfo = debugInfo))
    }

    private fun analyzerTimingDebug(analysisStartedAtNs: Long): String =
        "analyzerElapsedMs=${elapsedMs(analysisStartedAtNs)}"

    private fun elapsedMs(startedAtNs: Long): Long =
        ((nanoTimeProvider() - startedAtNs).coerceAtLeast(0L)) / 1_000_000L

    private fun tryEnterAnalysis(nowMs: Long): FrameGateDecision {
        if (!busy.compareAndSet(false, true)) return FrameGateDecision.Busy
        synchronized(intervalLock) {
            val lastAnalyzeStartedAt = lastAnalyzeStartedAtMs
            val elapsedSinceLastAnalyzeMs = lastAnalyzeStartedAt?.let { nowMs - it }
            if (
                elapsedSinceLastAnalyzeMs != null &&
                elapsedSinceLastAnalyzeMs >= 0L &&
                elapsedSinceLastAnalyzeMs < options.minAnalyzeIntervalMs
            ) {
                busy.set(false)
                return FrameGateDecision.Throttled
            }
            lastAnalyzeStartedAtMs = nowMs
        }
        return FrameGateDecision.Process
    }

    private fun ImageProxy.debugInfo(
        currentFrameIndex: Long,
        processedAtMs: Long,
        droppedReason: String,
    ): List<String> {
        val yPlane = planes.firstOrNull()
        val crop = cropRect
        return listOfNotNull(
            "frameIndex=$currentFrameIndex",
            "imageTimestamp=${imageInfo.timestamp}",
            "processedAtMs=$processedAtMs",
            "droppedReason=$droppedReason",
            "droppedFrameCount=${droppedFrameCount.get()}",
            "throttledFrameCount=${throttledFrameCount.get()}",
            "busyFrameCount=${busyFrameCount.get()}",
            "analyzerBusy=${busy.get()}",
            "lastDroppedReason=${lastDroppedReason.get()}",
            "laplacianVariance=${formatSharpness(lastLaplacianVariance.get())}",
            "ImageProxy=${width}x${height}",
            "cropRect=${crop.left},${crop.top},${crop.right - crop.left}x${crop.bottom - crop.top}",
            "rotationDegrees=${imageInfo.rotationDegrees}",
            "rowStride=${yPlane?.rowStride ?: "missing"}",
            "pixelStride=${yPlane?.pixelStride ?: "missing"}",
            "analysisOrientation=${CameraImageProxyFrameAdapter.analysisOrientation(options.analysisOrientationMode)}",
            options.requestedAnalysisResolutionLabel?.let { "requestedAnalysisResolution=$it" },
            options.minimumAnalysisResolution?.let { "minimumAnalysisResolution=$it" },
            "candidateWindowMs=${options.candidateWindowMs}",
            "frameQualityGate=${options.enableFrameQualityGate}",
        )
    }

    private fun MiniProgramFrame.debugInfo(template: TemplateState): List<String> {
        val layout = TemplateGeometry.buildLayout(template)
        val templateRatio = TemplateGeometry.renderedWidth(layout) / TemplateGeometry.renderedHeight(layout)
        val frameRatio = width.toDouble() / height.coerceAtLeast(1).toDouble()
        val templateOrientation = orientation(templateRatio.toDouble())
        val analysisFrameOrientation = orientation(frameRatio)
        val orientationMismatch = templateOrientation != analysisFrameOrientation
        return listOf(
            "MiniProgramFrame=${width}x$height",
            "centerLuma=${centerLuma()}",
            "templateRatio=${format(templateRatio.toDouble())}",
            "frameRatio=${format(frameRatio)}",
            "templateOrientation=$templateOrientation",
            "analysisFrameOrientation=$analysisFrameOrientation",
            "orientationMismatch=$orientationMismatch",
            if (orientationMismatch) {
                "orientationMessage=Analysis frame orientation differs from template orientation."
            } else {
                "orientationMessage=analysis frame orientation matches template orientation"
            },
        )
    }

    private fun MiniProgramFrame.centerLuma(): Int {
        val centerRow = height / 2
        val centerColumn = width / 2
        var sum = 0
        var count = 0
        for (row in centerRow - 1..centerRow + 1) {
            for (column in centerColumn - 1..centerColumn + 1) {
                if (row in 0 until height && column in 0 until width) {
                    sum += pixels[row * width + column]
                    count += 1
                }
            }
        }
        return if (count == 0) 0 else sum / count
    }

    private fun orientation(ratio: Double): String =
        if (ratio >= 1.0) "landscape" else "portrait"

    private fun format(value: Double): String =
        "%.3f".format(Locale.US, value)

    private fun formatSharpness(value: Double): String =
        "%.1f".format(Locale.US, value)

    private enum class FrameGateDecision {
        Process,
        Busy,
        Throttled,
    }

    private data class FrameCandidate(
        val frame: MiniProgramFrame,
        val template: TemplateState,
        val qualityScore: Double,
        val frameIndex: Long,
        val debugInfo: List<String>,
    )
}
