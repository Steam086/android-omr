package com.answercard.grader.miniprogram

import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import java.io.File
import java.util.Random
import javax.imageio.ImageIO
import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diagnostic probe (not a regression test): simulates camera-like frame variations on the
 * known-good sample photo and reports which pipeline stage becomes unstable.
 * Writes a report to build/reports/camera-instability-probe.txt.
 */
class DesktopCameraInstabilityProbeTest {
    @Test
    fun stronglyOverexposedLegacyFrameNeverOutputsScore() {
        val file = findRootFile("微信图片_20260707164730_464_10.png")
        assertTrue("sample photo should exist", file.isFile)
        val frame = mapPixels(downscaleToWidth(loadImageAsFrame(file), 1280)) { it + 40 }

        val result = AndroidOmrEngine.scan(
            frame = frame,
            template = sampleTemplate(),
            anchorMode = AnchorMode.LEGACY,
        )

        assertFalse(result.debugInfo.joinToString(), result.success)
        assertNull("unsafe frames must never expose a score", result.score)
        assertTrue(
            result.rejectionReason in setOf(
                ScanRejectionReason.RETAKE_EXPOSURE,
                ScanRejectionReason.LEGACY_ANCHOR_AMBIGUOUS,
                ScanRejectionReason.RETAKE_LEGACY_MARKERS,
                ScanRejectionReason.RETAKE_CARD_GEOMETRY,
            ),
        )
    }

    @Test
    fun blurredLegacyFrameWithUnstableAnswersNeverOutputsScore() {
        val file = findRootFile("微信图片_20260707164730_464_10.png")
        assertTrue("sample photo should exist", file.isFile)
        val frame = boxBlur(downscaleToWidth(loadImageAsFrame(file), 1280), radius = 2)

        val result = AndroidOmrEngine.scan(
            frame = frame,
            template = sampleTemplate(),
            anchorMode = AnchorMode.LEGACY,
        )

        assertFalse(result.debugInfo.joinToString(), result.success)
        assertNull("strongly blurred frames must never expose a score", result.score)
        assertTrue(result.debugInfo.joinToString(), result.rejectionReason == ScanRejectionReason.RETAKE_BLUR)
    }

    @Test
    fun probeCameraLikePerturbations() {
        val file = findRootFile("微信图片_20260707164730_464_10.png")
        assertTrue("sample photo should exist", file.isFile)
        val full = loadImageAsFrame(file)
        val template = sampleTemplate()

        val base = downscaleToWidth(full, 1280)
        val variants = buildList {
            add("full-res" to full)
            add("ds1280" to base)
            add("ds960" to downscaleToWidth(full, 960))
            for (delta in listOf(-40, -20, 20, 40)) {
                add("ds1280 bright%+d".format(delta) to mapPixels(base) { (it + delta) })
            }
            for (g in listOf(0.8, 1.25)) {
                add("ds1280 gamma$g" to gamma(base, g))
            }
            for (r in listOf(1, 2, 3, 4)) {
                add("ds1280 blur$r" to boxBlur(base, r))
            }
            for (seed in 1..5) {
                add("ds1280 noise3 seed$seed" to gaussianNoise(base, 3.0, seed.toLong()))
            }
            for (seed in 1..5) {
                add("ds1280 blur1+noise3 s$seed" to gaussianNoise(boxBlur(base, 1), 3.0, seed.toLong()))
            }
            add("ds1280 hramp-25" to horizontalRamp(base, -25))
            add("ds1280 hramp+25" to horizontalRamp(base, 25))
            add("ds1280 vramp-25" to verticalRamp(base, -25))
            add("ds1280 cornerShadow35" to cornerShadow(base, 35))
        }

        val report = StringBuilder()
        report.appendLine(
            "variant | thr | ok | score | admission | answers | sharpness | exposure | comps | ref | qMarks | aMarks | failure",
        )
        variants.forEach { (name, frame) ->
            val threshold = MiniProgramGeometry.threshold(frame)
            val result = AndroidOmrEngine.scan(
                frame = frame,
                template = template,
                anchorMode = AnchorMode.LEGACY,
            )
            val answers = result.answerArea?.questions
                ?.sortedBy { it.questionIndex }
                ?.joinToString("") { q -> if (q.selectedLabels.isEmpty()) "-" else q.selectedLabels.joinToString("") }
                ?: "?"
            val debug = result.debugInfo
            fun tag(prefix: String): String =
                debug.lastOrNull { it.startsWith(prefix) }?.substringAfter("=") ?: "?"
            report.appendLine(
                listOf(
                    name,
                    threshold,
                    result.success,
                    result.score?.let { "${it.totalScore}/${it.maxScore}" } ?: "-",
                    result.admissionNumber?.digits ?: "-",
                    answers,
                    tag("cardNormalizedLaplacianVariance"),
                    tag("cardHighlightClipRatio"),
                    tag("solidMarkComponents"),
                    tag("solidMarkReference"),
                    tag("solidQuestionMarks"),
                    tag("solidAdmissionMarks"),
                    result.failureReason ?: "-",
                ).joinToString(" | "),
            )
        }

        val out = File("build/reports/camera-instability-probe.txt")
        out.parentFile.mkdirs()
        out.writeText(report.toString())
        println(report)
    }

    private fun sampleTemplate(): TemplateState =
        TemplateState(
            name = "camera instability probe",
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

    private fun downscaleToWidth(frame: MiniProgramFrame, targetWidth: Int): MiniProgramFrame {
        val scale = frame.width.toDouble() / targetWidth
        val targetHeight = (frame.height / scale).roundToInt()
        val pixels = IntArray(targetWidth * targetHeight)
        for (row in 0 until targetHeight) {
            val srcRowStart = (row * scale).toInt()
            val srcRowEnd = (((row + 1) * scale).toInt()).coerceAtMost(frame.height).coerceAtLeast(srcRowStart + 1)
            for (column in 0 until targetWidth) {
                val srcColStart = (column * scale).toInt()
                val srcColEnd = (((column + 1) * scale).toInt()).coerceAtMost(frame.width).coerceAtLeast(srcColStart + 1)
                var sum = 0L
                var count = 0
                for (r in srcRowStart until srcRowEnd) {
                    for (c in srcColStart until srcColEnd) {
                        sum += frame.pixels[r * frame.width + c]
                        count += 1
                    }
                }
                pixels[row * targetWidth + column] = (sum / count).toInt()
            }
        }
        return MiniProgramFrame(width = targetWidth, height = targetHeight, pixels = pixels)
    }

    private fun mapPixels(frame: MiniProgramFrame, transform: (Int) -> Int): MiniProgramFrame =
        MiniProgramFrame(
            width = frame.width,
            height = frame.height,
            pixels = IntArray(frame.pixels.size) { transform(frame.pixels[it]).coerceIn(0, 255) },
        )

    private fun gamma(frame: MiniProgramFrame, g: Double): MiniProgramFrame =
        mapPixels(frame) { value -> (255.0 * (value / 255.0).pow(g)).roundToInt() }

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
        return mapPixels(frame) { value -> (value + random.nextGaussian() * sigma).roundToInt() }
    }

    private fun horizontalRamp(frame: MiniProgramFrame, maxDelta: Int): MiniProgramFrame {
        val pixels = IntArray(frame.pixels.size)
        for (row in 0 until frame.height) {
            for (column in 0 until frame.width) {
                val delta = maxDelta * column / frame.width
                pixels[row * frame.width + column] =
                    (frame.pixels[row * frame.width + column] + delta).coerceIn(0, 255)
            }
        }
        return MiniProgramFrame(width = frame.width, height = frame.height, pixels = pixels)
    }

    private fun verticalRamp(frame: MiniProgramFrame, maxDelta: Int): MiniProgramFrame {
        val pixels = IntArray(frame.pixels.size)
        for (row in 0 until frame.height) {
            val delta = maxDelta * row / frame.height
            for (column in 0 until frame.width) {
                pixels[row * frame.width + column] =
                    (frame.pixels[row * frame.width + column] + delta).coerceIn(0, 255)
            }
        }
        return MiniProgramFrame(width = frame.width, height = frame.height, pixels = pixels)
    }

    private fun cornerShadow(frame: MiniProgramFrame, maxDelta: Int): MiniProgramFrame {
        val pixels = IntArray(frame.pixels.size)
        val maxDistance = Math.hypot(frame.width / 2.0, frame.height / 2.0)
        for (row in 0 until frame.height) {
            for (column in 0 until frame.width) {
                val distance = Math.hypot(column - frame.width / 2.0, row - frame.height / 2.0)
                val delta = (maxDelta * distance / maxDistance).roundToInt()
                pixels[row * frame.width + column] =
                    (frame.pixels[row * frame.width + column] - delta).coerceIn(0, 255)
            }
        }
        return MiniProgramFrame(width = frame.width, height = frame.height, pixels = pixels)
    }

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
            val candidates = listOf(File(directory, "images/$fileName"), File(directory, fileName))
            candidates.firstOrNull { it.isFile }?.let { return it }
            directory = directory.parentFile
        }
        return File(fileName)
    }
}
