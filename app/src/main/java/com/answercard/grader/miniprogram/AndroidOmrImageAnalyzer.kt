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
    private val stabilityGate: (() -> Boolean)? = null,
    private val nowMsProvider: () -> Long = System::currentTimeMillis,
) : ImageAnalysis.Analyzer {
    private val busy = AtomicBoolean(false)
    private val intervalLock = Any()
    private var lastAnalyzeStartedAtMs: Long? = null
    private val frameIndex = AtomicLong(0L)
    private val droppedFrameCount = AtomicLong(0L)
    private val throttledFrameCount = AtomicLong(0L)
    private val busyFrameCount = AtomicLong(0L)
    private val unstableFrameCount = AtomicLong(0L)
    private val blurryFrameCount = AtomicLong(0L)
    private val lastDroppedReason = AtomicReference("none")
    private val lastLaplacianVariance = AtomicReference(Double.NaN)

    override fun analyze(image: ImageProxy) {
        val currentFrameIndex = frameIndex.incrementAndGet()
        val processedAtMs = nowMsProvider()
        if (stabilityGate?.invoke() == false) {
            droppedFrameCount.incrementAndGet()
            unstableFrameCount.incrementAndGet()
            lastDroppedReason.set("unstable")
            image.close()
            return
        }
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
            val frame = frameAdapter?.invoke(image)
                ?: CameraImageProxyFrameAdapter.fromImageProxy(
                    image = image,
                    orientationMode = options.analysisOrientationMode,
                )
            if (options.minLaplacianVariance > 0.0) {
                val sharpness = FrameSharpness.laplacianVariance(frame)
                lastLaplacianVariance.set(sharpness)
                if (sharpness < options.minLaplacianVariance) {
                    droppedFrameCount.incrementAndGet()
                    blurryFrameCount.incrementAndGet()
                    lastDroppedReason.set("blurry")
                    return
                }
            }
            val imageDebugInfo = image.debugInfo(
                currentFrameIndex = currentFrameIndex,
                processedAtMs = processedAtMs,
                droppedReason = "processed",
            )
            val template = templateProvider()
            val frameDebugInfo = frame.debugInfo(template)
            val result = processor.process(frame = frame, template = template)
            onResult(result.copy(debugInfo = imageDebugInfo + frameDebugInfo + result.debugInfo))
        } catch (error: Throwable) {
            onError(error)
        } finally {
            busy.set(false)
            image.close()
        }
    }

    private fun tryEnterAnalysis(nowMs: Long): FrameGateDecision {
        if (!busy.compareAndSet(false, true)) return FrameGateDecision.Busy
        synchronized(intervalLock) {
            val lastAnalyzeStartedAt = lastAnalyzeStartedAtMs
            if (
                lastAnalyzeStartedAt != null &&
                nowMs - lastAnalyzeStartedAt < options.minAnalyzeIntervalMs
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
            "unstableFrameCount=${unstableFrameCount.get()}",
            "blurryFrameCount=${blurryFrameCount.get()}",
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
        if (value.isNaN()) "disabled" else "%.1f".format(Locale.US, value)

    private enum class FrameGateDecision {
        Process,
        Busy,
        Throttled,
    }
}
