package com.answercard.grader.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.view.MotionEvent
import android.view.Surface
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
import androidx.core.view.doOnLayout
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
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(context, previewView, executor) {
        var disposed = false
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            if (disposed) return@Runnable
            require(currentOnFrame.value != null || currentAnalyzer.value != null) {
                "CameraPreview requires onFrame or analyzer"
            }
            val cameraProvider = cameraProviderFuture.get()
            previewView.doOnLayout {
                if (disposed) return@doOnLayout
                val targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                val preview = Preview.Builder()
                    .setTargetRotation(targetRotation)
                    .build()
                    .also { useCase -> useCase.surfaceProvider = previewView.surfaceProvider }
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            CameraAnalysisConfig.RequestedResolution,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build()
                val analysisBuilder = ImageAnalysis.Builder()
                    .setTargetRotation(targetRotation)
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
                    .also { useCase ->
                        useCase.setAnalyzer(executor) { image ->
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
                val viewPort = previewView.viewPort ?: return@doOnLayout
                val group = CameraUseCaseGroupFactory.create(preview, analysis, viewPort)
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    context.requireLifecycleOwner(),
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    group,
                )
                fun focusAt(x: Float, y: Float) {
                    if (previewView.width <= 0 || previewView.height <= 0) return
                    val point = previewView.meteringPointFactory.createPoint(x, y)
                    camera.cameraControl.startFocusAndMetering(CameraFocusActions.actionFor(point))
                }
                focusAt(previewView.width / 2f, previewView.height / 2f)
                previewView.setOnTouchListener { view, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        view.performClick()
                        focusAt(event.x, event.y)
                    }
                    true
                }
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            previewView.setOnTouchListener(null)
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
