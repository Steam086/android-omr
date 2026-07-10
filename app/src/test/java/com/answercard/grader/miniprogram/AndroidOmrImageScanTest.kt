package com.answercard.grader.miniprogram

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.Rect
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateRenderer
import com.answercard.grader.template.TemplateState
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidOmrImageScanTest {
    @Test
    fun formalScanReturnsCornerFailureForImageWithoutAnchors() {
        val result = AndroidOmrEngine.scan(whiteFrame(width = 420, height = 320), templateForImageScan())

        assertFalse(result.success)
        assertEquals(ScanRejectionReason.RETAKE_CODED_MARKERS, result.rejectionReason)
        assertNotNull(result.layout)
        assertTrue(result.debugInfo.any { it == "anchorPath=coded-marker-rejected" })
        assertTrue(result.debugInfo.none { it == "anchorPath=solid-marker" || it == "anchorPath=l-bracket" })
    }

    @Test
    fun formalScanReadsRenderedProductionImageWithAnchorsAnswersAdmissionAndScore() {
        val template = templateForImageScan()
        val bitmap = filledProductionTemplateBitmap(template, scale = 3f)

        val result = AndroidOmrEngine.scan(bitmap.toMiniProgramFrame(), template)

        assertTrue(result.debugInfo.joinToString(), result.success)
        assertNotNull(result.anchors)
        assertNotNull(result.grid)
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 0 }?.selectedLabels)
        assertEquals(listOf("B"), result.answerArea?.questions?.single { it.questionIndex == 1 }?.selectedLabels)
        assertEquals(listOf("C"), result.answerArea?.questions?.single { it.questionIndex == 5 }?.selectedLabels)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 15 }?.selectedLabels)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun formalScanReadsProductionCardAtScalePointEightFive() {
        val template = templateForImageScan()
        val bitmap = filledProductionTemplateBitmap(template, scale = 0.85f)

        val result = AndroidOmrEngine.scan(bitmap.toMiniProgramFrame(), template)

        assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 0 }?.selectedLabels)
        assertEquals(listOf("B"), result.answerArea?.questions?.single { it.questionIndex == 1 }?.selectedLabels)
        assertEquals(listOf("C"), result.answerArea?.questions?.single { it.questionIndex == 5 }?.selectedLabels)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 15 }?.selectedLabels)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun formalScanReadsRenderedProductionImageRotated180Degrees() {
        val template = templateForImageScan()
        val bitmap = filledProductionTemplateBitmap(template, scale = 3f)
        val rotatedBitmap = rotateBitmap180(bitmap)

        val result = AndroidOmrEngine.scan(rotatedBitmap.toMiniProgramFrame(), template)

        assertTrue(result.debugInfo.joinToString(), result.success)
        assertNotNull(result.anchors)
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    private fun rotateBitmap180(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val rotated = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        for (row in 0 until height) {
            for (col in 0 until width) {
                rotated.setPixel(width - 1 - col, height - 1 - row, bitmap.getPixel(col, row))
            }
        }
        return rotated
    }

    @Test
    fun formalScanHandlesLightRenderedProductionImageNoise() {
        val template = templateForImageScan()
        val bitmap = filledProductionTemplateBitmap(template, scale = 3f)
        addSparseNoise(bitmap)

        val result = AndroidOmrEngine.scan(
            frame = bitmap.toMiniProgramFrame(),
            template = template,
        )

        assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun productionTemplateIndependentLFindsAnchorsAndGrid() {
        val template = TemplateState.default()
        val bitmap = TemplateRenderer.render(template, scale = 3f)
        writeDebugPng(bitmap, "production-template-independent-l.png")

        val result = AndroidOmrEngine.scan(
            frame = bitmap.toMiniProgramFrame(),
            template = template,
        )

        assertNotNull(result.anchors)
        assertNotNull(result.grid)
        assertFalse(result.failureReason == "corner anchors not found")
    }

    @Test
    fun formalScanRejectsProjectedCellsThatAreTooSmallBeforeBubbleRead() {
        val template = templateForImageScan()
        val bitmap = filledProductionTemplateBitmap(template, scale = 0.7f)

        val result = AndroidOmrEngine.scan(
            frame = bitmap.toMiniProgramFrame(),
            template = template,
        )

        assertFalse(result.success)
        assertTrue(result.failureReason.orEmpty(), result.failureReason.orEmpty().startsWith("projected cell too small:"))
        assertFalse(result.failureReason.orEmpty().contains("bubble read failed"))
        assertTrue(result.debugInfo.any { it.contains("failureStage=required cell validation") })
    }

    @Test
    fun formalScanRejectsTinyFalseAnchorsBeforeBubbleRead() {
        val bitmap = Bitmap.createBitmap(300, 220, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        drawIndependentAnchorsOnly(bitmap)

        val result = AndroidOmrEngine.scan(
            frame = bitmap.toMiniProgramFrame(),
            template = templateForImageScan(),
            anchorMode = AnchorMode.LEGACY,
        )

        assertFalse(result.success)
        assertEquals(ScanRejectionReason.RETAKE_LEGACY_MARKERS, result.rejectionReason)
        assertFalse(result.debugInfo.joinToString().contains("bubble read failed"))
        assertTrue(result.debugInfo.any { it.contains("failureStage=corner") })
    }

    @Test
    fun formalScanRejectsDarkFalseCardInteriorBeforeReaders() {
        val frame = MiniProgramFrame(width = 640, height = 420, pixels = IntArray(640 * 420) { 60 })
        drawCornerAnchorOnFrame(frame, MiniProgramCornerKind.LU, row = 50, column = 70, armLength = 70, thickness = 12, value = 20)
        drawCornerAnchorOnFrame(frame, MiniProgramCornerKind.LD, row = 300, column = 70, armLength = 70, thickness = 12, value = 20)
        drawCornerAnchorOnFrame(frame, MiniProgramCornerKind.RU, row = 50, column = 465, armLength = 70, thickness = 12, value = 20)
        drawCornerAnchorOnFrame(frame, MiniProgramCornerKind.RD, row = 300, column = 465, armLength = 70, thickness = 12, value = 20)

        val result = AndroidOmrEngine.scan(
            frame = frame,
            template = templateForImageScan(),
            anchorMode = AnchorMode.LEGACY,
        )

        assertFalse(result.success)
        assertEquals(ScanRejectionReason.RETAKE_LEGACY_MARKERS, result.rejectionReason)
        assertTrue(result.debugInfo.joinToString(), result.debugInfo.any { it == "failureStage=corner" })
        assertFalse(result.debugInfo.joinToString().contains("bubble read failed"))
    }

    @Test
    fun formalScanRejectsFrameBorderAnchorsBeforeProjectedCellValidation() {
        val bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        drawFrameBorderAnchorsOnly(bitmap)

        val result = AndroidOmrEngine.scan(
            frame = bitmap.toMiniProgramFrame(),
            template = templateForImageScan(),
            anchorMode = AnchorMode.LEGACY,
        )

        assertFalse(result.success)
        assertEquals(ScanRejectionReason.RETAKE_LEGACY_MARKERS, result.rejectionReason)
        assertFalse(result.debugInfo.joinToString().contains("failureStage=cell size validation"))
        assertFalse(result.debugInfo.joinToString().contains("bubble read failed"))
        assertTrue(result.debugInfo.any { it.contains("failureStage=corner") })
    }

    @Test
    fun formalScanReadsFilledProductionTemplateWithTemplateGeometryCoordinates() {
        val template = templateForImageScan()
        val bitmap = filledProductionTemplateBitmap(template, scale = 3f)
        writeDebugPng(bitmap, "production-template-filled.png")

        val result = AndroidOmrEngine.scan(
            frame = bitmap.toMiniProgramFrame(),
            template = template,
        )

        assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertNotNull(result.anchors)
        assertNotNull(result.grid)
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 0 }?.selectedLabels)
        assertEquals(listOf("B"), result.answerArea?.questions?.single { it.questionIndex == 1 }?.selectedLabels)
        assertEquals(listOf("C"), result.answerArea?.questions?.single { it.questionIndex == 5 }?.selectedLabels)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 15 }?.selectedLabels)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun formalScanReadsRenderedHeaderlessTemplateAnswersAndScore() {
        val template = templateForImageScan().withShowHeader(false)
        val bitmap = TemplateRenderer.render(template, scale = 3f)
        markProductionAnswer(bitmap, template, questionNumber = 1, optionLabel = "A", scale = 3f)
        markProductionAnswer(bitmap, template, questionNumber = 2, optionLabel = "B", scale = 3f)
        markProductionAnswer(bitmap, template, questionNumber = 6, optionLabel = "C", scale = 3f)
        markProductionAnswer(bitmap, template, questionNumber = 11, optionLabel = "D", scale = 3f)
        markProductionAnswer(bitmap, template, questionNumber = 16, optionLabel = "A", scale = 3f)
        writeDebugPng(bitmap, "production-template-headerless-filled.png")

        val result = AndroidOmrEngine.scan(bitmap.toMiniProgramFrame(), template)

        assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertNotNull(result.anchors)
        assertEquals("", result.admissionNumber?.digits)
        assertEquals(true, result.admissionNumber?.success)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 0 }?.selectedLabels)
        assertEquals(listOf("B"), result.answerArea?.questions?.single { it.questionIndex == 1 }?.selectedLabels)
        assertEquals(listOf("C"), result.answerArea?.questions?.single { it.questionIndex == 5 }?.selectedLabels)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 15 }?.selectedLabels)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun desktopReferenceOldTemplateSmokeReportsScanStage() {
        val file = findDesktopReferenceCard()
        assertTrue("desktop-reference-card.png should exist for smoke diagnostics", file.isFile)

        val result = AndroidOmrEngine.scan(
            frame = AndroidOmrRenderedImageFactory.loadPngAsFrame(file),
            template = TemplateState.default(),
            anchorMode = AnchorMode.LEGACY,
        )

        assertTrue(result.debugInfo.isNotEmpty())
        if (!result.success) {
            val reason = result.failureReason.orEmpty()
            assertTrue(
                "unexpected failure stage: $reason",
                reason.startsWith("legacy corner anchors not found") ||
                    reason.startsWith("grid failed:") ||
                    reason.startsWith("invalid card geometry:") ||
                    reason.startsWith("answer area failed:") ||
                    reason.startsWith("admission number failed:") ||
                    reason.startsWith("score warnings:"),
            )
        }
    }

    @Test
    fun scanWechatPhotoRecognizesAnchorsAnswersAdmissionAndScore() {
        val file = findWechatPhoto()
        assertTrue("wechat photo should exist for real-image regression", file.isFile)
        val template = templateForImageScan()

        val result = AndroidOmrEngine.scan(
            frame = AndroidOmrRenderedImageFactory.loadPngAsFrame(file),
            template = template,
            anchorMode = AnchorMode.LEGACY,
        )

        assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertNotNull(result.anchors)
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 0 }?.selectedLabels)
        assertEquals(listOf("B"), result.answerArea?.questions?.single { it.questionIndex == 1 }?.selectedLabels)
        assertEquals(listOf("C"), result.answerArea?.questions?.single { it.questionIndex == 5 }?.selectedLabels)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(listOf("A"), result.answerArea?.questions?.single { it.questionIndex == 15 }?.selectedLabels)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun cornerMatcherTimingDiagnosticsForLargeFrames() {
        val white1280 = whiteFrame(width = 1280, height = 960)
        val noise1280 = noisyFrame(width = 1280, height = 960)
        val template = templateForImageScan()
        val layout = TemplateGeometry.buildLayout(template)
        val scale = 1280f / TemplateGeometry.renderedWidth(layout)
        val productionTemplateFrame = filledProductionTemplateBitmap(template, scale = scale).toMiniProgramFrame()
        val white640 = whiteFrame(width = 640, height = 480)

        val timings = listOf(
            "white1280" to measureCornerMatch(white1280),
            "noise1280" to measureCornerMatch(noise1280),
            "productionTemplate${productionTemplateFrame.width}x${productionTemplateFrame.height}" to
                measureCornerMatch(productionTemplateFrame),
            "white640" to measureCornerMatch(white640),
        )

        timings.forEach { (name, timing) ->
            println("cornerTiming $name elapsedMs=${timing.elapsedMs} ${timing.debugInfo.joinToString(" ")}")
            assertTrue("$name took ${timing.elapsedMs}ms", timing.elapsedMs < 1_500)
        }
    }

    private fun filledProductionTemplateBitmap(template: TemplateState, scale: Float): Bitmap {
        val bitmap = TemplateRenderer.render(template, scale = scale)
        markProductionAdmissionNumber(bitmap, template, "1234", scale)
        markProductionAnswer(bitmap, template, questionNumber = 1, optionLabel = "A", scale = scale)
        markProductionAnswer(bitmap, template, questionNumber = 2, optionLabel = "B", scale = scale)
        markProductionAnswer(bitmap, template, questionNumber = 6, optionLabel = "C", scale = scale)
        markProductionAnswer(bitmap, template, questionNumber = 11, optionLabel = "D", scale = scale)
        markProductionAnswer(bitmap, template, questionNumber = 16, optionLabel = "A", scale = scale)
        return bitmap
    }

    private fun addSparseNoise(bitmap: Bitmap) {
        val points = listOf(
            120 to 210,
            760 to 460,
            1320 to 690,
            1540 to 920,
        )
        points.forEach { (column, row) ->
            for (dy in 0 until 3) {
                for (dx in 0 until 3) {
                    if (column + dx in 0 until bitmap.width && row + dy in 0 until bitmap.height) {
                        bitmap.setPixel(column + dx, row + dy, Color.BLACK)
                    }
                }
            }
        }
    }

    private fun drawIndependentAnchorsOnly(bitmap: Bitmap) {
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val size = 34f
        val thickness = 8f
        val left = 60f
        val right = 190f
        val top = 60f
        val bottom = 160f
        val canvas = Canvas(bitmap)
        canvas.drawRect(left, top, left + size, top + thickness, paint)
        canvas.drawRect(left, top, left + thickness, top + size, paint)
        canvas.drawRect(right - size + 1, top, right + 1, top + thickness, paint)
        canvas.drawRect(right - thickness + 1, top, right + 1, top + size, paint)
        canvas.drawRect(left, bottom - size + thickness, left + size, bottom + thickness, paint)
        canvas.drawRect(left, bottom, left + thickness, bottom + size, paint)
        canvas.drawRect(right - size + 1, bottom - size + thickness, right + 1, bottom + thickness, paint)
        canvas.drawRect(right - thickness + 1, bottom, right + 1, bottom + size, paint)
    }

    private fun drawFrameBorderAnchorsOnly(bitmap: Bitmap) {
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val size = 34f
        val thickness = 8f
        val left = 0f
        val right = bitmap.width - 1f
        val top = 0f
        val bottom = bitmap.height - size
        val canvas = Canvas(bitmap)
        canvas.drawRect(left, top, left + size, top + thickness, paint)
        canvas.drawRect(left, top, left + thickness, top + size, paint)
        canvas.drawRect(right - size + 1, top, right + 1, top + thickness, paint)
        canvas.drawRect(right - thickness + 1, top, right + 1, top + size, paint)
        canvas.drawRect(left, bottom - size + thickness, left + size, bottom + thickness, paint)
        canvas.drawRect(left, bottom, left + thickness, bottom + size, paint)
        canvas.drawRect(right - size + 1, bottom - size + thickness, right + 1, bottom + thickness, paint)
        canvas.drawRect(right - thickness + 1, bottom, right + 1, bottom + size, paint)
    }

    private fun markProductionAdmissionNumber(
        bitmap: Bitmap,
        template: TemplateState,
        digits: String,
        scale: Float,
    ) {
        val layout = TemplateGeometry.buildLayout(template)
        digits.forEachIndexed { digitIndex, char ->
            if (char.isDigit()) {
                markProductionRect(
                    bitmap = bitmap,
                    rect = TemplateGeometry.examIdDigitBox(layout, digitIndex, char.digitToInt()),
                    scale = scale,
                )
            }
        }
    }

    private fun markProductionAnswer(
        bitmap: Bitmap,
        template: TemplateState,
        questionNumber: Int,
        optionLabel: String,
        scale: Float,
    ) {
        val layout = TemplateGeometry.buildLayout(template)
        val rect = layout.options.single { it.question == questionNumber && it.option == optionLabel }.rect
        markProductionRect(bitmap = bitmap, rect = rect, scale = scale)
    }

    private fun markProductionRect(bitmap: Bitmap, rect: Rect, scale: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val insetX = rect.w * 0.18f
        val insetY = rect.h * 0.18f
        Canvas(bitmap).drawRect(
            (TemplateGeometry.PAGE_MARGIN + rect.x + insetX) * scale,
            (TemplateGeometry.PAGE_MARGIN + rect.y + insetY) * scale,
            (TemplateGeometry.PAGE_MARGIN + rect.x + rect.w - insetX) * scale,
            (TemplateGeometry.PAGE_MARGIN + rect.y + rect.h - insetY) * scale,
            paint,
        )
    }

    private fun templateForImageScan(): TemplateState =
        TemplateState(
            name = "stage 9B image scan",
            questions = (1..16).map { number ->
                val answer = when (number) {
                    1 -> "A"
                    2 -> "B"
                    6 -> "C"
                    11 -> "D"
                    16 -> "A"
                    else -> "A"
                }
                val score = if (number in listOf(1, 2, 6, 11, 16)) 2 else 0
                QuestionSetting(number = number, answer = answer, score = score)
            },
        )

    private fun whiteFrame(width: Int, height: Int): MiniProgramFrame =
        MiniProgramFrame(width = width, height = height, pixels = IntArray(width * height) { 255 })

    private fun noisyFrame(width: Int, height: Int): MiniProgramFrame =
        MiniProgramFrame(
            width = width,
            height = height,
            pixels = IntArray(width * height) { index ->
                val row = index / width
                val column = index % width
                210 + ((row * 31 + column * 17) % 32)
            },
        )

    private fun drawCornerAnchorOnFrame(
        frame: MiniProgramFrame,
        kind: MiniProgramCornerKind,
        row: Int,
        column: Int,
        armLength: Int,
        thickness: Int,
        value: Int,
    ) {
        when (kind) {
            MiniProgramCornerKind.LU -> {
                fillFrameRect(frame, row, column, thickness, armLength, value)
                fillFrameRect(frame, row, column, armLength, thickness, value)
            }
            MiniProgramCornerKind.LD -> {
                fillFrameRect(frame, row, column, thickness, armLength, value)
                fillFrameRect(frame, row - armLength + thickness, column, armLength, thickness, value)
            }
            MiniProgramCornerKind.RU -> {
                fillFrameRect(frame, row, column - armLength + 1, thickness, armLength, value)
                fillFrameRect(frame, row, column - armLength + 1, armLength, thickness, value)
            }
            MiniProgramCornerKind.RD -> {
                fillFrameRect(frame, row, column - armLength + 1, thickness, armLength, value)
                fillFrameRect(frame, row - armLength + thickness, column - armLength + 1, armLength, thickness, value)
            }
        }
    }

    private fun fillFrameRect(
        frame: MiniProgramFrame,
        row: Int,
        column: Int,
        height: Int,
        width: Int,
        value: Int,
    ) {
        for (y in row.coerceAtLeast(0) until (row + height).coerceAtMost(frame.height)) {
            for (x in column.coerceAtLeast(0) until (column + width).coerceAtMost(frame.width)) {
                frame.pixels[y * frame.width + x] = value
            }
        }
    }

    private data class CornerTiming(
        val elapsedMs: Long,
        val debugInfo: List<String>,
    )

    private fun measureCornerMatch(frame: MiniProgramFrame): CornerTiming {
        val startedAt = System.nanoTime()
        val result = CornerAnchorMatcher.findAnchorsWithDiagnostics(frame)
        return CornerTiming(
            elapsedMs = (System.nanoTime() - startedAt) / 1_000_000,
            debugInfo = result.diagnostics.debugInfo(),
        )
    }

    private fun writeDebugPng(bitmap: Bitmap, name: String) {
        val directory = File("build/omr-debug").apply { mkdirs() }
        FileOutputStream(File(directory, name)).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun Bitmap.toMiniProgramFrame(): MiniProgramFrame {
        val pixels = IntArray(width * height)
        for (row in 0 until height) {
            for (column in 0 until width) {
                val color = getPixel(column, row)
                pixels[row * width + column] = (
                    Color.red(color) * 299 +
                        Color.green(color) * 587 +
                        Color.blue(color) * 114
                    ) / 1000
            }
        }
        return MiniProgramFrame(width = width, height = height, pixels = pixels)
    }

    private fun findDesktopReferenceCard(): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "desktop-reference-card.png")
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        return File("desktop-reference-card.png")
    }

    private fun findWechatPhoto(): File {
        val fileName = "微信图片_20260707164730_464_10.png"
        var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (directory != null) {
            val candidates = listOf(File(directory, "images/$fileName"), File(directory, fileName))
            candidates.firstOrNull { it.isFile }?.let { return it }
            directory = directory.parentFile
        }
        return File(fileName)
    }

}
