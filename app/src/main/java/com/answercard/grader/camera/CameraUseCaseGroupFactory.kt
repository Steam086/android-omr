package com.answercard.grader.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort

object CameraUseCaseGroupFactory {
    fun create(
        preview: Preview,
        analysis: ImageAnalysis,
        viewPort: ViewPort,
    ): UseCaseGroup =
        UseCaseGroup.Builder()
            .setViewPort(viewPort)
            .addUseCase(preview)
            .addUseCase(analysis)
            .build()
}
