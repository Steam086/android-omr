package com.answercard.grader.template

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

object TemplateRenderer {
    fun render(scale: Float = 3f): Bitmap = render(TemplateGeometry.buildLayout(), scale)

    fun render(template: TemplateState, scale: Float = 3f): Bitmap =
        render(TemplateGeometry.buildLayout(template), scale)

    fun render(layout: CardLayout, scale: Float = 3f): Bitmap {
        require(scale > 0f) { "scale must be greater than zero" }

        val bitmap = Bitmap.createBitmap(
            (TemplateGeometry.renderedWidth(layout) * scale).roundToInt(),
            (TemplateGeometry.renderedHeight(layout) * scale).roundToInt(),
            Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val optionText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        drawCornerBrackets(canvas, layout, fill)
        canvas.save()
        canvas.translate(TemplateGeometry.PAGE_MARGIN, TemplateGeometry.PAGE_MARGIN)
        if (layout.showHeader) {
            drawHeaderDivider(canvas, layout)
            drawInfoPanel(canvas)
            drawExamId(canvas, layout)
        }
        drawQuestions(canvas, layout, optionText, stroke)
        canvas.restore()

        return bitmap
    }

    fun renderPng(layout: CardLayout, scale: Float = 3f): ByteArray {
        val out = ByteArrayOutputStream()
        render(layout, scale).compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    fun renderPng(scale: Float = 3f): ByteArray = renderPng(TemplateGeometry.buildLayout(), scale)

    fun renderPng(template: TemplateState, scale: Float = 3f): ByteArray =
        renderPng(TemplateGeometry.buildLayout(template), scale)

    private fun drawCornerBrackets(canvas: Canvas, layout: CardLayout, paint: Paint) {
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
        pieces.forEach { canvas.drawRect(it.x, it.y, it.x + it.w, it.y + it.h, paint) }
    }

    private fun drawHeaderDivider(canvas: Canvas, layout: CardLayout) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1f
        }
        val y = TemplateGeometry.HEADER_HEIGHT + TemplateGeometry.HEADER_DIVIDER_GAP / 2f
        canvas.drawLine(24f, y, layout.width - 24f, y, paint)
    }

    private fun drawInfoPanel(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 0.7f
            textSize = 10.5f
        }
        val lineYs = listOf(42f, 70f)
        val labels = listOf("班级", "姓名")
        labels.forEachIndexed { index, label ->
            val y = lineYs[index]
            canvas.drawText("$label：", TemplateGeometry.INFO_X, y, paint)
            canvas.drawLine(
                TemplateGeometry.INFO_X + 36f,
                y + 1f,
                TemplateGeometry.INFO_X + TemplateGeometry.INFO_W,
                y + 1f,
                paint,
            )
        }

        paint.pathEffect = DashPathEffect(floatArrayOf(1.6f, 2f), 0f)
        paint.strokeWidth = 0.55f
        canvas.drawLine(
            TemplateGeometry.EXAM_DASH_X,
            18f,
            TemplateGeometry.EXAM_DASH_X,
            TemplateGeometry.HEADER_HEIGHT - 18f,
            paint,
        )
        paint.pathEffect = null
    }

    private fun drawExamId(canvas: Canvas, layout: CardLayout) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 0.65f
        }
        val guide = Paint(stroke).apply {
            strokeWidth = 0.55f
            pathEffect = DashPathEffect(floatArrayOf(2f, 2f), 0f)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = 7.2f
            typeface = Typeface.DEFAULT_BOLD
        }

        layout.examIdRows.forEachIndexed { rowIndex, row ->
            canvas.drawRect(row.x, row.y - 2f, row.x + row.w, row.y + row.h + 2f, guide)

            val writeY = row.y + (row.h - TemplateGeometry.EXAM_WRITE_BOX) / 2f
            canvas.drawRect(
                row.x + 2f,
                writeY,
                row.x + 2f + TemplateGeometry.EXAM_WRITE_BOX,
                writeY + TemplateGeometry.EXAM_WRITE_BOX,
                stroke,
            )

            for (digit in 0..9) {
                val box = TemplateGeometry.examIdDigitBox(layout, rowIndex, digit)
                canvas.drawRect(box.x, box.y, box.x + box.w, box.y + box.h, stroke)
                canvas.drawText(digit.toString(), box.x + box.w / 2f, box.y + box.h - 2f, text)
            }
        }
    }

    private fun drawQuestions(
        canvas: Canvas,
        layout: CardLayout,
        text: Paint,
        stroke: Paint,
    ) {
        val numberText = Paint(text).apply {
            textAlign = Paint.Align.RIGHT
        }
        layout.questionGuides.forEach { guide ->
            canvas.drawText(
                guide.question.toString(),
                guide.numberRect.x + guide.numberRect.w,
                guide.numberRect.y + guide.numberRect.h - 2.6f,
                numberText,
            )
        }

        stroke.strokeWidth = 0.65f
        layout.options.forEach { option ->
            val rect = option.rect
            canvas.drawRect(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, stroke)
            canvas.drawText(option.option, rect.x + rect.w / 2f, rect.y + rect.h - 3.2f, text)
        }
    }
}
