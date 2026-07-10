package com.answercard.grader.miniprogram

import kotlin.math.roundToInt
import kotlin.math.sin

object TestNonPlanarWarp {
    fun horizontalBend(frame: MiniProgramFrame, amplitude: Double): MiniProgramFrame {
        val pixels = IntArray(frame.pixels.size) { 255 }
        val denominator = (frame.height - 1).coerceAtLeast(1).toDouble()
        for (row in 0 until frame.height) {
            val ratio = row / denominator
            val shift = amplitude * sin(Math.PI * ratio)
            for (column in 0 until frame.width) {
                val sourceColumn = (column - shift).roundToInt()
                if (sourceColumn in 0 until frame.width) {
                    pixels[row * frame.width + column] = frame[row, sourceColumn]
                }
            }
        }
        return MiniProgramFrame(frame.width, frame.height, pixels)
    }
}
