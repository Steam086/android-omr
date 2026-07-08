package com.answercard.grader.miniprogram

import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplatePoint
import com.answercard.grader.template.TemplateState
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diagnostic probe: traces every dense component's inverted template-space center for the
 * downscaled sample photo and reports which option rect claims it (and by how much),
 * to explain the Q11 D->C flip at camera analysis resolution.
 */
class DesktopQ11FlipProbeTest {
    @Test
    fun traceInvertedComponentCenters() {
        val file = findRootFile("微信图片_20260707164730_464_10.png")
        assertTrue(file.isFile)
        val full = loadImageAsFrame(file)
        val template = sampleTemplate()
        val cardLayout = TemplateGeometry.buildLayout(template)

        val report = StringBuilder()
        listOf(
            "full-res" to full,
            "ds1280" to downscaleToWidth(full, 1280),
        ).forEach { (name, frame) ->
            report.appendLine("=== $name (${frame.width}x${frame.height}) ===")
            val anchors = CornerAnchorMatcher.findAnchorsWithDiagnostics(
                frame = frame,
                expectedAspectRatio = TemplateGeometry.renderedWidth(cardLayout).toDouble() /
                    TemplateGeometry.renderedHeight(cardLayout).toDouble(),
            ).anchors
            if (anchors == null) {
                report.appendLine("anchors not found")
                return@forEach
            }
            listOf("lu" to anchors.lu, "ld" to anchors.ld, "ru" to anchors.ru, "rd" to anchors.rd)
                .forEach { (label, candidate) ->
                    report.appendLine(
                        "anchor $label: (${candidate.point.column},${candidate.point.row}) source=${candidate.source}",
                    )
                }

            val reference = TemplateGeometry.cornerAnchorReference(cardLayout)
            val outer = listOf(reference.lu, reference.ld, reference.ru, reference.rd)
            val adjustedRight = reference.ru.x -
                (TemplateGeometry.CORNER_BRACKET_SIZE - TemplateGeometry.CORNER_BRACKET_THICKNESS)
            val innerRight = listOf(
                reference.lu,
                reference.ld,
                TemplatePoint(adjustedRight, reference.ru.y),
                TemplatePoint(adjustedRight, reference.rd.y),
            )

            val components = denseComponents(frame)
            report.appendLine("denseComponents=${components.size}")
            components.forEach { component ->
                val centerColumn = (component.left + component.right - 1) / 2.0
                val centerRow = (component.top + component.bottom - 1) / 2.0
                listOf("outer" to outer, "innerRight" to innerRight).forEach { (refName, ref) ->
                    val point = invert(centerRow, centerColumn, anchors, ref)
                    if (point != null) {
                        val hits = cardLayout.options.filter { option ->
                            val rect = TemplateGeometry.renderedRect(option.rect)
                            point.x >= rect.x - 4f && point.x <= rect.x + rect.w + 4f &&
                                point.y >= rect.y - 4f && point.y <= rect.y + rect.h + 4f
                        }
                        if (hits.isNotEmpty()) {
                            val labels = hits.joinToString(",") { "Q${it.question}${it.option}" }
                            val first = TemplateGeometry.renderedRect(hits.first().rect)
                            val insideFirst = point.x >= first.x && point.x <= first.x + first.w
                            report.appendLine(
                                "  comp@px(${format(centerColumn)},${format(centerRow)}) $refName -> " +
                                    "tpl(${format(point.x.toDouble())},${format(point.y.toDouble())}) " +
                                    "hits=[$labels] firstRectX=${first.x}..${first.x + first.w} insideFirstX=$insideFirst",
                            )
                        }
                    }
                }
            }
            val q11 = cardLayout.options.filter { it.question == 11 }
            q11.forEach { option ->
                val rect = TemplateGeometry.renderedRect(option.rect)
                report.appendLine("Q11 ${option.option}: x=${rect.x}..${rect.x + rect.w} y=${rect.y}..${rect.y + rect.h}")
            }
        }

        val out = File("build/reports/q11-flip-probe.txt")
        out.parentFile.mkdirs()
        out.writeText(report.toString())
        println(report)
    }

    private fun denseComponents(frame: MiniProgramFrame): List<Box> {
        val threshold = MiniProgramGeometry.threshold(frame)
        val mask = BooleanArray(frame.width * frame.height) { frame.pixels[it] < threshold }
        val minArea = maxOf(80, (frame.width * frame.height * 0.0001).toInt())
        val minSize = maxOf(8, (minOf(frame.width, frame.height) * 0.008).toInt())
        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        val boxes = mutableListOf<Box>()
        for (start in mask.indices) {
            if (visited[start] || !mask[start]) {
                visited[start] = true
                continue
            }
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var minRow = start / frame.width
            var maxRow = minRow
            var minColumn = start % frame.width
            var maxColumn = minColumn
            var area = 0
            while (head < tail) {
                val index = queue[head++]
                val row = index / frame.width
                val column = index % frame.width
                area += 1
                minRow = minOf(minRow, row)
                maxRow = maxOf(maxRow, row)
                minColumn = minOf(minColumn, column)
                maxColumn = maxOf(maxColumn, column)
                for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    val r = row + dr
                    val c = column + dc
                    if (r !in 0 until frame.height || c !in 0 until frame.width) continue
                    val neighbor = r * frame.width + c
                    if (visited[neighbor] || !mask[neighbor]) {
                        visited[neighbor] = true
                        continue
                    }
                    visited[neighbor] = true
                    queue[tail++] = neighbor
                }
            }
            val box = Box(left = minColumn, top = minRow, right = maxColumn + 1, bottom = maxRow + 1, area = area)
            val fill = area.toFloat() / ((box.right - box.left) * (box.bottom - box.top)).coerceAtLeast(1)
            if (
                area >= minArea &&
                box.right - box.left >= minSize &&
                box.bottom - box.top >= minSize &&
                fill >= 0.82f &&
                box.left > 0 && box.top > 0 && box.right < frame.width && box.bottom < frame.height
            ) {
                boxes += box
            }
        }
        return boxes
    }

    private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int, val area: Int)

    private fun invert(
        targetRow: Double,
        targetColumn: Double,
        anchors: MiniProgramAnchors,
        source: List<TemplatePoint>,
    ): TemplatePoint? {
        var rowRatio = 0.5
        var columnRatio = 0.5
        repeat(16) {
            val projected = interpolate(anchors, rowRatio, columnRatio)
            val rowError = targetRow - projected.row
            val columnError = targetColumn - projected.column
            if (abs(rowError) + abs(columnError) < 0.001) return@repeat
            val d = 0.0001
            val rowStep = interpolate(anchors, rowRatio + d, columnRatio)
            val columnStep = interpolate(anchors, rowRatio, columnRatio + d)
            val j00 = (rowStep.row - projected.row) / d
            val j10 = (rowStep.column - projected.column) / d
            val j01 = (columnStep.row - projected.row) / d
            val j11 = (columnStep.column - projected.column) / d
            val determinant = j00 * j11 - j01 * j10
            if (abs(determinant) < 1e-9) return null
            rowRatio += (rowError * j11 - j01 * columnError) / determinant
            columnRatio += (j00 * columnError - rowError * j10) / determinant
        }
        if (!rowRatio.isFinite() || !columnRatio.isFinite()) return null
        val x = source[0].x + (source[2].x - source[0].x) * columnRatio
        val y = source[0].y + (source[1].y - source[0].y) * rowRatio
        return TemplatePoint(x.toFloat(), y.toFloat())
    }

    private fun interpolate(anchors: MiniProgramAnchors, rowRatio: Double, columnRatio: Double): MiniProgramGridPoint =
        MiniProgramGridBuilder.interpolate(
            lu = MiniProgramGridPoint(anchors.lu.point.row.toDouble(), anchors.lu.point.column.toDouble()),
            ld = MiniProgramGridPoint(anchors.ld.point.row.toDouble(), anchors.ld.point.column.toDouble()),
            ru = MiniProgramGridPoint(anchors.ru.point.row.toDouble(), anchors.ru.point.column.toDouble()),
            rd = MiniProgramGridPoint(anchors.rd.point.row.toDouble(), anchors.rd.point.column.toDouble()),
            rowRatio = rowRatio,
            columnRatio = columnRatio,
        )

    private fun sampleTemplate(): TemplateState =
        TemplateState(
            name = "q11 flip probe",
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

    private fun format(value: Double): String = "%.1f".format(Locale.US, value)

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
