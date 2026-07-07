package com.answercard.grader.miniprogram

data class MiniProgramFrame(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(pixels.size == width * height) { "pixels must contain width * height values" }
    }

    operator fun get(row: Int, column: Int): Int =
        if (row < 0 || row >= height || column < 0 || column >= width) {
            255
        } else {
            pixels[row * width + column].coerceIn(0, 255)
        }
}

data class MiniProgramPoint(
    val row: Int,
    val column: Int,
)
