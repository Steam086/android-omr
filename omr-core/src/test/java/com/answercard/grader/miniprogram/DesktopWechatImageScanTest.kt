package com.answercard.grader.miniprogram

import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopWechatImageScanTest {
    @Test
    fun scanWechatPhotoWithJvmImageLoader() {
        val file = findRootFile("微信图片_20260707164730_464_10.png")
        assertTrue("wechat photo should exist for desktop regression", file.isFile)

        val result = AndroidOmrEngine.scan(
            frame = loadImageAsFrame(file),
            template = sampleTemplate(),
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
        assertEdgeRefinementReported(result)
    }

    @Test
    fun scanWechatPhotoAtAnalysisResolution() {
        val file = findRootFile("微信图片_20260707164730_464_10.png")
        assertTrue("wechat photo should exist for desktop regression", file.isFile)

        val result = AndroidOmrEngine.scan(
            frame = downscaleToWidth(loadImageAsFrame(file), 1280),
            template = sampleTemplate(),
            anchorMode = AnchorMode.LEGACY,
        )

        assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
        assertEdgeRefinementReported(result)
    }

    @Test
    fun scanAllSolidMarkerPhotos() {
        val files = solidMarkerFiles()
        assumeTrue("server solid-marker photos should be available for desktop regression", files.all { it.isFile })

        files.forEach { file ->
            assertSolidMarkerScan(file, loadImageAsFrame(file))
        }
    }

    @Test
    fun scanAllSolidMarkerPhotosAtAnalysisResolution() {
        val files = solidMarkerFiles()
        assumeTrue("server solid-marker photos should be available for desktop regression", files.all { it.isFile })

        files.forEach { file ->
            assertSolidMarkerScan(file, downscaleToWidth(loadImageAsFrame(file), 1280))
        }
    }

    private fun sampleTemplate(): TemplateState =
        TemplateState(
            name = "desktop wechat regression",
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

    private fun solidMarkerTemplate(): TemplateState =
        TemplateState(
            name = "desktop solid marker regression",
            questions = (1..16).map { number ->
                QuestionSetting(
                    number = number,
                    answer = "A",
                    score = if (number in listOf(1, 7, 15)) 2 else 0,
                )
            },
        )

    private fun assertSolidMarkerScan(file: File, frame: MiniProgramFrame) {
        val result = AndroidOmrEngine.scan(
            frame = frame,
            template = solidMarkerTemplate(),
            anchorMode = AnchorMode.LEGACY,
        )

        assertTrue("${file.name}: ${result.failureReason ?: result.debugInfo.joinToString()}", result.success)
        assertEquals("${file.name}: admission", "1233", result.admissionNumber?.digits)
        assertTrue("${file.name}: marker path", result.debugInfo.contains("anchorPath=solid-marker"))
        assertEdgeRefinementReported(result, file.name)
        result.answerArea!!.questions.forEach { question ->
            val expected = if (question.questionIndex in listOf(0, 6, 14)) listOf("A") else emptyList()
            assertEquals("${file.name}: Q${question.questionIndex + 1}", expected, question.selectedLabels)
        }
    }

    private fun assertEdgeRefinementReported(result: AndroidOmrResult, label: String = "wechat photo") {
        assertTrue(
            "$label: edge refinement diagnostics missing: ${result.debugInfo.joinToString()}",
            result.debugInfo.any { it == "edgeRefinement=active" || it == "edgeRefinement=fallback" },
        )
    }

    private fun solidMarkerFiles(): List<File> = listOf(
        "微信图片_20260709175934_784_10.jpg",
        "微信图片_20260709175936_785_10.jpg",
        "微信图片_20260709175937_786_10.jpg",
        "微信图片_20260709175939_787_10.jpg",
    ).map(::findRootFile)

    private fun loadImageAsFrame(file: File): MiniProgramFrame {
        val image = ImageIO.read(file)
        val pixels = IntArray(image.width * image.height)
        for (row in 0 until image.height) {
            for (column in 0 until image.width) {
                val rgb = image.getRGB(column, row)
                val red = rgb ushr 16 and 0xff
                val green = rgb ushr 8 and 0xff
                val blue = rgb and 0xff
                pixels[row * image.width + column] = (red * 299 + green * 587 + blue * 114) / 1000
            }
        }
        return MiniProgramFrame(width = image.width, height = image.height, pixels = pixels)
    }

    private fun downscaleToWidth(frame: MiniProgramFrame, targetWidth: Int): MiniProgramFrame {
        val scale = frame.width.toDouble() / targetWidth
        val targetHeight = (frame.height / scale).roundToInt()
        val pixels = IntArray(targetWidth * targetHeight)
        for (row in 0 until targetHeight) {
            val rowStart = (row * scale).toInt()
            val rowEnd = (((row + 1) * scale).toInt()).coerceAtMost(frame.height).coerceAtLeast(rowStart + 1)
            for (column in 0 until targetWidth) {
                val columnStart = (column * scale).toInt()
                val columnEnd = (((column + 1) * scale).toInt()).coerceAtMost(frame.width).coerceAtLeast(columnStart + 1)
                var sum = 0L
                var count = 0
                for (r in rowStart until rowEnd) {
                    for (c in columnStart until columnEnd) {
                        sum += frame.pixels[r * frame.width + c]
                        count += 1
                    }
                }
                pixels[row * targetWidth + column] = (sum / count).toInt()
            }
        }
        return MiniProgramFrame(width = targetWidth, height = targetHeight, pixels = pixels)
    }

    private fun findRootFile(fileName: String): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (directory != null) {
            val candidates = listOf(File(directory, "images/$fileName"), File(directory, fileName))
            candidates.firstOrNull { it.isFile }?.let { return it }
            directory = directory.parentFile
        }
        return File(fileName)
    }
}
