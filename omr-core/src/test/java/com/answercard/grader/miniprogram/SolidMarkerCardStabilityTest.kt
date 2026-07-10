package com.answercard.grader.miniprogram

import com.answercard.grader.template.CornerMarkerStyle
import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import java.util.Random
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for camera-frame score stability: the same square-marker card,
 * downscaled to analysis resolution with blur and per-frame sensor noise,
 * must yield the same correct score on every frame that succeeds — and most
 * frames must succeed.
 */
class SolidMarkerCardStabilityTest {
    @Test
    fun noisyBlurredFramesAgreeOnTheCorrectScore() {
        assertNoisyFramesStayStable(CornerMarkerStyle.SOLID_SQUARE, "anchorPath=solid-marker")
    }

    @Test
    fun codedMarkersStayDecodableInNoisyBlurredFrames() {
        assertNoisyFramesStayStable(CornerMarkerStyle.CODED, "anchorPath=coded-marker")
    }

    private fun assertNoisyFramesStayStable(markerStyle: CornerMarkerStyle, expectedAnchorPath: String) {
        val template = TemplateState(
            name = "stability",
            questions = (1..16).map { number ->
                val answer = when (number) {
                    1 -> "A"
                    2 -> "B"
                    6 -> "C"
                    11 -> "D"
                    16 -> "A"
                    else -> "A"
                }
                QuestionSetting(number = number, answer = answer, score = if (number in listOf(1, 2, 6, 11, 16)) 2 else 0)
            },
        )
        val renderer = DesktopTemplateCardRenderer(template, scale = 3f, markerStyle = markerStyle)
        renderer.markAnswer(1, "A")
        renderer.markAnswer(2, "B")
        renderer.markAnswer(6, "C")
        renderer.markAnswer(11, "D")
        renderer.markAnswer(16, "A")
        renderer.markAdmissionNumber("1234")
        val base = boxBlur(downscaleToWidth(renderer.frame(), 1280), radius = 1)

        var successes = 0
        for (seed in 1..8) {
            val frame = gaussianNoise(base, sigma = 3.0, seed = seed.toLong())
            val mode = if (markerStyle == CornerMarkerStyle.CODED) AnchorMode.CODED_ONLY else AnchorMode.LEGACY
            val result = AndroidOmrEngine.scan(frame, template, mode)
            if (!result.success) continue
            successes += 1
            assertTrue("seed $seed path", result.debugInfo.contains(expectedAnchorPath))
            assertEquals("seed $seed admission", "1234", result.admissionNumber?.digits)
            assertEquals("seed $seed score", 10.0, result.score?.totalScore ?: -1.0, 0.0)
        }
        assertTrue("at least 6 of 8 noisy frames should scan, got $successes", successes >= 6)
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

    private fun boxBlur(frame: MiniProgramFrame, radius: Int): MiniProgramFrame {
        val pixels = IntArray(frame.pixels.size)
        for (row in 0 until frame.height) {
            for (column in 0 until frame.width) {
                var sum = 0
                var count = 0
                for (dr in -radius..radius) {
                    for (dc in -radius..radius) {
                        val r = row + dr
                        val c = column + dc
                        if (r in 0 until frame.height && c in 0 until frame.width) {
                            sum += frame.pixels[r * frame.width + c]
                            count += 1
                        }
                    }
                }
                pixels[row * frame.width + column] = sum / count
            }
        }
        return MiniProgramFrame(width = frame.width, height = frame.height, pixels = pixels)
    }

    private fun gaussianNoise(frame: MiniProgramFrame, sigma: Double, seed: Long): MiniProgramFrame {
        val random = Random(seed)
        return MiniProgramFrame(
            width = frame.width,
            height = frame.height,
            pixels = IntArray(frame.pixels.size) { index ->
                (frame.pixels[index] + random.nextGaussian() * sigma).roundToInt().coerceIn(0, 255)
            },
        )
    }
}
