package com.answercard.grader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.answercard.grader.camera.CameraAnalysisConfig
import com.answercard.grader.camera.CameraCaptureMetadataTracker
import com.answercard.grader.camera.CameraPreview
import com.answercard.grader.camera.DeviceStabilityMonitor
import com.answercard.grader.miniprogram.AnchorMode
import com.answercard.grader.miniprogram.AndroidOmrAnalyzerOptions
import com.answercard.grader.miniprogram.AndroidOmrFrameProcessor
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
    var lockedScoreText by remember(template) { mutableStateOf<String?>(null) }
    var deviceStable by remember { mutableStateOf(true) }
    var anchorModeName by rememberSaveable { mutableStateOf(AnchorMode.CODED_ONLY.name) }
    val anchorMode = remember(anchorModeName) { AnchorMode.valueOf(anchorModeName) }
    val consensusTracker = remember(template, anchorMode) { ScanConsensusTracker() }
    val captureMetadataTracker = remember { CameraCaptureMetadataTracker() }
    val stabilityMonitor = remember {
        DeviceStabilityMonitor(context) { stable ->
            mainHandler.post { deviceStable = stable }
        }
    }
    DisposableEffect(stabilityMonitor) {
        stabilityMonitor.start()
        onDispose { stabilityMonitor.stop() }
    }
    var status by remember { mutableStateOf("扫描中") }
    val currentSoundEnabled = rememberUpdatedState(soundEnabled)
    val currentTemplateId = rememberUpdatedState(templateId)
    var analysisOrientationModeName by rememberSaveable {
        mutableStateOf(OmrAnalysisOrientationMode.LANDSCAPE_TEMPLATE.name)
    }
    val analysisOrientationMode = remember(analysisOrientationModeName) {
        OmrAnalysisOrientationMode.valueOf(analysisOrientationModeName)
    }
    val layout = remember(template) { TemplateGeometry.buildLayout(template) }
    val androidOmrAnalyzer = remember(template, analysisOrientationMode, anchorMode) {
        AndroidOmrImageAnalyzer(
            templateProvider = { template },
            onResult = { result ->
                mainHandler.post {
                    when (val decision = consensusTracker.offer(result)) {
                        is ScanConsensusDecision.Locked -> {
                            val score = decision.result.score
                            val examId = decision.result.admissionNumber?.digits.orEmpty()
                            val examIdReady = examId.isNotBlank() || !template.showHeader
                            if (score != null && examIdReady) {
                                displayResult = ScanDisplayResult.fromAndroidOmrResult(decision.result, template)
                                status = "已识别"
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
                        is ScanConsensusDecision.Pending -> {
                            val rejectedDisplay = result.rejectionReason?.let {
                                ScanDisplayResult.fromAndroidOmrResult(result, template)
                            }
                            if (lockedScoreText == null) {
                                if (rejectedDisplay != null) displayResult = rejectedDisplay
                                status = when {
                                    rejectedDisplay?.friendlyMessage != null -> rejectedDisplay.friendlyMessage
                                    decision.streak > 0 -> "确认中 ${decision.streak}/${decision.required}"
                                    else -> "未识别"
                                }
                            } else if (rejectedDisplay?.friendlyMessage != null) {
                                status = "已锁定；${rejectedDisplay.friendlyMessage}"
                            }
                        }
                        is ScanConsensusDecision.AlreadyLocked -> status = "已识别"
                        ScanConsensusDecision.CardCleared -> {
                            displayResult = null
                            lockedScoreText = null
                            status = "扫描中"
                        }
                    }
                }
            },
            onError = {
                mainHandler.post {
                    if (lockedScoreText == null) status = "识别出错"
                }
            },
            options = AndroidOmrAnalyzerOptions(
                minAnalyzeIntervalMs = 250L,
                candidateWindowMs = 0L,
                analysisOrientationMode = analysisOrientationMode,
                requestedAnalysisResolutionLabel = CameraAnalysisConfig.RequestedResolutionLabel,
                minimumAnalysisResolution = CameraAnalysisConfig.MinimumResolution,
            ),
            processor = AndroidOmrFrameProcessor(anchorMode = anchorMode),
            captureMetadataProvider = captureMetadataTracker::metadataFor,
            isDeviceStableProvider = { stabilityMonitor.isStable() },
        )
    }

    DisposableEffect(speaker) {
        onDispose { speaker.shutdown() }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 4.dp, start = 12.dp, end = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScanChromeButton("返回", onClick = onBack)
                Text(
                    template.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScanChromeButton("方向：${analysisOrientationMode.label()}") {
                    analysisOrientationModeName = analysisOrientationMode.next().name
                }
                ScanChromeButton(if (anchorMode == AnchorMode.CODED_ONLY) "卡片：新版" else "卡片：旧版") {
                    anchorModeName = if (anchorMode == AnchorMode.CODED_ONLY) {
                        AnchorMode.LEGACY.name
                    } else {
                        AnchorMode.CODED_ONLY.name
                    }
                    consensusTracker.reset()
                    captureMetadataTracker.clear()
                    displayResult = null
                    lockedScoreText = null
                    status = "扫描中"
                }
                ScanChromeButton(if (soundEnabled) "声音：开" else "声音：关") {
                    soundEnabled = !soundEnabled
                }
                Button(
                    onClick = onOpenSetup,
                    colors = ButtonDefaults.buttonColors(containerColor = UiTokens.Green, contentColor = Color.White),
                ) {
                    Text("答案")
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .background(Color.Black),
            ) {
                if (hasCameraPermission) {
                    if (USE_NEW_ANDROID_OMR_ANALYZER) {
                        CameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            analyzer = androidOmrAnalyzer,
                            captureMetadataTracker = captureMetadataTracker,
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
                                        status = if (result == null) "未识别" else "已识别"
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
                    ScanViewfinderGuide(template, Modifier.fillMaxSize())
                } else {
                    PermissionPrompt(onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) })
                }

                lockedScoreText?.let { locked ->
                    Text(
                        text = locked,
                        color = Color.White,
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 24.dp)
                            .background(Color(0x99000000), RoundedCornerShape(16.dp))
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                if (!deviceStable) {
                    Text(
                        text = "请持稳手机…",
                        color = Color.Yellow,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color(0x99000000), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            if (hasCameraPermission) {
                ScanStatusPanel(
                    template = template,
                    status = status,
                    result = displayResult,
                    soundEnabled = soundEnabled,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
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
        OmrAnalysisOrientationMode.LANDSCAPE_TEMPLATE -> "横版"
        OmrAnalysisOrientationMode.FOLLOW_IMAGE_ROTATION -> "跟随"
        OmrAnalysisOrientationMode.PORTRAIT_TEMPLATE -> "竖版"
    }

@Composable
private fun ScanChromeButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
    ) {
        Text(label, maxLines = 1)
    }
}

@Composable
private fun PermissionPrompt(onRequestPermission: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text("需要相机权限才能扫描答题卡", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(
                "如未弹出授权窗口，请前往系统设置开启相机权限",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = UiTokens.Green, contentColor = Color.White),
            ) {
                Text("开启相机")
            }
        }
    }
}

@Composable
private fun ScanStatusPanel(
    template: TemplateState,
    status: String,
    result: ScanDisplayResult?,
    soundEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(color = Color(0xCC000000), modifier = modifier) {
        Column(
            Modifier
                .navigationBarsPadding()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    status,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                result?.scoreText?.let {
                    Text("分数：$it", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
                Text(if (soundEnabled) "声音：开" else "声音：关", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "轻触卡片可对焦",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (result?.isRecognized == true) {
                ScanResultTemplateView(
                    template = template,
                    result = result,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp),
                )
            }
            if (result != null) {
                Text("考号：${result.examId ?: "无"}", color = Color.White)
                result.friendlyMessage?.let { Text(it, color = Color.White) }
                result.failureReason?.let { Text("失败原因：$it", color = Color.White) }
            }
        }
    }
}
