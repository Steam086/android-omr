package com.answercard.grader.camera

import android.util.Rational
import android.view.Surface
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.ViewPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraUseCaseGroupFactoryTest {
    @Test
    fun groupContainsPreviewAnalysisAndSharedViewPort() {
        val preview = Preview.Builder().build()
        val analysis = ImageAnalysis.Builder().build()
        val viewPort = ViewPort.Builder(Rational(3, 4), Surface.ROTATION_0).build()

        val group = CameraUseCaseGroupFactory.create(preview, analysis, viewPort)

        assertEquals(listOf(preview, analysis), group.useCases)
        assertSame(viewPort, group.viewPort)
    }
}
