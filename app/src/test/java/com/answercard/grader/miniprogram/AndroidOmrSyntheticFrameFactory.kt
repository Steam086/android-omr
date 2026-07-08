package com.answercard.grader.miniprogram

import com.answercard.grader.template.TemplateState

class AndroidOmrSyntheticFrameFactory(
    val template: TemplateState,
) {
    val layout: AndroidPaperTemplateLayout = AndroidPaperTemplateBuilder.build(
        questionOptionCounts = template.questions.map { it.optionCount },
        admissionNumberDigits = if (template.showHeader) template.examIdDigits else 0,
    )
    val frameWidth: Int = layout.gridColumns * CELL_SIZE
    val frameHeight: Int = layout.gridRows * CELL_SIZE
    val grid: MiniProgramGrid = MiniProgramGridBuilder.build(
        lu = MiniProgramPoint(row = 0, column = 0),
        ld = MiniProgramPoint(row = frameHeight - 1, column = 0),
        ru = MiniProgramPoint(row = 0, column = frameWidth - 1),
        rd = MiniProgramPoint(row = frameHeight - 1, column = frameWidth - 1),
        rows = layout.gridRows,
        columns = layout.gridColumns,
    )
    private val pixels = IntArray(frameWidth * frameHeight) { 255 }

    fun markAnswer(questionIndex: Int, optionIndex: Int, markSize: Int = DEFAULT_MARK_SIZE) {
        val mapping = layout.questionMappings.single {
            it.questionIndex == questionIndex && it.optionIndex == optionIndex
        }
        markCell(mapping.row, mapping.column, markSize)
    }

    fun markAdmissionNumber(digits: String) {
        digits.forEachIndexed { digitIndex, char ->
            if (char.isDigit()) {
                markAdmissionDigit(digitIndex = digitIndex, numberValue = char.digitToInt())
            }
        }
    }

    fun markAdmissionDigit(digitIndex: Int, numberValue: Int, markSize: Int = DEFAULT_MARK_SIZE) {
        val mapping = layout.admissionNumberMappings.single {
            it.digitIndex == digitIndex && it.numberValue == numberValue
        }
        markCell(mapping.row, mapping.column, markSize)
    }

    fun frame(): MiniProgramFrame =
        MiniProgramFrame(width = frameWidth, height = frameHeight, pixels = pixels)

    private fun markCell(row: Int, column: Int, markSize: Int) {
        val top = row * CELL_SIZE + (CELL_SIZE - markSize) / 2
        val left = column * CELL_SIZE + (CELL_SIZE - markSize) / 2
        for (r in top until top + markSize) {
            for (c in left until left + markSize) {
                pixels[r * frameWidth + c] = 0
            }
        }
    }

    companion object {
        const val CELL_SIZE = 28
        private const val DEFAULT_MARK_SIZE = 14
    }
}
