package com.answercard.grader.miniprogram

data class MiniProgramComponentRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class MiniProgramComponent(
    val rect: MiniProgramComponentRect,
    val area: Int,
    val centroidRow: Double,
    val centroidColumn: Double,
) {
    val fillRatio: Float get() = area.toFloat() / (rect.width * rect.height).coerceAtLeast(1)
    val centerRow: Double get() = (rect.top + rect.bottom - 1) / 2.0
    val centerColumn: Double get() = (rect.left + rect.right - 1) / 2.0
}

object MiniProgramComponentScanner {
    fun scan(frame: MiniProgramFrame, threshold: Int): List<MiniProgramComponent> {
        val mask = BooleanArray(frame.width * frame.height) { frame.pixels[it] < threshold }
        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        val components = mutableListOf<MiniProgramComponent>()
        for (start in mask.indices) {
            if (visited[start] || !mask[start]) {
                visited[start] = true
                continue
            }
            var minRow = start / frame.width
            var maxRow = minRow
            var minColumn = start % frame.width
            var maxColumn = minColumn
            var count = 0
            var sumRow = 0L
            var sumColumn = 0L
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            while (head < tail) {
                val index = queue[head++]
                val row = index / frame.width
                val column = index % frame.width
                count += 1
                sumRow += row
                sumColumn += column
                minRow = minOf(minRow, row)
                maxRow = maxOf(maxRow, row)
                minColumn = minOf(minColumn, column)
                maxColumn = maxOf(maxColumn, column)
                tail = enqueue(mask, visited, queue, tail, frame.width, frame.height, row - 1, column)
                tail = enqueue(mask, visited, queue, tail, frame.width, frame.height, row + 1, column)
                tail = enqueue(mask, visited, queue, tail, frame.width, frame.height, row, column - 1)
                tail = enqueue(mask, visited, queue, tail, frame.width, frame.height, row, column + 1)
            }
            components += MiniProgramComponent(
                rect = MiniProgramComponentRect(
                    left = minColumn,
                    top = minRow,
                    right = maxColumn + 1,
                    bottom = maxRow + 1,
                ),
                area = count,
                centroidRow = sumRow.toDouble() / count,
                centroidColumn = sumColumn.toDouble() / count,
            )
        }
        return components
    }

    private fun enqueue(
        mask: BooleanArray,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int,
        width: Int,
        height: Int,
        row: Int,
        column: Int,
    ): Int {
        if (row !in 0 until height || column !in 0 until width) return tail
        val index = row * width + column
        if (visited[index]) return tail
        visited[index] = true
        if (!mask[index]) return tail
        queue[tail] = index
        return tail + 1
    }
}
