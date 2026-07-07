package com.answercard.grader.miniprogram

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraImageProxyFrameAdapterTest {
    @Test
    fun landscapeTemplateModeKeepsLandscapeFrameWhenCameraRotationWouldMakeItPortrait() {
        val image = FakeImageProxy(width = 640, height = 480, rotationDegrees = 90)

        val frame = CameraImageProxyFrameAdapter.fromImageProxy(
            image = image,
            orientationMode = OmrAnalysisOrientationMode.LANDSCAPE_TEMPLATE,
        )

        assertEquals(640, frame.width)
        assertEquals(480, frame.height)
    }

    @Test
    fun followImageRotationModePreservesCameraXRotationBehavior() {
        val image = FakeImageProxy(width = 640, height = 480, rotationDegrees = 90)

        val frame = CameraImageProxyFrameAdapter.fromImageProxy(
            image = image,
            orientationMode = OmrAnalysisOrientationMode.FOLLOW_IMAGE_ROTATION,
        )

        assertEquals(480, frame.width)
        assertEquals(640, frame.height)
    }

    @Test
    fun portraitTemplateModeKeepsPortraitFrame() {
        val image = FakeImageProxy(width = 640, height = 480, rotationDegrees = 0)

        val frame = CameraImageProxyFrameAdapter.fromImageProxy(
            image = image,
            orientationMode = OmrAnalysisOrientationMode.PORTRAIT_TEMPLATE,
        )

        assertEquals(480, frame.width)
        assertEquals(640, frame.height)
    }

    private class FakeImageProxy(
        private val width: Int,
        private val height: Int,
        private val rotationDegrees: Int,
    ) : ImageProxy {
        private val crop = rect(0, 0, width, height)
        private val buffer = ByteBuffer.wrap(ByteArray(width * height) { 255.toByte() })

        override fun close() = Unit

        override fun getCropRect(): Rect = crop

        override fun setCropRect(rect: Rect?) = Unit

        override fun getFormat(): Int = 0

        override fun getHeight(): Int = height

        override fun getWidth(): Int = width

        override fun getPlanes(): Array<ImageProxy.PlaneProxy> = arrayOf(FakePlaneProxy(width, buffer))

        override fun getImageInfo(): ImageInfo = FakeImageInfo(rotationDegrees)

        override fun getImage(): Image? = null

        override fun toBitmap(): Bitmap {
            throw UnsupportedOperationException("not used")
        }
    }

    private class FakePlaneProxy(
        private val rowStride: Int,
        private val buffer: ByteBuffer,
    ) : ImageProxy.PlaneProxy {
        override fun getRowStride(): Int = rowStride

        override fun getPixelStride(): Int = 1

        override fun getBuffer(): ByteBuffer = buffer.duplicate()
    }

    private class FakeImageInfo(
        private val rotationDegrees: Int,
    ) : ImageInfo {
        override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()

        override fun getTimestamp(): Long = 0L

        override fun getRotationDegrees(): Int = rotationDegrees

        override fun populateExifData(builder: ExifData.Builder) = Unit
    }

    private companion object {
        fun rect(left: Int, top: Int, right: Int, bottom: Int): Rect =
            Rect().also {
                it.left = left
                it.top = top
                it.right = right
                it.bottom = bottom
            }
    }
}
