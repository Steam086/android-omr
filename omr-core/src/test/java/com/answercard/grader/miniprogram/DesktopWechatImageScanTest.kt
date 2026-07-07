package com.answercard.grader.miniprogram

import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopWechatImageScanTest {
    @Test
    fun scanWechatPhotoWithJvmImageLoader() {
        val file = findRootFile("微信图片_20260707164730_464_10.png")
        assertTrue("wechat photo should exist for desktop regression", file.isFile)

        val result = AndroidOmrEngine.scan(
            frame = loadImageAsFrame(file),
            template = sampleTemplate(),
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

    private fun findRootFile(fileName: String): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (directory != null) {
            val candidate = File(directory, fileName)
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        return File(fileName)
    }
}
