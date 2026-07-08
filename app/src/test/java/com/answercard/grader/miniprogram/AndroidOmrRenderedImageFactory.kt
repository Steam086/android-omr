package com.answercard.grader.miniprogram

import com.answercard.grader.template.TemplateState
import java.io.File
import javax.imageio.ImageIO

class AndroidOmrRenderedImageFactory(
    val template: TemplateState,
    private val cellSize: Int = DEFAULT_CELL_SIZE,
    private val margin: Int = DEFAULT_MARGIN,
    backgroundGray: Int = 255,
) {
    val layout: AndroidPaperTemplateLayout = AndroidPaperTemplateBuilder.build(
        questionOptionCounts = template.questions.map { it.optionCount },
        admissionNumberDigits = if (template.showHeader) template.examIdDigits else 0,
    )
    val frameWidth: Int = margin * 2 + layout.gridColumns * cellSize + 1
    val frameHeight: Int = margin * 2 + layout.gridRows * cellSize + 1
    private val pixels = IntArray(frameWidth * frameHeight) { backgroundGray.coerceIn(0, 255) }

    fun markAdmissionNumber(digits: String) {
        digits.forEachIndexed { digitIndex, char ->
            if (char.isDigit()) markAdmissionDigit(digitIndex, char.digitToInt())
        }
    }

    fun markAdmissionDigit(
        digitIndex: Int,
        numberValue: Int,
        markSize: Int = DEFAULT_MARK_SIZE,
        gray: Int = 0,
    ) {
        val mapping = layout.admissionNumberMappings.single {
            it.digitIndex == digitIndex && it.numberValue == numberValue
        }
        markCell(mapping.row, mapping.column, markSize, gray)
    }

    fun markAnswer(
        questionIndex: Int,
        optionIndex: Int,
        markSize: Int = DEFAULT_MARK_SIZE,
        gray: Int = 0,
    ) {
        val mapping = layout.questionMappings.single {
            it.questionIndex == questionIndex && it.optionIndex == optionIndex
        }
        markCell(mapping.row, mapping.column, markSize, gray)
    }

    fun addSparseNoise() {
        val points = listOf(
            margin + 2 * cellSize to margin + 2 * cellSize,
            margin + 7 * cellSize + 3 to margin + 8 * cellSize + 5,
            margin + 4 * cellSize + 9 to margin + 10 * cellSize + 4,
        )
        points.forEach { (row, column) -> setPixel(row, column, 0) }
    }

    fun frame(
        skewTopRightDown: Int = 0,
        skewBottomRightUp: Int = 0,
    ): MiniProgramFrame {
        drawCornerAnchors(skewTopRightDown = skewTopRightDown, skewBottomRightUp = skewBottomRightUp)
        return MiniProgramFrame(width = frameWidth, height = frameHeight, pixels = pixels.copyOf())
    }

    private fun drawCornerAnchors(
        skewTopRightDown: Int,
        skewBottomRightUp: Int,
    ) {
        val top = margin
        val left = margin
        val right = margin + layout.gridColumns * cellSize
        val bottom = margin + layout.gridRows * cellSize
        drawTopLeftAnchor(top, left)
        drawTopRightAnchor(top + skewTopRightDown, right)
        drawBottomLeftAnchor(bottom, left)
        drawBottomRightAnchor(bottom - skewBottomRightUp, right)
    }

    private fun drawTopLeftAnchor(row: Int, column: Int) {
        fillRect(row, column, ANCHOR_THICKNESS, ANCHOR_SIZE, 0)
        fillRect(row, column, ANCHOR_SIZE, ANCHOR_THICKNESS, 0)
    }

    private fun drawTopRightAnchor(row: Int, column: Int) {
        fillRect(row, column - ANCHOR_SIZE + 1, ANCHOR_THICKNESS, ANCHOR_SIZE, 0)
        fillRect(row, column - ANCHOR_THICKNESS + 1, ANCHOR_SIZE, ANCHOR_THICKNESS, 0)
    }

    private fun drawBottomLeftAnchor(row: Int, column: Int) {
        fillRect(row, column, ANCHOR_THICKNESS, ANCHOR_SIZE, 0)
        fillRect(row - ANCHOR_SIZE + ANCHOR_THICKNESS, column, ANCHOR_SIZE, ANCHOR_THICKNESS, 0)
    }

    private fun drawBottomRightAnchor(row: Int, column: Int) {
        fillRect(row, column - ANCHOR_SIZE + 1, ANCHOR_THICKNESS, ANCHOR_SIZE, 0)
        fillRect(row - ANCHOR_SIZE + ANCHOR_THICKNESS, column - ANCHOR_THICKNESS + 1, ANCHOR_SIZE, ANCHOR_THICKNESS, 0)
    }

    private fun markCell(row: Int, column: Int, markSize: Int, gray: Int) {
        val top = margin + row * cellSize + (cellSize - markSize) / 2
        val left = margin + column * cellSize + (cellSize - markSize) / 2
        fillRect(top, left, markSize, markSize, gray.coerceIn(0, 255))
    }

    private fun fillRect(top: Int, left: Int, height: Int, width: Int, gray: Int) {
        for (row in top until top + height) {
            for (column in left until left + width) {
                setPixel(row, column, gray)
            }
        }
    }

    private fun setPixel(row: Int, column: Int, gray: Int) {
        if (row in 0 until frameHeight && column in 0 until frameWidth) {
            pixels[row * frameWidth + column] = gray
        }
    }

    companion object {
        const val DEFAULT_CELL_SIZE = 34
        const val DEFAULT_MARGIN = 24
        const val DEFAULT_MARK_SIZE = 18
        private const val ANCHOR_SIZE = 34
        private const val ANCHOR_THICKNESS = 8

        fun loadPngAsFrame(file: File): MiniProgramFrame {
            val image = ImageIO.read(file)
            val pixels = IntArray(image.width * image.height)
            for (row in 0 until image.height) {
                for (column in 0 until image.width) {
                    val rgb = image.getRGB(column, row)
                    val red = rgb shr 16 and 0xff
                    val green = rgb shr 8 and 0xff
                    val blue = rgb and 0xff
                    pixels[row * image.width + column] = (red * 299 + green * 587 + blue * 114) / 1000
                }
            }
            return MiniProgramFrame(width = image.width, height = image.height, pixels = pixels)
        }
    }
}
