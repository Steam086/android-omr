package com.answercard.grader.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.answercard.grader.template.CardLayout
import com.answercard.grader.template.OptionBox
import kotlin.math.ceil
import kotlin.math.floor

object AnswerReader {
    private const val DARK_THRESHOLD = 95
    private const val FILLED_RATIO_THRESHOLD = 0.5f

    fun readAnswers(
        bitmap: Bitmap,
        layout: CardLayout,
        scale: Float = 3f,
    ): Map<Int, String?> {
        require(scale > 0f) { "scale must be greater than zero" }

        return layout.options
            .groupBy { it.question }
            .mapValues { (_, options) -> readQuestion(bitmap, options, scale) }
    }

    private fun readQuestion(
        bitmap: Bitmap,
        options: List<OptionBox>,
        scale: Float,
    ): String? {
        val scored = options.map { it.option to filledRatio(bitmap, it, scale) }
        val best = scored.maxByOrNull { it.second } ?: return null
        return if (best.second >= FILLED_RATIO_THRESHOLD) best.first else null
    }

    private fun filledRatio(bitmap: Bitmap, option: OptionBox, scale: Float): Float {
        val rect = option.rect
        val left = floor((rect.x + 2f) * scale).toInt().coerceIn(0, bitmap.width)
        val top = floor((rect.y + 2f) * scale).toInt().coerceIn(0, bitmap.height)
        val right = ceil((rect.x + rect.w * 0.42f) * scale).toInt().coerceIn(left, bitmap.width)
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
