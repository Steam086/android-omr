package com.answercard.grader.miniprogram

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import com.answercard.grader.template.TemplateState
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidOmrImageAnalyzerTest {
    @Test
    fun analyzeSendsResultAndClosesImageProxy() {
        val image = FakeImageProxy()
        val expected = result(success = true)
        var received: AndroidOmrResult? = null
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = { received = it },
            frameAdapter = { MiniProgramFrame(width = 1, height = 1, pixels = intArrayOf(255)) },
            processor = AndroidOmrFrameProcessor { _, _ -> expected },
        )

        analyzer.analyze(image)

        assertEquals(expected.success, received?.success)
        assertTrue(image.closed)
    }

    @Test
    fun analyzeSkipsFramesInsideMinimumAnalyzeIntervalAndClosesThem() {
        var nowMs = 1_000L
        var resultCount = 0
        var processCount = 0
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = { resultCount++ },
            frameAdapter = { MiniProgramFrame(width = 1, height = 1, pixels = intArrayOf(255)) },
            processor = AndroidOmrFrameProcessor { _, _ ->
                processCount++
                result(success = true)
            },
            options = AndroidOmrAnalyzerOptions(minAnalyzeIntervalMs = 300L),
            nowMsProvider = { nowMs },
        )
        val first = FakeImageProxy()
        val skipped = FakeImageProxy()

        analyzer.analyze(first)
        nowMs = 1_100L
        analyzer.analyze(skipped)

        assertEquals(1, processCount)
        assertEquals(1, resultCount)
        assertTrue(first.closed)
        assertTrue(skipped.closed)
    }

    @Test
    fun analyzeProcessesFramesAfterMinimumAnalyzeInterval() {
        var nowMs = 1_000L
        var resultCount = 0
        var processCount = 0
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = { resultCount++ },
            frameAdapter = { MiniProgramFrame(width = 1, height = 1, pixels = intArrayOf(255)) },
            processor = AndroidOmrFrameProcessor { _, _ ->
                processCount++
                result(success = true)
            },
            options = AndroidOmrAnalyzerOptions(minAnalyzeIntervalMs = 300L),
            nowMsProvider = { nowMs },
        )
        val first = FakeImageProxy()
        val second = FakeImageProxy()

        analyzer.analyze(first)
        nowMs = 1_300L
        analyzer.analyze(second)

        assertEquals(2, processCount)
        assertEquals(2, resultCount)
        assertTrue(first.closed)
        assertTrue(second.closed)
    }

    @Test
    fun analyzeSendsErrorsAndClosesImageProxy() {
        val image = FakeImageProxy()
        val expected = IllegalStateException("adapter failed")
        var received: Throwable? = null
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = {},
            onError = { received = it },
            frameAdapter = { throw expected },
        )

        analyzer.analyze(image)

        assertSame(expected, received)
        assertTrue(image.closed)
    }

    @Test
    fun analyzeSendsProcessorErrorsAndClosesImageProxy() {
        val image = FakeImageProxy()
        val expected = IllegalStateException("processor failed")
        var received: Throwable? = null
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = {},
            onError = { received = it },
            frameAdapter = { MiniProgramFrame(width = 1, height = 1, pixels = intArrayOf(255)) },
            processor = AndroidOmrFrameProcessor { _, _ -> throw expected },
        )

        analyzer.analyze(image)

        assertSame(expected, received)
        assertTrue(image.closed)
    }

    @Test
    fun analyzePassesFrameAndTemplateToProcessor() {
        val image = FakeImageProxy()
        val frame = MiniProgramFrame(width = 1, height = 1, pixels = intArrayOf(42))
        val template = TemplateState.default()
        var processedFrame: MiniProgramFrame? = null
        var processedTemplate: TemplateState? = null
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { template },
            onResult = {},
            frameAdapter = { frame },
            processor = AndroidOmrFrameProcessor { processorFrame, processorTemplate ->
                processedFrame = processorFrame
                processedTemplate = processorTemplate
                result(success = true)
            },
        )

        analyzer.analyze(image)

        assertSame(frame, processedFrame)
        assertSame(template, processedTemplate)
        assertTrue(image.closed)
    }

    @Test
    fun analyzeAddsImageProxyAndFrameDiagnosticsToResult() {
        val image = FakeImageProxy(timestamp = 42L)
        var received: AndroidOmrResult? = null
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = { received = it },
            frameAdapter = { MiniProgramFrame(width = 3, height = 2, pixels = IntArray(6) { 180 }) },
            processor = AndroidOmrFrameProcessor { _, _ -> result(success = false).copy(debugInfo = listOf("engine=debug")) },
            options = AndroidOmrAnalyzerOptions(requestedAnalysisResolutionLabel = "1280x960"),
            nowMsProvider = { 1_234L },
        )

        analyzer.analyze(image)

        val debugInfo = received?.debugInfo.orEmpty()
        assertTrue(debugInfo.contains("frameIndex=1"))
        assertTrue(debugInfo.contains("imageTimestamp=42"))
        assertTrue(debugInfo.contains("processedAtMs=1234"))
        assertTrue(debugInfo.contains("droppedReason=processed"))
        assertTrue(debugInfo.contains("ImageProxy=1x1"))
        assertTrue(debugInfo.contains("cropRect=0,0,1x1"))
        assertTrue(debugInfo.contains("rotationDegrees=0"))
        assertTrue(debugInfo.contains("rowStride=1"))
        assertTrue(debugInfo.contains("pixelStride=1"))
        assertTrue(debugInfo.contains("analysisOrientation=landscape-template"))
        assertTrue(debugInfo.contains("requestedAnalysisResolution=1280x960"))
        assertTrue(debugInfo.contains("MiniProgramFrame=3x2"))
        assertTrue(debugInfo.contains("centerLuma=180"))
        assertTrue(debugInfo.any { it.startsWith("templateRatio=") })
        assertTrue(debugInfo.contains("frameRatio=1.500"))
        assertTrue(debugInfo.contains("templateOrientation=landscape"))
        assertTrue(debugInfo.contains("analysisFrameOrientation=landscape"))
        assertTrue(debugInfo.contains("orientationMismatch=false"))
        assertTrue(debugInfo.contains("engine=debug"))
    }

    @Test
    fun analyzeReportsFollowImageRotationOrientationMode() {
        var received: AndroidOmrResult? = null
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = { received = it },
            frameAdapter = { MiniProgramFrame(width = 2, height = 3, pixels = IntArray(6) { 255 }) },
            processor = AndroidOmrFrameProcessor { _, _ -> result(success = false) },
            options = AndroidOmrAnalyzerOptions(
                analysisOrientationMode = OmrAnalysisOrientationMode.FOLLOW_IMAGE_ROTATION,
            ),
        )

        analyzer.analyze(FakeImageProxy())

        assertTrue(received?.debugInfo.orEmpty().contains("analysisOrientation=follow-image-rotation"))
    }

    @Test
    fun analyzeIncrementsFrameIndexAcrossSkippedFramesAndReportsDropCounts() {
        var nowMs = 1_000L
        val debugSnapshots = mutableListOf<List<String>>()
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = { debugSnapshots += it.debugInfo },
            frameAdapter = { MiniProgramFrame(width = 2, height = 2, pixels = IntArray(4) { 100 }) },
            processor = AndroidOmrFrameProcessor { _, _ -> result(success = true) },
            options = AndroidOmrAnalyzerOptions(minAnalyzeIntervalMs = 300L),
            nowMsProvider = { nowMs },
        )
        val first = FakeImageProxy(timestamp = 10L)
        val skipped = FakeImageProxy(timestamp = 20L)
        val third = FakeImageProxy(timestamp = 30L)

        analyzer.analyze(first)
        nowMs = 1_100L
        analyzer.analyze(skipped)
        nowMs = 1_300L
        analyzer.analyze(third)

        assertEquals(2, debugSnapshots.size)
        assertTrue(debugSnapshots[0].contains("frameIndex=1"))
        assertTrue(debugSnapshots[0].contains("droppedFrameCount=0"))
        assertTrue(debugSnapshots[1].contains("frameIndex=3"))
        assertTrue(debugSnapshots[1].contains("imageTimestamp=30"))
        assertTrue(debugSnapshots[1].contains("droppedFrameCount=1"))
        assertTrue(debugSnapshots[1].contains("throttledFrameCount=1"))
        assertTrue(debugSnapshots[1].contains("busyFrameCount=0"))
        assertTrue(debugSnapshots[1].contains("lastDroppedReason=throttled"))
        assertTrue(first.closed)
        assertTrue(skipped.closed)
        assertTrue(third.closed)
    }

    @Test
    fun analyzeReportsDifferentCenterLumaForDifferentFrames() {
        var nowMs = 1_000L
        val debugSnapshots = mutableListOf<List<String>>()
        val frames = ArrayDeque(
            listOf(
                MiniProgramFrame(width = 3, height = 3, pixels = IntArray(9) { 40 }),
                MiniProgramFrame(width = 3, height = 3, pixels = IntArray(9) { 210 }),
            ),
        )
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = { debugSnapshots += it.debugInfo },
            frameAdapter = { frames.removeFirst() },
            processor = AndroidOmrFrameProcessor { _, _ -> result(success = true) },
            options = AndroidOmrAnalyzerOptions(minAnalyzeIntervalMs = 0L),
            nowMsProvider = { nowMs++ },
        )

        analyzer.analyze(FakeImageProxy(timestamp = 10L))
        analyzer.analyze(FakeImageProxy(timestamp = 20L))

        assertTrue(debugSnapshots[0].contains("centerLuma=40"))
        assertTrue(debugSnapshots[1].contains("centerLuma=210"))
    }

    @Test
    fun analyzeReportsOrientationMismatchWhenLandscapeTemplateGetsPortraitFrame() {
        var received: AndroidOmrResult? = null
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = { received = it },
            frameAdapter = { MiniProgramFrame(width = 2, height = 3, pixels = IntArray(6) { 255 }) },
            processor = AndroidOmrFrameProcessor { _, _ -> result(success = false) },
        )

        analyzer.analyze(FakeImageProxy())

        val debugInfo = received?.debugInfo.orEmpty()
        assertTrue(debugInfo.contains("templateOrientation=landscape"))
        assertTrue(debugInfo.contains("analysisFrameOrientation=portrait"))
        assertTrue(debugInfo.contains("orientationMismatch=true"))
        assertTrue(debugInfo.contains("orientationMessage=Analysis frame orientation differs from template orientation."))
    }

    @Test
    fun analyzeDropsReentrantFramesWhileBusyAndClosesThem() {
        val nestedImage = FakeImageProxy()
        var resultCount = 0
        lateinit var analyzer: AndroidOmrImageAnalyzer
        analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = { resultCount++ },
            frameAdapter = { MiniProgramFrame(width = 1, height = 1, pixels = intArrayOf(255)) },
            processor = AndroidOmrFrameProcessor { _, _ ->
                analyzer.analyze(nestedImage)
                result(success = true)
            },
            options = AndroidOmrAnalyzerOptions(minAnalyzeIntervalMs = 0L),
        )
        val first = FakeImageProxy()

        analyzer.analyze(first)

        assertEquals(1, resultCount)
        assertTrue(first.closed)
        assertTrue(nestedImage.closed)
    }

    @Test
    fun analyzeRejectsLowSharpnessFramesBeforeProcessor() {
        var processCount = 0
        var received: AndroidOmrResult? = null
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = { received = it },
            frameAdapter = {
                MiniProgramFrame(
                    width = 64,
                    height = 64,
                    pixels = IntArray(64 * 64) { index -> (index % 64) * 255 / 63 },
                )
            },
            processor = AndroidOmrFrameProcessor { _, _ ->
                processCount++
                result(success = true)
            },
        )
        val image = FakeImageProxy()

        analyzer.analyze(image)

        assertEquals(0, processCount)
        assertEquals(ScanRejectionReason.RETAKE_BLUR, received?.rejectionReason)
        assertTrue(image.closed)
    }

    @Test
    fun analyzeWaitsForAutofocusBeforeAdaptingFrame() {
        var adapterCount = 0
        var received: AndroidOmrResult? = null
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = { received = it },
            frameAdapter = {
                adapterCount++
                MiniProgramFrame(width = 1, height = 1, pixels = intArrayOf(255))
            },
            captureMetadataProvider = {
                FrameCaptureMetadata(
                    timestampNs = it,
                    focusState = CameraFocusState.SCANNING,
                    exposureState = CameraExposureState.CONVERGED,
                    focusRequired = true,
                    exposureRequired = true,
                    exposureTimeNs = 10_000_000L,
                    iso = 100,
                )
            },
        )

        analyzer.analyze(FakeImageProxy(timestamp = 42L))

        assertEquals(0, adapterCount)
        assertEquals(ScanRejectionReason.WAIT_FOCUS, received?.rejectionReason)
        assertTrue(received?.debugInfo.orEmpty().contains("afState=SCANNING"))
    }

    @Test
    fun analyzeChoosesBestFrameAtEndOfCandidateWindow() {
        var nowMs = 1_000L
        val first = crispFrame(marker = 40)
        val second = crispFrame(marker = 80)
        val frames = ArrayDeque(listOf(first, second))
        var processed: MiniProgramFrame? = null
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = {},
            frameAdapter = { frames.removeFirst() },
            processor = AndroidOmrFrameProcessor { frame, _ ->
                processed = frame
                result(success = true)
            },
            options = AndroidOmrAnalyzerOptions(minAnalyzeIntervalMs = 0L, candidateWindowMs = 400L),
            nowMsProvider = { nowMs },
        )

        analyzer.analyze(FakeImageProxy(timestamp = 10L))
        nowMs = 1_400L
        analyzer.analyze(FakeImageProxy(timestamp = 20L))

        assertSame(first, processed)
    }

    @Test
    fun analyzeAlwaysReportsMeasuredSharpnessInDebug() {
        var received: AndroidOmrResult? = null
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = { received = it },
            frameAdapter = { MiniProgramFrame(width = 4, height = 4, pixels = sharpPixels()) },
            processor = AndroidOmrFrameProcessor { _, _ -> result(success = true) },
        )

        analyzer.analyze(FakeImageProxy())

        val debugInfo = received?.debugInfo.orEmpty()
        assertTrue(
            debugInfo.any { it.startsWith("laplacianVariance=") && it != "laplacianVariance=disabled" },
        )
    }

    // A hard black/white vertical split → strong Laplacian edge → high sharpness variance.
    private fun sharpPixels(): IntArray = IntArray(16) { index -> if (index % 4 < 2) 0 else 255 }

    private fun crispFrame(marker: Int): MiniProgramFrame =
        MiniProgramFrame(
            width = 64,
            height = 64,
            pixels = IntArray(64 * 64) { index ->
                if (index == 0) marker else if ((index / 64 / 4 + index % 64 / 4) % 2 == 0) 20 else 235
            },
        )

    private fun result(success: Boolean): AndroidOmrResult =
        AndroidOmrResult(
            success = success,
            failureReason = null,
            layout = null,
            anchors = null,
            grid = null,
            answerArea = null,
            admissionNumber = null,
            score = null,
            warnings = emptyList(),
            debugInfo = emptyList(),
        )

    private class FakeImageProxy(
        private val timestamp: Long = 0L,
    ) : ImageProxy {
        var closed = false
        private var crop = rect(0, 0, 1, 1)

        override fun close() {
            closed = true
        }

        override fun getCropRect(): Rect = crop

        override fun setCropRect(rect: Rect?) {
            crop = rect ?: rect(0, 0, width, height)
        }

        override fun getFormat(): Int = 0

        override fun getHeight(): Int = 1

        override fun getWidth(): Int = 1

        override fun getPlanes(): Array<ImageProxy.PlaneProxy> = arrayOf(FakePlaneProxy())

        override fun getImageInfo(): ImageInfo = FakeImageInfo(timestamp)

        override fun getImage(): Image? = null

        override fun toBitmap(): Bitmap {
            throw UnsupportedOperationException("not used")
        }
    }

    private class FakePlaneProxy : ImageProxy.PlaneProxy {
        override fun getRowStride(): Int = 1

        override fun getPixelStride(): Int = 1

        override fun getBuffer(): ByteBuffer = ByteBuffer.wrap(byteArrayOf(255.toByte()))
    }

    private class FakeImageInfo(
        private val timestamp: Long,
    ) : ImageInfo {
        override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()

        override fun getTimestamp(): Long = timestamp

        override fun getRotationDegrees(): Int = 0

        override fun populateExifData(builder: ExifData.Builder) {
            // Not used by analyzer.
        }
    }

    private companion object {
        fun rect(left: Int, top: Int, right: Int, bottom: Int): Rect =
            Rect().also {
                it.left = left
                it.top = top
                it.right = right
                it.bottom = bottom
            }
    }
}
