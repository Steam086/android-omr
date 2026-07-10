package com.answercard.grader.miniprogram

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CameraYPlaneFrameAdapterTest {
    @Test
    fun readsContinuousYPlaneWhenStridesMatchWidth() {
        val frame = CameraYPlaneFrameAdapter.toMiniProgramFrame(
            YPlaneFrameInput(
                width = 3,
                height = 2,
                rowStride = 3,
                pixelStride = 1,
                cropLeft = 0,
                cropTop = 0,
                cropWidth = 3,
                cropHeight = 2,
                rotationDegrees = 0,
                yData = bytes(10, 20, 30, 40, 50, 60),
            ),
        )

        assertFrame(frame, width = 3, height = 2, pixels = intArrayOf(10, 20, 30, 40, 50, 60))
    }

    @Test
    fun skipsRowPaddingWhenRowStrideIsGreaterThanWidth() {
        val frame = CameraYPlaneFrameAdapter.toMiniProgramFrame(
            YPlaneFrameInput(
                width = 3,
                height = 2,
                rowStride = 5,
                pixelStride = 1,
                cropLeft = 0,
                cropTop = 0,
                cropWidth = 3,
                cropHeight = 2,
                rotationDegrees = 0,
                yData = bytes(
                    1, 2, 3, 99, 98,
                    4, 5, 6, 97, 96,
                ),
            ),
        )

        assertFrame(frame, width = 3, height = 2, pixels = intArrayOf(1, 2, 3, 4, 5, 6))
    }

    @Test
    fun readsInterleavedYPlaneWhenPixelStrideIsTwo() {
        val frame = CameraYPlaneFrameAdapter.toMiniProgramFrame(
            YPlaneFrameInput(
                width = 3,
                height = 2,
                rowStride = 6,
                pixelStride = 2,
                cropLeft = 0,
                cropTop = 0,
                cropWidth = 3,
                cropHeight = 2,
                rotationDegrees = 0,
                yData = bytes(
                    7, 70, 8, 80, 9, 90,
                    10, 100, 11, 110, 12, 120,
                ),
            ),
        )

        assertFrame(frame, width = 3, height = 2, pixels = intArrayOf(7, 8, 9, 10, 11, 12))
    }

    @Test
    fun cropsBeforeRotation() {
        val frame = CameraYPlaneFrameAdapter.toMiniProgramFrame(
            YPlaneFrameInput(
                width = 4,
                height = 3,
                rowStride = 4,
                pixelStride = 1,
                cropLeft = 1,
                cropTop = 1,
                cropWidth = 2,
                cropHeight = 2,
                rotationDegrees = 0,
                yData = bytes(
                    1, 2, 3, 4,
                    5, 6, 7, 8,
                    9, 10, 11, 12,
                ),
            ),
        )

        assertFrame(frame, width = 2, height = 2, pixels = intArrayOf(6, 7, 10, 11))
    }

    @Test
    fun rotatesCroppedFrameByNinetyDegreesClockwise() {
        val frame = CameraYPlaneFrameAdapter.toMiniProgramFrame(inputForRotation(rotationDegrees = 90))

        assertFrame(frame, width = 2, height = 3, pixels = intArrayOf(4, 1, 5, 2, 6, 3))
    }

    @Test
    fun rotatesCroppedFrameByOneHundredEightyDegrees() {
        val frame = CameraYPlaneFrameAdapter.toMiniProgramFrame(inputForRotation(rotationDegrees = 180))

        assertFrame(frame, width = 3, height = 2, pixels = intArrayOf(6, 5, 4, 3, 2, 1))
    }

    @Test
    fun rotatesCroppedFrameByTwoHundredSeventyDegreesClockwise() {
        val frame = CameraYPlaneFrameAdapter.toMiniProgramFrame(inputForRotation(rotationDegrees = 270))

        assertFrame(frame, width = 2, height = 3, pixels = intArrayOf(3, 6, 2, 5, 1, 4))
    }

    @Test
    fun rejectsUnsupportedRotationDegrees() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            CameraYPlaneFrameAdapter.toMiniProgramFrame(inputForRotation(rotationDegrees = 45))
        }

        assertEquals("rotationDegrees must be one of 0, 90, 180, 270", error.message)
    }

    @Test
    fun treatsSignedBytesAsUnsignedGrayValues() {
        val frame = CameraYPlaneFrameAdapter.toMiniProgramFrame(
            YPlaneFrameInput(
                width = 2,
                height = 1,
                rowStride = 2,
                pixelStride = 1,
                cropLeft = 0,
                cropTop = 0,
                cropWidth = 2,
                cropHeight = 1,
                rotationDegrees = 0,
                yData = ByteBuffer.wrap(byteArrayOf(200.toByte(), 255.toByte())),
            ),
        )

        assertFrame(frame, width = 2, height = 1, pixels = intArrayOf(200, 255))
    }

    @Test
    fun conversionDoesNotChangeInputBufferPosition() {
        val input = inputForRotation(rotationDegrees = 90)
        input.yData.position(2)

        CameraYPlaneFrameAdapter.toMiniProgramFrame(input)

        assertEquals(2, input.yData.position())
    }

    private fun inputForRotation(rotationDegrees: Int): YPlaneFrameInput =
        YPlaneFrameInput(
            width = 3,
            height = 2,
            rowStride = 3,
            pixelStride = 1,
            cropLeft = 0,
            cropTop = 0,
            cropWidth = 3,
            cropHeight = 2,
            rotationDegrees = rotationDegrees,
            yData = bytes(1, 2, 3, 4, 5, 6),
        )

    private fun assertFrame(
        frame: MiniProgramFrame,
        width: Int,
        height: Int,
        pixels: IntArray,
    ) {
        assertEquals(width, frame.width)
        assertEquals(height, frame.height)
        assertArrayEquals(pixels, frame.pixels)
    }

    private fun bytes(vararg values: Int): ByteBuffer =
        ByteBuffer.wrap(ByteArray(values.size) { index -> values[index].toByte() })
}
