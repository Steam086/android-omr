package com.answercard.grader.miniprogram

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
    val yData: ByteArray,
)

object CameraYPlaneFrameAdapter {
    fun toMiniProgramFrame(input: YPlaneFrameInput): MiniProgramFrame {
        validate(input)
        val cropped = crop(input)
        val rotated = rotate(
            pixels = cropped,
            width = input.cropWidth,
            height = input.cropHeight,
            rotationDegrees = input.rotationDegrees,
        )
        return MiniProgramFrame(
            width = if (input.rotationDegrees == 90 || input.rotationDegrees == 270) input.cropHeight else input.cropWidth,
            height = if (input.rotationDegrees == 90 || input.rotationDegrees == 270) input.cropWidth else input.cropHeight,
            pixels = rotated,
        )
    }

    private fun crop(input: YPlaneFrameInput): IntArray {
        val pixels = IntArray(input.cropWidth * input.cropHeight)
        var outputIndex = 0
        for (row in 0 until input.cropHeight) {
            val sourceRow = input.cropTop + row
            for (column in 0 until input.cropWidth) {
                val sourceColumn = input.cropLeft + column
                val sourceIndex = sourceRow * input.rowStride + sourceColumn * input.pixelStride
                pixels[outputIndex++] = input.yData[sourceIndex].toInt() and 0xff
            }
        }
        return pixels
    }

    private fun rotate(
        pixels: IntArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
    ): IntArray {
        val outputWidth = if (rotationDegrees == 90 || rotationDegrees == 270) height else width
        val outputHeight = if (rotationDegrees == 90 || rotationDegrees == 270) width else height
        val output = IntArray(outputWidth * outputHeight)
        for (row in 0 until height) {
            for (column in 0 until width) {
                val value = pixels[row * width + column]
                val target = when (rotationDegrees) {
                    0 -> row to column
                    90 -> column to (height - 1 - row)
                    180 -> (height - 1 - row) to (width - 1 - column)
                    270 -> (width - 1 - column) to row
                    else -> error("unsupported rotation")
                }
                output[target.first * outputWidth + target.second] = value
            }
        }
        return output
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
        require(input.yData.size > lastRowStart + lastPixelOffset) { "yData is too small for frame strides" }
    }
}
