package com.answercard.grader.miniprogram

data class MiniProgramConnectedComponentCleanResult(
    val cleanedBinary: IntArray,
    val originalBlackCount: Int,
    val noiseComponentsRemoved: Int,
    val noisePixelsRemoved: Int,
    val componentsKept: Int,
    val largestComponentArea: Int,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            other is MiniProgramConnectedComponentCleanResult &&
            cleanedBinary.contentEquals(other.cleanedBinary) &&
            originalBlackCount == other.originalBlackCount &&
            noiseComponentsRemoved == other.noiseComponentsRemoved &&
            noisePixelsRemoved == other.noisePixelsRemoved &&
            componentsKept == other.componentsKept &&
            largestComponentArea == other.largestComponentArea

    override fun hashCode(): Int {
        var result = cleanedBinary.contentHashCode()
        result = 31 * result + originalBlackCount
        result = 31 * result + noiseComponentsRemoved
        result = 31 * result + noisePixelsRemoved
        result = 31 * result + componentsKept
        result = 31 * result + largestComponentArea
        return result
    }
}

object MiniProgramConnectedComponentCleaner {
    private const val DEFAULT_NOISE_RATIO = 0.125

    fun clean(
        binary: IntArray,
        rows: Int,
        columns: Int,
        noiseRatio: Double = DEFAULT_NOISE_RATIO,
    ): MiniProgramConnectedComponentCleanResult {
        require(rows > 0) { "rows must be positive" }
        require(columns > 0) { "columns must be positive" }
        require(binary.size == rows * columns) { "binary size must equal rows * columns" }
        require(noiseRatio >= 0.0) { "noiseRatio must be non-negative" }
        require(binary.all { it == 0 || it == 1 }) { "binary values must be 0 or 1" }

        val cleaned = binary.copyOf()
        val labels = IntArray(binary.size)
        val queue = IntArray(binary.size)
        val noiseThreshold = rows * columns * noiseRatio
        var nextLabel = 1
        var originalBlackCount = 0
        var noiseComponentsRemoved = 0
        var noisePixelsRemoved = 0
        var componentsKept = 0
        var largestComponentArea = 0

        for (index in binary.indices) {
            if (binary[index] == 0) originalBlackCount += 1
            if (binary[index] != 0 || labels[index] != 0) continue

            val componentArea = labelComponent(binary, labels, queue, index, nextLabel, rows, columns)
            largestComponentArea = maxOf(largestComponentArea, componentArea)
            if (componentArea < noiseThreshold) {
                noiseComponentsRemoved += 1
                noisePixelsRemoved += componentArea
                for (labelIndex in labels.indices) {
                    if (labels[labelIndex] == nextLabel) cleaned[labelIndex] = 1
                }
            } else {
                componentsKept += 1
            }
            nextLabel += 1
        }

        return MiniProgramConnectedComponentCleanResult(
            cleanedBinary = cleaned,
            originalBlackCount = originalBlackCount,
            noiseComponentsRemoved = noiseComponentsRemoved,
            noisePixelsRemoved = noisePixelsRemoved,
            componentsKept = componentsKept,
            largestComponentArea = largestComponentArea,
        )
    }

    private fun labelComponent(
        binary: IntArray,
        labels: IntArray,
        queue: IntArray,
        startIndex: Int,
        label: Int,
        rows: Int,
        columns: Int,
    ): Int {
        var head = 0
        var tail = 0
        var count = 0
        queue[tail++] = startIndex
        labels[startIndex] = label

        while (head < tail) {
            val index = queue[head++]
            count += 1
            val row = index / columns
            val column = index % columns
            tail = enqueue(binary, labels, queue, tail, label, rows, columns, row, column - 1)
            tail = enqueue(binary, labels, queue, tail, label, rows, columns, row, column + 1)
            tail = enqueue(binary, labels, queue, tail, label, rows, columns, row - 1, column)
            tail = enqueue(binary, labels, queue, tail, label, rows, columns, row + 1, column)
        }

        return count
    }

    private fun enqueue(
        binary: IntArray,
        labels: IntArray,
        queue: IntArray,
        tail: Int,
        label: Int,
        rows: Int,
        columns: Int,
        row: Int,
        column: Int,
    ): Int {
        if (row !in 0 until rows || column !in 0 until columns) return tail
        val index = row * columns + column
        if (binary[index] != 0 || labels[index] != 0) return tail
        labels[index] = label
        queue[tail] = index
        return tail + 1
    }
}
