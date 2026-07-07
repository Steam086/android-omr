package com.answercard.grader.miniprogram

object MiniProgramGridBuilder {
    fun build(
        lu: MiniProgramPoint,
        ld: MiniProgramPoint,
        ru: MiniProgramPoint,
        rd: MiniProgramPoint,
        rows: Int,
        columns: Int,
    ): MiniProgramGrid {
        require(rows > 0) { "rows must be positive" }
        require(columns > 0) { "columns must be positive" }
        require(isValidQuad(lu, ld, ru, rd)) { "corner points must form a valid quadrilateral" }

        val points = ArrayList<MiniProgramGridPoint>((rows + 1) * (columns + 1))
        val leftTop = lu.toGridPoint()
        val leftBottom = ld.toGridPoint()
        val rightTop = ru.toGridPoint()
        val rightBottom = rd.toGridPoint()

        for (row in 0..rows) {
            val rowRatio = row.toDouble() / rows.toDouble()
            for (column in 0..columns) {
                points += interpolate(
                    lu = leftTop,
                    ld = leftBottom,
                    ru = rightTop,
                    rd = rightBottom,
                    rowRatio = rowRatio,
                    columnRatio = column.toDouble() / columns.toDouble(),
                )
            }
        }

        return MiniProgramGrid(rows = rows, columns = columns, points = points)
    }

    internal fun interpolate(
        lu: MiniProgramGridPoint,
        ld: MiniProgramGridPoint,
        ru: MiniProgramGridPoint,
        rd: MiniProgramGridPoint,
        rowRatio: Double,
        columnRatio: Double,
    ): MiniProgramGridPoint {
        val top = lerp(lu, ru, columnRatio)
        val bottom = lerp(ld, rd, columnRatio)
        return lerp(top, bottom, rowRatio)
    }

    private fun MiniProgramPoint.toGridPoint(): MiniProgramGridPoint =
        MiniProgramGridPoint(row = row.toDouble(), column = column.toDouble())

    private fun lerp(a: MiniProgramGridPoint, b: MiniProgramGridPoint, ratio: Double): MiniProgramGridPoint =
        MiniProgramGridPoint(
            row = a.row + (b.row - a.row) * ratio,
            column = a.column + (b.column - a.column) * ratio,
        )

    private fun isValidQuad(
        lu: MiniProgramPoint,
        ld: MiniProgramPoint,
        ru: MiniProgramPoint,
        rd: MiniProgramPoint,
    ): Boolean =
        ru.column > lu.column &&
            rd.column > ld.column &&
            ld.row > lu.row &&
            rd.row > ru.row &&
            polygonArea(lu, ru, rd, ld) > 0.0

    private fun polygonArea(
        a: MiniProgramPoint,
        b: MiniProgramPoint,
        c: MiniProgramPoint,
        d: MiniProgramPoint,
    ): Double {
        val twiceArea =
            a.column * b.row - a.row * b.column +
                b.column * c.row - b.row * c.column +
                c.column * d.row - c.row * d.column +
                d.column * a.row - d.row * a.column
        return kotlin.math.abs(twiceArea.toDouble()) / 2.0
    }
}
