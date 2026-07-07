package com.answercard.grader.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.answercard.grader.template.CardLayout
import com.answercard.grader.template.TemplateGeometry
import kotlin.math.ceil
import kotlin.math.floor

object ExamIdReader {
    private const val DARK_THRESHOLD = 95
    private const val FILLED_RATIO_THRESHOLD = 0.5f

    fun readExamId(
        bitmap: Bitmap,
        layout: CardLayout,
        scale: Float = 3f,
    ): String? {
        require(scale > 0f) { "scale must be greater than zero" }

        val digits = layout.examIdRows.indices.map { column ->
            readColumn(bitmap, layout, column, scale) ?: return null
        }
        return digits.joinToString(separator = "")
    }

    private fun readColumn(bitmap: Bitmap, layout: CardLayout, column: Int, scale: Float): Int? {
        val scored = (0..9).map { digit ->
            digit to filledRatio(bitmap, TemplateGeometry.examIdDigitBox(layout, column, digit), scale)
        }
        val best = scored.maxByOrNull { it.second } ?: return null
        return if (best.second >= FILLED_RATIO_THRESHOLD) best.first else null
    }

    private fun filledRatio(
        bitmap: Bitmap,
        rect: com.answercard.grader.template.Rect,
        scale: Float,
    ): Float {
        val left = floor((rect.x + 2f) * scale).toInt().coerceIn(0, bitmap.width)
        val top = floor((rect.y + 2f) * scale).toInt().coerceIn(0, bitmap.height)
        val right = ceil((rect.x + rect.w - 2f) * scale).toInt().coerceIn(left, bitmap.width)
        val bottom = ceil((rect.y + rect.h - 2f) * scale).toInt().coerceIn(top, bitmap.height)
        var dark = 0
        var total = 0

        for (y in top until bottom) {
            for (x in left until right) {
                total += 1
                if (isDark(bitmap.getPixel(x, y))) dark += 1
            }
        }

        return if (total == 0) 0f else dark.toFloat() / total.toFloat()
    }

    private fun isDark(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return (red + green + blue) / 3 < DARK_THRESHOLD
    }
}
