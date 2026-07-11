package com.answercard.grader.miniprogram

data class MiniProgramCornerTemplatePoint(
    val rowOffset: Int,
    val columnOffset: Int,
    val expected: Int,
)

object MiniProgramCornerTemplate {
    fun points(kind: MiniProgramCornerKind, large: Boolean): List<MiniProgramCornerTemplatePoint> {
        val main = if (large) 6 else 3
        val fine = if (large) 2 else 1
        return when (kind) {
            MiniProgramCornerKind.LU -> listOf(
                p(0, 0, 0), p(-main, 0, 1), p(0, -main, 1), p(main, main, 1),
                p(0, main, 0), p(-main, main, 1), p(main, -main, 1), p(main, 0, 0),
                p(main * 2, main, 1), p(main, main * 2, 1),
                p(0, fine, 0), p(fine, 0, 0), p(-main, fine, 1), p(fine, -main, 1),
                p(main, main + fine, 1), p(main + fine, main, 1),
                p(0, fine * 2, 0), p(fine * 2, 0, 0), p(-main, fine * 2, 1),
                p(fine * 2, -main, 1), p(main, main + fine * 2, 1), p(main + fine * 2, main, 1),
            )
            MiniProgramCornerKind.LD -> listOf(
                p(0, 0, 0), p(-main, 0, 1), p(0, main, 1), p(main, -main, 1),
                p(main, 0, 0), p(0, -main, 0), p(main, main, 1), p(-main, -main, 1),
                p(main * 2, -main, 1), p(main, -main * 2, 1),
                p(fine, 0, 0), p(0, -fine, 0), p(fine, main, 1), p(-main, -fine, 1),
                p(main + fine, -main, 1), p(main, -main - fine, 1),
                p(fine * 2, 0, 0), p(0, -fine * 2, 0), p(fine * 2, main, 1),
                p(-main, -fine * 2, 1), p(main + fine * 2, -main, 1), p(main, -main - fine * 2, 1),
            )
            MiniProgramCornerKind.RU -> listOf(
                p(0, 0, 0), p(main, 0, 1), p(0, -main, 1), p(-main, main, 1),
                p(0, main, 0), p(-main, 0, 0), p(main, main, 1), p(-main, -main, 1),
                p(-main, main * 2, 1), p(-main * 2, main, 1),
                p(0, fine, 0), p(-fine, 0, 0), p(main, fine, 1), p(-fine, -main, 1),
                p(-main, main + fine, 1), p(-main - fine, main, 1),
                p(0, fine * 2, 0), p(-fine * 2, 0, 0), p(main, fine * 2, 1),
                p(-fine * 2, -main, 1), p(-main, main + fine * 2, 1), p(-main - fine * 2, main, 1),
            )
            MiniProgramCornerKind.RD -> listOf(
                p(0, 0, 0), p(0, main, 1), p(main, 0, 1), p(-main, -main, 1),
                p(-main, 0, 0), p(0, -main, 0), p(-main, main, 1), p(main, -main, 1),
                p(-main * 2, -main, 1), p(-main, -main * 2, 1),
                p(-fine, 0, 0), p(0, -fine, 0), p(-fine, main, 1), p(main, -fine, 1),
                p(-main - fine, -main, 1), p(-main, -main - fine, 1),
                p(-fine * 2, 0, 0), p(0, -fine * 2, 0), p(-fine * 2, main, 1),
                p(main, -fine * 2, 1), p(-main - fine * 2, -main, 1), p(-main, -main - fine * 2, 1),
            )
        }
    }

    private fun p(row: Int, column: Int, expected: Int) =
        MiniProgramCornerTemplatePoint(row, column, expected)
}
