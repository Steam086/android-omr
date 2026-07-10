package com.answercard.grader.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFrame: ((Bitmap) -> Unit)? = null,
    analyzer: ImageAnalysis.Analyzer? = null,
    captureMetadataTracker: CameraCaptureMetadataTracker? = null,
) {
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val currentOnFrame = rememberUpdatedState(onFrame)
    val currentAnalyzer = rememberUpdatedState(analyzer)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            // FIT_CENTER letterboxes instead of cropping, so the user sees the
            // full frame that the analyzer receives and can aim truthfully.
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    DisposableEffect(context, previewView, executor) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            require(currentOnFrame.value != null || currentAnalyzer.value != null) {
                "CameraPreview requires onFrame or analyzer"
            }
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        CameraAnalysisConfig.RequestedResolution,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build()
            val analysisBuilder = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            val camera2 = Camera2Interop.Extender(analysisBuilder)
            camera2.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                CameraAnalysisConfig.TargetFrameRateRange,
            )
            camera2.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            )
            camera2.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON,
            )
            captureMetadataTracker?.let { tracker ->
                camera2.setSessionCaptureCallback(
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult,
                        ) {
                            tracker.record(result)
                        }
                    },
                )
            }
            val analysis = analysisBuilder
                .build()
                .also {
                    it.setAnalyzer(executor) { image ->
                        val analyzerForPreview = currentAnalyzer.value
                        if (analyzerForPreview != null) {
                            analyzerForPreview.analyze(image)
                        } else {
                            try {
                                currentOnFrame.value?.invoke(ImageProxyBitmap.grayscaleBitmap(image))
                            } finally {
                                image.close()
                            }
                        }
                    }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                context.requireLifecycleOwner(),
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            captureMetadataTracker?.clear()
            ProcessCameraProvider.getInstance(context).get().unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun Context.requireLifecycleOwner(): LifecycleOwner =
    generateSequence(this) { current ->
        if (current is android.content.ContextWrapper) current.baseContext else null
    }.filterIsInstance<LifecycleOwner>().first()
