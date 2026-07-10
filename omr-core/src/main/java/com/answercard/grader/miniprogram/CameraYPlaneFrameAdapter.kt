package com.answercard.grader.miniprogram

import java.nio.ByteBuffer

data class YPlaneFrameInput(
    val width: Int,
    val height: Int,
    val rowStride: Int,
    val pixelStride: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val cropWidth: Int,
    val cropHeight: Int,
    val rotationDegrees: Int,
    val yData: ByteBuffer,
)

object CameraYPlaneFrameAdapter {
    fun toMiniProgramFrame(input: YPlaneFrameInput): MiniProgramFrame {
        validate(input)
        val outputWidth = if (input.rotationDegrees == 90 || input.rotationDegrees == 270) {
            input.cropHeight
        } else {
            input.cropWidth
        }
        val outputHeight = if (input.rotationDegrees == 90 || input.rotationDegrees == 270) {
            input.cropWidth
        } else {
            input.cropHeight
        }
        val output = IntArray(outputWidth * outputHeight)
        val source = input.yData.duplicate().apply { rewind() }
        for (row in 0 until input.cropHeight) {
            val sourceRow = input.cropTop + row
            for (column in 0 until input.cropWidth) {
                val sourceColumn = input.cropLeft + column
                val sourceIndex = sourceRow * input.rowStride + sourceColumn * input.pixelStride
                val targetIndex = when (input.rotationDegrees) {
                    0 -> row * outputWidth + column
                    90 -> column * outputWidth + (input.cropHeight - 1 - row)
                    180 -> (input.cropHeight - 1 - row) * outputWidth +
                        (input.cropWidth - 1 - column)
                    270 -> (input.cropWidth - 1 - column) * outputWidth + row
                    else -> error("validated rotation")
                }
                output[targetIndex] = source.get(sourceIndex).toInt() and 0xff
            }
        }
        return MiniProgramFrame(
            width = outputWidth,
            height = outputHeight,
            pixels = output,
        )
    }

    private fun validate(input: YPlaneFrameInput) {
        require(input.width > 0) { "width must be positive" }
        require(input.height > 0) { "height must be positive" }
        require(input.rowStride > 0) { "rowStride must be positive" }
        require(input.pixelStride > 0) { "pixelStride must be positive" }
        require(input.cropWidth > 0) { "cropWidth must be positive" }
        require(input.cropHeight > 0) { "cropHeight must be positive" }
        require(input.cropLeft >= 0) { "cropLeft must be non-negative" }
        require(input.cropTop >= 0) { "cropTop must be non-negative" }
        require(input.cropLeft + input.cropWidth <= input.width) { "cropRect must be inside frame" }
        require(input.cropTop + input.cropHeight <= input.height) { "cropRect must be inside frame" }
        require(input.rotationDegrees in setOf(0, 90, 180, 270)) {
            "rotationDegrees must be one of 0, 90, 180, 270"
        }
        val lastRowStart = (input.height - 1) * input.rowStride
        val lastPixelOffset = (input.width - 1) * input.pixelStride
        require(input.yData.limit() > lastRowStart + lastPixelOffset) { "yData is too small for frame strides" }
    }
}
