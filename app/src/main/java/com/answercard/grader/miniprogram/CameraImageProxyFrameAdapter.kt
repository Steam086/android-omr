package com.answercard.grader.miniprogram

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

object CameraImageProxyFrameAdapter {
    fun fromImageProxy(
        image: ImageProxy,
        orientationMode: OmrAnalysisOrientationMode = OmrAnalysisOrientationMode.LANDSCAPE_TEMPLATE,
    ): MiniProgramFrame {
        require(image.planes.isNotEmpty()) { "ImageProxy does not contain a Y plane" }

        val yPlane = image.planes[0]
        val cropRect = image.cropRect
        val cropWidth = cropRect.right - cropRect.left
        val cropHeight = cropRect.bottom - cropRect.top
        return CameraYPlaneFrameAdapter.toMiniProgramFrame(
            YPlaneFrameInput(
                width = image.width,
                height = image.height,
                rowStride = yPlane.rowStride,
                pixelStride = yPlane.pixelStride,
                cropLeft = cropRect.left,
                cropTop = cropRect.top,
                cropWidth = cropWidth,
                cropHeight = cropHeight,
                rotationDegrees = effectiveRotationDegrees(
                    imageRotationDegrees = image.imageInfo.rotationDegrees,
                    cropWidth = cropWidth,
                    cropHeight = cropHeight,
                    orientationMode = orientationMode,
                ),
                yData = yPlane.buffer.toByteArrayFromStart(),
            ),
        )
    }

    fun analysisOrientation(orientationMode: OmrAnalysisOrientationMode): String =
        when (orientationMode) {
            OmrAnalysisOrientationMode.LANDSCAPE_TEMPLATE -> "landscape-template"
            OmrAnalysisOrientationMode.FOLLOW_IMAGE_ROTATION -> "follow-image-rotation"
            OmrAnalysisOrientationMode.PORTRAIT_TEMPLATE -> "portrait-template"
        }

    private fun effectiveRotationDegrees(
        imageRotationDegrees: Int,
        cropWidth: Int,
        cropHeight: Int,
        orientationMode: OmrAnalysisOrientationMode,
    ): Int =
        when (orientationMode) {
            OmrAnalysisOrientationMode.FOLLOW_IMAGE_ROTATION -> imageRotationDegrees
            OmrAnalysisOrientationMode.LANDSCAPE_TEMPLATE ->
                if (rotatedWidth(cropWidth, cropHeight, imageRotationDegrees) >=
                    rotatedHeight(cropWidth, cropHeight, imageRotationDegrees)
                ) {
                    imageRotationDegrees
                } else {
                    rotationForLandscape(cropWidth, cropHeight)
                }
            OmrAnalysisOrientationMode.PORTRAIT_TEMPLATE ->
                if (rotatedHeight(cropWidth, cropHeight, imageRotationDegrees) >=
                    rotatedWidth(cropWidth, cropHeight, imageRotationDegrees)
                ) {
                    imageRotationDegrees
                } else {
                    rotationForPortrait(cropWidth, cropHeight)
                }
        }

    private fun rotationForLandscape(width: Int, height: Int): Int =
        if (width >= height) 0 else 90

    private fun rotationForPortrait(width: Int, height: Int): Int =
        if (height >= width) 0 else 90

    private fun rotatedWidth(width: Int, height: Int, rotationDegrees: Int): Int =
        if (rotationDegrees == 90 || rotationDegrees == 270) height else width

    private fun rotatedHeight(width: Int, height: Int, rotationDegrees: Int): Int =
        if (rotationDegrees == 90 || rotationDegrees == 270) width else height

    private fun ByteBuffer.toByteArrayFromStart(): ByteArray {
        val copy = duplicate()
        copy.rewind()
        return ByteArray(copy.remaining()).also(copy::get)
    }
}
