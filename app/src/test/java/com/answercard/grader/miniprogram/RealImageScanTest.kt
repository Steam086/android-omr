package com.answercard.grader.miniprogram

import com.answercard.grader.template.TemplateCollection
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealImageScanTest {
    private val imagePath = "D:\\desk\\保存文件\\1783434586643.png"

    @Test
    fun scanRealImage() {
        val file = File(imagePath)
        if (!file.exists()) {
            println("Image not found at $imagePath, skipping")
            return
        }
        val template = TemplateCollection.default().selectedTemplate.template
        val frame = AndroidOmrRenderedImageFactory.loadPngAsFrame(file)
        println("Image size: ${frame.width}x${frame.height}")

        val result = AndroidOmrEngine.scan(frame = frame, template = template)

        println("=== Scan Result ===")
        println("success: ${result.success}")
        println("failureReason: ${result.failureReason}")
        println("admissionNumber: ${result.admissionNumber?.digits}")
        println("score: ${result.score?.totalScore}/${result.score?.maxScore}")
        println("warnings: ${result.warnings}")
        println("=== Debug Info ===")
        result.debugInfo.forEach { println(it) }
        println("=== Answer Area ===")
        result.answerArea?.questions?.forEach { q ->
            println("Q${q.questionIndex + 1}: selected=${q.selectedLabels} multi=${q.isMultiMarked} blank=${q.isBlank}")
        }

        assertTrue(result.success)
        assertEquals("1234", result.admissionNumber?.digits)
    }

    @Test
    fun dumpImageAsAsciiArt() {
        val file = File(imagePath)
        if (!file.exists()) {
            println("Image not found at $imagePath, skipping")
            return
        }
        val frame = AndroidOmrRenderedImageFactory.loadPngAsFrame(file)
        val targetWidth = 200
        val targetHeight = 110
        val stepX = frame.width / targetWidth
        val stepY = frame.height / targetHeight
        println("=== ASCII Art (threshold=128) size=${frame.width}x${frame.height} step=${stepX}x${stepY} ===")
        for (row in 0 until targetHeight) {
            val sb = StringBuilder()
            for (col in 0 until targetWidth) {
                val srcRow = row * stepY
                val srcCol = col * stepX
                val value = frame[srcRow, srcCol]
                sb.append(if (value < 128) '#' else ' ')
            }
            println(String.format("%4d %s", row * stepY, sb.toString()))
        }
    }

    @Test
    fun dumpAnchorRegions() {
        val file = File(imagePath)
        if (!file.exists()) {
            println("Image not found at $imagePath, skipping")
            return
        }
        val frame = AndroidOmrRenderedImageFactory.loadPngAsFrame(file)
        val template = TemplateCollection.default().selectedTemplate.template
        val result = AndroidOmrEngine.scan(frame = frame, template = template)
        val anchors = result.anchors ?: run {
            println("No anchors found")
            return
        }
        println("=== Anchor Regions (60x60 around each anchor, threshold=128) ===")
        val positions = listOf(
            "LU" to anchors.lu.point,
            "RU" to anchors.ru.point,
            "LD" to anchors.ld.point,
            "RD" to anchors.rd.point,
        )
        positions.forEach { (name, point) ->
            println("--- $name at row=${point.row}, col=${point.column} ---")
            val startRow = (point.row - 30).coerceAtLeast(0)
            val startCol = (point.column - 30).coerceAtLeast(0)
            for (r in startRow until minOf(startRow + 60, frame.height)) {
                val sb = StringBuilder()
                for (c in startCol until minOf(startCol + 60, frame.width)) {
                    sb.append(if (frame[r, c] < 128) '#' else ' ')
                }
                println(sb.toString())
            }
        }
    }

    @Test
    fun dumpAllCandidates() {
        val file = File(imagePath)
        if (!file.exists()) {
            println("Image not found at $imagePath, skipping")
            return
        }
        val frame = AndroidOmrRenderedImageFactory.loadPngAsFrame(file)
        val match = CornerAnchorMatcher.findAnchorsWithDiagnostics(frame)
        println("=== Component candidates by kind ===")
        for (kind in MiniProgramCornerKind.values()) {
            val candidates = CornerAnchorMatcher.findCandidatesForTest(frame, kind)
            println("--- $kind: ${candidates.size} candidates ---")
            candidates.sortedByDescending { it.length }.take(15).forEach { c ->
                println("  row=${c.point.row}, col=${c.point.column}, length=${c.length}, source=${c.source}")
            }
        }
    }
}
