package com.answercard.grader.template

import kotlin.math.ceil

data class Rect(val x: Float, val y: Float, val w: Float, val h: Float)

data class TemplatePoint(val x: Float, val y: Float)

data class CornerAnchorReference(
    val lu: TemplatePoint,
    val ld: TemplatePoint,
    val ru: TemplatePoint,
    val rd: TemplatePoint,
)

data class OptionBox(val question: Int, val option: String, val rect: Rect)

data class QuestionGuide(val question: Int, val numberRect: Rect)

data class CardLayout(
    val width: Float,
    val height: Float,
    val options: List<OptionBox>,
    val examIdRows: List<Rect>,
    val questionGuides: List<QuestionGuide>,
    val showHeader: Boolean = true,
)

object TemplateGeometry {
    val OPTIONS = listOf("A", "B", "C", "D")
    const val QUESTION_COUNT = 15
    const val PAGE_MARGIN = 40f
    const val CORNER_BRACKET_MARGIN = 24f
    const val CORNER_BRACKET_SIZE = 34f
    const val CORNER_BRACKET_THICKNESS = 8f
    const val CARD_WIDTH = 540f
    const val HEADER_HEIGHT = 96f
    const val HEADER_DIVIDER_GAP = 8f
    const val QUESTION_GROUPS_PER_ROW = 3
    const val QUESTIONS_PER_GROUP = 5
    const val QUESTIONS_PER_BAND = QUESTION_GROUPS_PER_ROW * QUESTIONS_PER_GROUP
    const val QUESTION_BAND_HEIGHT = 98f
    const val CARD_MARGIN_X = 20f
    const val CARD_MARGIN_BOTTOM = 14f
    const val OPTION_BOX_W = 18f
    const val OPTION_BOX_H = 12.5f
    const val OPTION_STEP_X = 21.5f
    const val QUESTION_ROW_STEP_Y = 18.5f
    const val INFO_X = 18f
    const val INFO_Y_FROM_TOP = 27f
    const val INFO_W = 156f
    const val EXAM_X = 226f
    const val EXAM_Y_FROM_TOP = 16f
    const val EXAM_ROW_W = 294f
    const val EXAM_ROW_H = 13f
    const val EXAM_ROW_STEP_Y = 18f
    const val EXAM_WRITE_BOX = 12f
    const val EXAM_DIGIT_BOX_W = 14f
    const val EXAM_DIGIT_BOX_H = 10f
    const val EXAM_DIGIT_STEP_X = 22f
    const val EXAM_DASH_X = 205f

    fun buildLayout(questionCount: Int = QUESTION_COUNT): CardLayout {
        val count = questionCount.coerceIn(1, 60)
        val questions = (1..count).map { question ->
            LayoutQuestion(number = question, options = OPTIONS)
        }
        return buildLayout(questions)
    }

    fun buildLayout(template: TemplateState): CardLayout {
        val questions = template.questions.take(60).ifEmpty {
            TemplateState.default().questions
        }.map { question ->
            LayoutQuestion(number = question.number, options = question.options.ifEmpty { defaultOptionLabels(question.optionCount) })
        }
        return buildLayout(questions, template.showHeader)
    }

    private fun buildLayout(questions: List<LayoutQuestion>, showHeader: Boolean = true): CardLayout {
        val count = questions.size.coerceIn(1, 60)
        val bands = ceil(count / QUESTIONS_PER_BAND.toFloat()).toInt()
        val headerOffset = if (showHeader) HEADER_HEIGHT + HEADER_DIVIDER_GAP else 0f
        val height = headerOffset + bands * QUESTION_BAND_HEIGHT + CARD_MARGIN_BOTTOM
        val questionWidth = CARD_WIDTH - CARD_MARGIN_X * 2f
        val groupWidth = questionWidth / QUESTION_GROUPS_PER_ROW
        val answerTopGap = 20f
        val options = mutableListOf<OptionBox>()
        val guides = mutableListOf<QuestionGuide>()

        questions.take(count).forEachIndexed { questionIndex, question ->
            val position = questionIndex + 1
            val band = (position - 1) / QUESTIONS_PER_BAND
            val withinBand = (position - 1) % QUESTIONS_PER_BAND
            val group = withinBand / QUESTIONS_PER_GROUP
            val row = withinBand % QUESTIONS_PER_GROUP
            val baseX = CARD_MARGIN_X + 6f + group * groupWidth
            val y = headerOffset + answerTopGap +
                band * QUESTION_BAND_HEIGHT + row * QUESTION_ROW_STEP_Y

            guides += QuestionGuide(question.number, Rect(baseX, y - 1f, 18f, OPTION_BOX_H))
            question.options.forEachIndexed { index, option ->
                options += OptionBox(
                    question = question.number,
                    option = option,
                    rect = Rect(baseX + 25f + index * OPTION_STEP_X, y, OPTION_BOX_W, OPTION_BOX_H),
                )
            }
        }

        val examRows = if (showHeader) {
            (0 until 4).map { row ->
                Rect(EXAM_X, EXAM_Y_FROM_TOP + row * EXAM_ROW_STEP_Y, EXAM_ROW_W, EXAM_ROW_H)
            }
        } else {
            emptyList()
        }

        return CardLayout(
            width = CARD_WIDTH,
            height = height,
            options = options,
            examIdRows = examRows,
            questionGuides = guides,
            showHeader = showHeader,
        )
    }

    fun examIdDigitBox(layout: CardLayout, column: Int, digit: Int): Rect {
        val row = layout.examIdRows[column.coerceIn(0, layout.examIdRows.lastIndex)]
        val safeDigit = digit.coerceIn(0, 9)
        val x = row.x + EXAM_WRITE_BOX + 14f + safeDigit * EXAM_DIGIT_STEP_X
        val y = row.y + (row.h - EXAM_DIGIT_BOX_H) / 2f
        return Rect(x, y, EXAM_DIGIT_BOX_W, EXAM_DIGIT_BOX_H)
    }

    fun renderedWidth(layout: CardLayout): Float = layout.width + PAGE_MARGIN * 2f

    fun renderedHeight(layout: CardLayout): Float = layout.height + PAGE_MARGIN * 2f

    fun renderedRect(rect: Rect): Rect =
        Rect(
            x = PAGE_MARGIN + rect.x,
            y = PAGE_MARGIN + rect.y,
            w = rect.w,
            h = rect.h,
        )

    fun cornerAnchorReference(layout: CardLayout): CornerAnchorReference {
        val right = renderedWidth(layout) - CORNER_BRACKET_MARGIN
        val bottom = renderedHeight(layout) - CORNER_BRACKET_MARGIN
        return CornerAnchorReference(
            lu = TemplatePoint(CORNER_BRACKET_MARGIN, CORNER_BRACKET_MARGIN),
            ld = TemplatePoint(CORNER_BRACKET_MARGIN, bottom - CORNER_BRACKET_THICKNESS),
            ru = TemplatePoint(right, CORNER_BRACKET_MARGIN),
            rd = TemplatePoint(right, bottom - CORNER_BRACKET_THICKNESS),
        )
    }

    private data class LayoutQuestion(
        val number: Int,
        val options: List<String>,
    )

    private fun defaultOptionLabels(optionCount: Int): List<String> =
        if (optionCount == 2) {
            listOf("T", "F")
        } else {
            OPTIONS.take(optionCount.coerceIn(2, 4))
        }
}
