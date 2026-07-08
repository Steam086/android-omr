package com.answercard.grader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.answercard.grader.camera.CameraAnalysisConfig
import com.answercard.grader.camera.CameraPreview
import com.answercard.grader.camera.DeviceStabilityMonitor
import com.answercard.grader.miniprogram.AndroidOmrAnalyzerOptions
import com.answercard.grader.miniprogram.AndroidOmrImageAnalyzer
import com.answercard.grader.miniprogram.OmrAnalysisOrientationMode
import com.answercard.grader.miniprogram.ScanConsensusDecision
import com.answercard.grader.miniprogram.ScanConsensusTracker
import com.answercard.grader.records.ScanRecord
import com.answercard.grader.records.ScanRecordStore
import com.answercard.grader.speech.ScoreSpeaker
import com.answercard.grader.speech.ScoreSpeechText
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState
import com.answercard.grader.vision.OmrScanner
import java.time.LocalDateTime
import kotlin.math.roundToInt

private const val USE_NEW_ANDROID_OMR_ANALYZER = true

@Composable
fun ScanScreen(
    templateId: String,
    template: TemplateState,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val context = LocalContext.current
    val recordStore = remember { ScanRecordStore(context.applicationContext) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val speaker = remember { ScoreSpeaker(context.applicationContext) }
    var soundEnabled by remember { mutableStateOf(true) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
    }
    var lastScanAt by remember { mutableLongStateOf(0L) }
    var lastHandledKey by remember { mutableStateOf<String?>(null) }
    var displayResult by remember { mutableStateOf<ScanDisplayResult?>(null) }
    var lockedScoreText by remember { mutableStateOf<String?>(null) }
    var deviceStable by remember { mutableStateOf(true) }
    var stabilityGateEnabled by rememberSaveable { mutableStateOf(true) }
    val currentStabilityGateEnabled = rememberUpdatedState(stabilityGateEnabled)
    val consensusTracker = remember(template) { ScanConsensusTracker() }
    val stabilityMonitor = remember {
        DeviceStabilityMonitor(context) { stable ->
            mainHandler.post { deviceStable = stable }
        }
    }
    DisposableEffect(stabilityMonitor) {
        stabilityMonitor.start()
        onDispose { stabilityMonitor.stop() }
    }
    var status by remember { mutableStateOf("Scanning") }
    val currentSoundEnabled = rememberUpdatedState(soundEnabled)
    val currentTemplateId = rememberUpdatedState(templateId)
    var analysisOrientationModeName by rememberSaveable {
        mutableStateOf(OmrAnalysisOrientationMode.LANDSCAPE_TEMPLATE.name)
    }
    val analysisOrientationMode = remember(analysisOrientationModeName) {
        OmrAnalysisOrientationMode.valueOf(analysisOrientationModeName)
    }
    val layout = remember(template) { TemplateGeometry.buildLayout(template) }
    val androidOmrAnalyzer = remember(template, analysisOrientationMode) {
        AndroidOmrImageAnalyzer(
            templateProvider = { template },
            onResult = { result ->
                mainHandler.post {
                    displayResult = ScanDisplayResult.fromAndroidOmrResult(result)
                    status = if (result.success) "Recognized" else "Not recognized"
                    when (val decision = consensusTracker.offer(result)) {
                        is ScanConsensusDecision.Locked -> {
                            val score = decision.result.score
                            val examId = decision.result.admissionNumber?.digits.orEmpty()
                            val examIdReady = examId.isNotBlank() || !template.showHeader
                            if (score != null && examIdReady) {
                                lockedScoreText = "${score.totalScore.roundToInt()}/${score.maxScore.roundToInt()}"
                                recordStore.saveRecord(
                                    ScanRecord(
                                        templateId = currentTemplateId.value,
                                        templateName = template.name,
                                        examId = examId,
                                        totalScore = score.totalScore.roundToInt(),
                                        maxScore = score.maxScore.roundToInt(),
                                        scannedAt = LocalDateTime.now(),
                                    ),
                                )
                                if (currentSoundEnabled.value) {
                                    speaker.speak(
                                        ScoreSpeechText.build(
                                            totalScore = score.totalScore.roundToInt(),
                                            maxScore = score.maxScore.roundToInt(),
                                            examId = examId,
                                        ),
                                    )
                                }
                            }
                        }
                        is ScanConsensusDecision.Pending,
                        is ScanConsensusDecision.AlreadyLocked,
                        -> Unit
                    }
                }
            },
            onError = { error ->
                mainHandler.post {
                    displayResult = ScanDisplayResult(
                        isRecognized = false,
                        examId = null,
                        scoreText = null,
                        failureReason = error.message ?: error::class.java.simpleName,
                        friendlyMessage = null,
                        debugInfo = emptyList(),
                    )
                    status = "Recognition error"
                }
            },
            options = AndroidOmrAnalyzerOptions(
                analysisOrientationMode = analysisOrientationMode,
                requestedAnalysisResolutionLabel = CameraAnalysisConfig.RequestedResolutionLabel,
            ),
            stabilityGate = { !currentStabilityGateEnabled.value || stabilityMonitor.isStable() },
        )
    }

    DisposableEffect(speaker) {
        onDispose { speaker.shutdown() }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = UiTokens.StatusBarHeight, start = 12.dp, end = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
            Text(template.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        analysisOrientationModeName = analysisOrientationMode.next().name
                    },
                ) {
                    Text("Direction: ${analysisOrientationMode.label()}")
                }
                OutlinedButton(onClick = { stabilityGateEnabled = !stabilityGateEnabled }) {
                    Text(if (stabilityGateEnabled) "Steady: On" else "Steady: Off")
                }
                OutlinedButton(onClick = { soundEnabled = !soundEnabled }) {
                    Text(if (soundEnabled) "Sound" else "Silent")
                }
                Button(onClick = onOpenSetup) {
                    Text("Answers")
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (hasCameraPermission) {
                if (USE_NEW_ANDROID_OMR_ANALYZER) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        analyzer = androidOmrAnalyzer,
                    )
                } else {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onFrame = { bitmap ->
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastScanAt >= 700L) {
                                lastScanAt = now
                                val result = OmrScanner.scan(bitmap, template, layout, scale = 3f)
                                mainHandler.post {
                                    displayResult = ScanDisplayResult.fromLegacyResult(result)
                                    status = if (result == null) "Not recognized" else "Recognized"
                                    if (result != null) {
                                        val handledKey =
                                            "${result.examId.orEmpty()}:${result.grade.totalScore}/${result.grade.maxScore}"
                                        if (handledKey != lastHandledKey) {
                                            lastHandledKey = handledKey
                                            if (!result.examId.isNullOrBlank()) {
                                                recordStore.saveRecord(
                                                    ScanRecord(
                                                        templateId = templateId,
                                                        templateName = template.name,
                                                        examId = result.examId,
                                                        totalScore = result.grade.totalScore,
                                                        maxScore = result.grade.maxScore,
                                                        scannedAt = LocalDateTime.now(),
                                                    ),
                                                )
                                            }
                                            if (soundEnabled) {
                                                speaker.speak(
                                                    ScoreSpeechText.build(
                                                        totalScore = result.grade.totalScore,
                                                        maxScore = result.grade.maxScore,
                                                        examId = result.examId,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            } else {
                PermissionPrompt(onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) })
            }

            lockedScoreText?.let { locked ->
                Text(
                    text = locked,
                    color = Color.White,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                )
            }
            if (stabilityGateEnabled && !deviceStable) {
                Text(
                    text = "Hold steady…",
                    color = Color.Yellow,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            ScanStatusPanel(
                status = status,
                result = displayResult,
                soundEnabled = soundEnabled,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}

private fun OmrAnalysisOrientationMode.next(): OmrAnalysisOrientationMode =
    when (this) {
        OmrAnalysisOrientationMode.LANDSCAPE_TEMPLATE -> OmrAnalysisOrientationMode.FOLLOW_IMAGE_ROTATION
        OmrAnalysisOrientationMode.FOLLOW_IMAGE_ROTATION -> OmrAnalysisOrientationMode.PORTRAIT_TEMPLATE
        OmrAnalysisOrientationMode.PORTRAIT_TEMPLATE -> OmrAnalysisOrientationMode.LANDSCAPE_TEMPLATE
    }

private fun OmrAnalysisOrientationMode.label(): String =
    when (this) {
        OmrAnalysisOrientationMode.LANDSCAPE_TEMPLATE -> "Landscape"
        OmrAnalysisOrientationMode.FOLLOW_IMAGE_ROTATION -> "Follow"
        OmrAnalysisOrientationMode.PORTRAIT_TEMPLATE -> "Portrait"
    }

@Composable
private fun PermissionPrompt(onRequestPermission: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onRequestPermission) {
            Text("Enable camera")
        }
    }
}

@Composable
private fun ScanStatusPanel(
    status: String,
    result: ScanDisplayResult?,
    soundEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(color = Color(0xCC000000), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(status, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(if (soundEnabled) "Sound: on" else "Sound: off", color = Color.White)
            if (result != null) {
                Text("Recognized: ${if (result.isRecognized) "yes" else "no"}", color = Color.White)
                result.scoreText?.let { Text("Score: $it", color = Color.White) }
                Text("Admission: ${result.examId ?: "blank"}", color = Color.White)
                result.friendlyMessage?.let { Text(it, color = Color.White) }
                result.failureReason?.let { Text("Failure: $it", color = Color.White) }
                result.debugInfo.take(24).forEach { Text(it, color = Color.White) }
            }
        }
    }
}
