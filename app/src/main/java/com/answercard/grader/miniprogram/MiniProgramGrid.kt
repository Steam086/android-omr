package com.answercard.grader.miniprogram

data class MiniProgramGridPoint(
    val row: Double,
    val column: Double,
)

data class MiniProgramGrid(
    val rows: Int,
    val columns: Int,
    val points: List<MiniProgramGridPoint>,
) {
    init {
        require(rows > 0) { "rows must be positive" }
        require(columns > 0) { "columns must be positive" }
        require(points.size == (rows + 1) * (columns + 1)) {
            "points must contain (rows + 1) * (columns + 1) values"
        }
    }

    fun point(row: Int, column: Int): MiniProgramGridPoint {
        require(row in 0..rows) { "row must be in 0..rows" }
        require(column in 0..columns) { "column must be in 0..columns" }
        return points[row * (columns + 1) + column]
    }

    fun point(row: Double, column: Double): MiniProgramGridPoint {
        require(row in 0.0..rows.toDouble()) { "row must be in 0..rows" }
        require(column in 0.0..columns.toDouble()) { "column must be in 0..columns" }
        return MiniProgramGridBuilder.interpolate(
            lu = point(0, 0),
            ld = point(rows, 0),
            ru = point(0, columns),
            rd = point(rows, columns),
            rowRatio = row / rows.toDouble(),
            columnRatio = column / columns.toDouble(),
        )
    }

    fun cell(row: Int, column: Int): MiniProgramCell {
        require(row in 0 until rows) { "row must be in 0 until rows" }
        require(column in 0 until columns) { "column must be in 0 until columns" }
        return MiniProgramCell(
            row = row,
            column = column,
            leftTop = point(row, column),
            rightTop = point(row, column + 1),
            leftBottom = point(row + 1, column),
            rightBottom = point(row + 1, column + 1),
        )
    }
}
