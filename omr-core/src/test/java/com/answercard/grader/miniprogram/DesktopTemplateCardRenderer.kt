package com.answercard.grader.miniprogram

import com.answercard.grader.template.CardLayout
import com.answercard.grader.template.CodedCornerMarker
import com.answercard.grader.template.CornerMarkerId
import com.answercard.grader.template.CornerMarkerStyle
import com.answercard.grader.template.Rect
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/**
 * Pure-JVM twin of the Android `TemplateRenderer` for scanner regression tests:
 * same geometry source, java.awt drawing instead of android.graphics.
 */
class DesktopTemplateCardRenderer(
    template: TemplateState,
    private val scale: Float = 3f,
    private val markerStyle: CornerMarkerStyle = CornerMarkerStyle.CODED,
) {
    private val layout: CardLayout = TemplateGeometry.buildLayout(template)
    private val image: BufferedImage = BufferedImage(
        (TemplateGeometry.renderedWidth(layout) * scale).roundToInt(),
        (TemplateGeometry.renderedHeight(layout) * scale).roundToInt(),
        BufferedImage.TYPE_INT_RGB,
    )

    init {
        withGraphics { graphics ->
            graphics.color = Color.WHITE
            graphics.fill(Rectangle2D.Float(0f, 0f, TemplateGeometry.renderedWidth(layout), TemplateGeometry.renderedHeight(layout)))
            graphics.color = Color.BLACK
            when (markerStyle) {
                CornerMarkerStyle.CODED -> drawCodedCornerMarkers(graphics)
                CornerMarkerStyle.SOLID_SQUARE -> drawCornerMarkers(graphics)
                CornerMarkerStyle.L_BRACKET -> drawCornerBrackets(graphics)
            }
            graphics.translate(TemplateGeometry.PAGE_MARGIN.toDouble(), TemplateGeometry.PAGE_MARGIN.toDouble())
            if (layout.showHeader) {
                drawHeader(graphics)
            }
            drawQuestions(graphics)
        }
    }

    fun markAnswer(questionNumber: Int, optionLabel: String) {
        val rect = layout.options.single { it.question == questionNumber && it.option == optionLabel }.rect
        fillMark(TemplateGeometry.renderedRect(rect))
    }

    fun markAdmissionNumber(digits: String) {
        digits.forEachIndexed { digitIndex, char ->
            if (char.isDigit()) {
                fillMark(
                    TemplateGeometry.renderedRect(
                        TemplateGeometry.examIdDigitBox(layout, digitIndex, char.digitToInt()),
                    ),
                )
            }
        }
    }

    fun eraseCornerMarker(id: CornerMarkerId) {
        val rect = TemplateGeometry.cornerMarkerRect(layout, id)
        withGraphics { graphics ->
            graphics.color = Color.WHITE
            graphics.fill(Rectangle2D.Float(rect.x, rect.y, rect.w, rect.h))
        }
    }

    fun frame(): MiniProgramFrame {
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

    private fun fillMark(rect: Rect) {
        withGraphics { graphics ->
            graphics.color = Color.BLACK
            val insetX = rect.w * 0.18f
            val insetY = rect.h * 0.18f
            graphics.fill(
                Rectangle2D.Float(
                    rect.x + insetX,
                    rect.y + insetY,
                    rect.w - insetX * 2f,
                    rect.h - insetY * 2f,
                ),
            )
        }
    }

    private fun withGraphics(block: (Graphics2D) -> Unit) {
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.scale(scale.toDouble(), scale.toDouble())
            block(graphics)
        } finally {
            graphics.dispose()
        }
    }

    private fun drawCornerBrackets(graphics: Graphics2D) {
        val size = TemplateGeometry.CORNER_BRACKET_SIZE
        val thick = TemplateGeometry.CORNER_BRACKET_THICKNESS
        val left = TemplateGeometry.CORNER_BRACKET_MARGIN
        val top = TemplateGeometry.CORNER_BRACKET_MARGIN
        val right = TemplateGeometry.renderedWidth(layout) - TemplateGeometry.CORNER_BRACKET_MARGIN
        val bottom = TemplateGeometry.renderedHeight(layout) - TemplateGeometry.CORNER_BRACKET_MARGIN
        val pieces = listOf(
            Rect(left, top, size, thick),
            Rect(left, top, thick, size),
            Rect(right - size, top, size, thick),
            Rect(right - thick, top, thick, size),
            Rect(left, bottom - thick, size, thick),
            Rect(left, bottom - size, thick, size),
            Rect(right - size, bottom - thick, size, thick),
            Rect(right - thick, bottom - size, thick, size),
        )
        pieces.forEach { graphics.fill(Rectangle2D.Float(it.x, it.y, it.w, it.h)) }
    }

    private fun drawCornerMarkers(graphics: Graphics2D) {
        val rects = TemplateGeometry.cornerMarkerRects(layout)
        listOf(rects.lu, rects.ru, rects.ld, rects.rd).forEach { rect ->
            graphics.fill(Rectangle2D.Float(rect.x, rect.y, rect.w, rect.h))
        }
    }

    private fun drawCodedCornerMarkers(graphics: Graphics2D) {
        CornerMarkerId.entries.forEach { id ->
            val rect = TemplateGeometry.cornerMarkerRect(layout, id)
            val module = rect.w / CodedCornerMarker.GRID_SIZE
            graphics.color = Color.BLACK
            graphics.fill(Rectangle2D.Float(rect.x, rect.y, rect.w, rect.h))
            graphics.color = Color.WHITE
            graphics.fill(
                Rectangle2D.Float(
                    rect.x + module,
                    rect.y + module,
                    rect.w - module * 2f,
                    rect.h - module * 2f,
                ),
            )
            graphics.color = Color.BLACK
            for (row in 0 until CodedCornerMarker.PAYLOAD_SIZE) {
                for (column in 0 until CodedCornerMarker.PAYLOAD_SIZE) {
                    if (!CodedCornerMarker.payloadBit(CodedCornerMarker.payload(id), row, column)) continue
                    graphics.fill(
                        Rectangle2D.Float(
                            rect.x + (column + 1) * module,
                            rect.y + (row + 1) * module,
                            module,
                            module,
                        ),
                    )
                }
            }
        }
    }

    private fun drawHeader(graphics: Graphics2D) {
        graphics.stroke = BasicStroke(1f)
        val dividerY = TemplateGeometry.HEADER_HEIGHT + TemplateGeometry.HEADER_DIVIDER_GAP / 2f
        graphics.drawLine(24, dividerY.roundToInt(), (layout.width - 24f).roundToInt(), dividerY.roundToInt())

        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 10)
        graphics.stroke = BasicStroke(0.7f)
        listOf(42f to "Class:", 70f to "Name:").forEach { (y, label) ->
            graphics.drawString(label, TemplateGeometry.INFO_X, y)
            graphics.draw(
                java.awt.geom.Line2D.Float(
                    TemplateGeometry.INFO_X + 36f,
                    y + 1f,
                    TemplateGeometry.INFO_X + TemplateGeometry.INFO_W,
                    y + 1f,
                ),
            )
        }

        val stroke = BasicStroke(0.65f)
        val dashed = BasicStroke(0.55f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(2f, 2f), 0f)
        val digitFont = Font(Font.SANS_SERIF, Font.BOLD, 8).deriveFont(7.2f)
        layout.examIdRows.forEachIndexed { rowIndex, row ->
            graphics.stroke = dashed
            graphics.draw(Rectangle2D.Float(row.x, row.y - 2f, row.w, row.h + 4f))

            graphics.stroke = stroke
            val writeY = row.y + (row.h - TemplateGeometry.EXAM_WRITE_BOX) / 2f
            graphics.draw(
                Rectangle2D.Float(row.x + 2f, writeY, TemplateGeometry.EXAM_WRITE_BOX, TemplateGeometry.EXAM_WRITE_BOX),
            )

            for (digit in 0..9) {
                val box = TemplateGeometry.examIdDigitBox(layout, rowIndex, digit)
                graphics.draw(Rectangle2D.Float(box.x, box.y, box.w, box.h))
                drawCenteredText(graphics, digit.toString(), digitFont, box.x + box.w / 2f, box.y + box.h - 2f)
            }
        }
    }

    private fun drawQuestions(graphics: Graphics2D) {
        val labelFont = Font(Font.SANS_SERIF, Font.BOLD, 9).deriveFont(8.5f)
        layout.questionGuides.forEach { guide ->
            drawRightAlignedText(
                graphics,
                guide.question.toString(),
                labelFont,
                guide.numberRect.x + guide.numberRect.w,
                guide.numberRect.y + guide.numberRect.h - 2.6f,
            )
        }

        graphics.stroke = BasicStroke(0.65f)
        layout.options.forEach { option ->
            val rect = option.rect
            graphics.draw(Rectangle2D.Float(rect.x, rect.y, rect.w, rect.h))
            drawCenteredText(graphics, option.option, labelFont, rect.x + rect.w / 2f, rect.y + rect.h - 3.2f)
        }
    }

    private fun drawCenteredText(graphics: Graphics2D, text: String, font: Font, centerX: Float, baselineY: Float) {
        graphics.font = font
        val width = graphics.fontMetrics.stringWidth(text)
        graphics.drawString(text, centerX - width / 2f, baselineY)
    }

    private fun drawRightAlignedText(graphics: Graphics2D, text: String, font: Font, rightX: Float, baselineY: Float) {
        graphics.font = font
        val width = graphics.fontMetrics.stringWidth(text)
        graphics.drawString(text, rightX - width, baselineY)
    }
}
