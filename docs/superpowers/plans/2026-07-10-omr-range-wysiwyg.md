# OMR Range and WYSIWYG Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make portrait camera scanning show exactly the sensor crop analyzed by OMR while widening safe near/far recognition without allowing clipped or low-information answer cells to produce scores.

**Architecture:** CameraX `Preview` and `ImageAnalysis` share `PreviewView.viewPort` in one `UseCaseGroup`, displayed in an unobscured 3:4 viewfinder. The scanner requests 1920×1440, reports/rejects genuinely low actual resolutions, samples projective cells into a fixed 16×16 grid after validating raw source information, and permits one inferred coded marker outside the frame only when every required cell remains safely inside.

**Tech Stack:** Kotlin 2.2.20, Android CameraX 1.6.0, Jetpack Compose, JUnit 4, Robolectric, pure JVM `omr-core`, Gradle/Android SDK on Fedora only.

## Global Constraints

- Keep `MainActivity` portrait-only.
- Keep `omr-core` free of Android, AndroidX, Compose, CameraX, and `android.graphics` imports.
- Keep legacy scanner and `AnchorMode.LEGACY`; border relaxation applies only to coded markers inferred from exactly three IDs.
- Every rejected frame must have `success=false` and `score=null`.
- Do not change printed template geometry or coded-marker payloads.
- Process newest frames at 250ms cadence with `STRATEGY_KEEP_ONLY_LATEST`.
- Do not download an Android SDK locally; run Gradle only on Fedora at `/data/Sources/android-omr-codex-wysiwyg-019f4d09` using JDK 21 and `/data/local/android-sdk`.
- Before every remote RED/GREEN command, copy each locally changed source/test file to the same relative path in the dedicated Fedora clone with `scp -P 22`; never modify `/data/Sources/android-omr`.

---

### Task 1: P0 Shared Camera ViewPort

**Files:**
- Create: `app/src/main/java/com/answercard/grader/camera/CameraUseCaseGroupFactory.kt`
- Modify: `app/src/main/java/com/answercard/grader/camera/CameraPreview.kt`
- Test: `app/src/test/java/com/answercard/grader/camera/CameraUseCaseGroupFactoryTest.kt`

**Interfaces:**
- Consumes: CameraX `Preview`, `ImageAnalysis`, and non-null `ViewPort`.
- Produces: `CameraUseCaseGroupFactory.create(preview, analysis, viewPort): UseCaseGroup` containing both use cases and the shared viewport.

- [ ] **Step 1: Write the failing group-factory test**

```kotlin
@Test
fun groupContainsPreviewAnalysisAndSharedViewPort() {
    val preview = Preview.Builder().build()
    val analysis = ImageAnalysis.Builder().build()
    val viewPort = ViewPort.Builder(Rational(3, 4), Surface.ROTATION_0).build()

    val group = CameraUseCaseGroupFactory.create(preview, analysis, viewPort)

    assertEquals(listOf(preview, analysis), group.useCases)
    assertSame(viewPort, group.viewPort)
}
```

- [ ] **Step 2: Copy the test to Fedora and verify RED**

Run:

```sh
scp -P 22 app/src/test/java/com/answercard/grader/camera/CameraUseCaseGroupFactoryTest.kt fedora:/data/Sources/android-omr-codex-wysiwyg-019f4d09/app/src/test/java/com/answercard/grader/camera/
ssh -p 22 fedora 'env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/data/local/android-sdk ANDROID_SDK_ROOT=/data/local/android-sdk sh /data/Sources/android-omr-codex-wysiwyg-019f4d09/gradlew -p /data/Sources/android-omr-codex-wysiwyg-019f4d09 :app:testDebugUnitTest --tests com.answercard.grader.camera.CameraUseCaseGroupFactoryTest'
```

Expected: FAIL because `CameraUseCaseGroupFactory` does not exist.

- [ ] **Step 3: Implement the factory**

```kotlin
object CameraUseCaseGroupFactory {
    fun create(preview: Preview, analysis: ImageAnalysis, viewPort: ViewPort): UseCaseGroup =
        UseCaseGroup.Builder()
            .setViewPort(viewPort)
            .addUseCase(preview)
            .addUseCase(analysis)
            .build()
}
```

- [ ] **Step 4: Bind only after PreviewView layout**

In `CameraPreview.kt`:

```kotlin
scaleType = PreviewView.ScaleType.FILL_CENTER
```

Inside the provider listener, wrap use-case construction and binding in `previewView.doOnLayout` and use one rotation:

```kotlin
val targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
val preview = Preview.Builder().setTargetRotation(targetRotation).build().also {
    it.surfaceProvider = previewView.surfaceProvider
}
val analysis = analysisBuilder.setTargetRotation(targetRotation).build().also { /* analyzer */ }
val viewPort = previewView.viewPort ?: return@doOnLayout
val group = CameraUseCaseGroupFactory.create(preview, analysis, viewPort)
cameraProvider.unbindAll()
cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
```

- [ ] **Step 5: Copy production files and verify GREEN plus camera tests**

Run the same targeted test plus `CameraYPlaneFrameAdapterTest` and `CameraImageProxyFrameAdapterTest`. Expected: PASS.

- [ ] **Step 6: Commit**

```sh
git add app/src/main/java/com/answercard/grader/camera/CameraUseCaseGroupFactory.kt app/src/main/java/com/answercard/grader/camera/CameraPreview.kt app/src/test/java/com/answercard/grader/camera/CameraUseCaseGroupFactoryTest.kt
git commit -m "fix(camera): share preview and analysis viewport"
```

### Task 2: P0 Unobscured 3:4 Viewfinder and Guide

**Files:**
- Create: `app/src/main/java/com/answercard/grader/ui/ScanGuideGeometry.kt`
- Create: `app/src/main/java/com/answercard/grader/ui/ScanViewfinderGuide.kt`
- Modify: `app/src/main/java/com/answercard/grader/ui/ScanScreen.kt`
- Test: `app/src/test/java/com/answercard/grader/ui/ScanGuideGeometryTest.kt`

**Interfaces:**
- Produces: `ScanGuideGeometry.calculate(viewWidth, viewHeight, cardAspectRatio, insetFraction): ScanGuideRect`.
- Produces: `ScanViewfinderGuide(template, modifier)` rendering only a recommendation frame, never an analysis ROI.

- [ ] **Step 1: Write failing guide geometry tests**

```kotlin
@Test
fun wideCardGuideFitsInsidePortraitViewfinder() {
    val rect = ScanGuideGeometry.calculate(900f, 1200f, cardAspectRatio = 2f)
    assertEquals(810f, rect.width, 0.01f)
    assertEquals(405f, rect.height, 0.01f)
    assertEquals(45f, rect.left, 0.01f)
    assertEquals(397.5f, rect.top, 0.01f)
}

@Test
fun tallTemplateGuideIsLimitedByAvailableHeight() {
    val rect = ScanGuideGeometry.calculate(900f, 1200f, cardAspectRatio = 0.6f)
    assertTrue(rect.left >= 0f)
    assertTrue(rect.top >= 0f)
    assertTrue(rect.right <= 900f)
    assertTrue(rect.bottom <= 1200f)
}
```

- [ ] **Step 2: Verify RED on Fedora**

Expected: FAIL because `ScanGuideGeometry` is absent.

- [ ] **Step 3: Implement centered fit geometry**

```kotlin
object ScanGuideGeometry {
    fun calculate(
        viewWidth: Float,
        viewHeight: Float,
        cardAspectRatio: Float,
        insetFraction: Float = 0.05f,
    ): ScanGuideRect {
        val maxWidth = viewWidth * (1f - insetFraction * 2f)
        val maxHeight = viewHeight * (1f - insetFraction * 2f)
        val width = minOf(maxWidth, maxHeight * cardAspectRatio)
        val height = width / cardAspectRatio
        val left = (viewWidth - width) / 2f
        val top = (viewHeight - height) / 2f
        return ScanGuideRect(left, top, left + width, top + height)
    }
}
```

- [ ] **Step 4: Implement the Compose guide and non-overlapping layout**

In `ScanScreen`, replace the camera/status overlay box with a column:

```kotlin
Column(Modifier.weight(1f).fillMaxWidth()) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .background(Color.Black),
    ) {
        CameraPreview(Modifier.fillMaxSize(), analyzer = androidOmrAnalyzer, captureMetadataTracker = tracker)
        ScanViewfinderGuide(template, Modifier.fillMaxSize())
        // score and stability overlays remain inside the camera area
    }
    ScanStatusPanel(
        template = template,
        status = status,
        result = displayResult,
        soundEnabled = soundEnabled,
        modifier = Modifier.weight(1f).fillMaxWidth(),
    )
}
```

Only show `ScanResultTemplateView` when `result?.isRecognized == true`; scanning/rejection states remain compact text below the viewfinder.

- [ ] **Step 5: Verify GREEN and UI unit tests on Fedora**

Expected: guide tests and existing `ScanDisplayResultTest` pass; `:app:compileDebugKotlin` succeeds.

- [ ] **Step 6: Commit**

```sh
git add app/src/main/java/com/answercard/grader/ui app/src/test/java/com/answercard/grader/ui/ScanGuideGeometryTest.kt
git commit -m "fix(scan): keep status outside camera viewfinder"
```

### Task 3: P1 Actual Resolution Contract

**Files:**
- Modify: `app/src/main/java/com/answercard/grader/camera/CameraAnalysisConfig.kt`
- Modify: `app/src/main/java/com/answercard/grader/ui/ScanScreen.kt`
- Modify: `app/src/main/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzer.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidOmrAnalyzerOptions.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/ScanSafety.kt`
- Modify: `app/src/main/java/com/answercard/grader/ui/ScanDisplayResult.kt`
- Test: `app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzerTest.kt`
- Test: `app/src/test/java/com/answercard/grader/ui/ScanDisplayResultTest.kt`

**Interfaces:**
- Adds `AnalysisResolution(width: Int, height: Int)` and `AndroidOmrAnalyzerOptions.minimumAnalysisResolution`.
- Adds `ScanRejectionReason.RETAKE_LOW_RESOLUTION`.

- [ ] **Step 1: Add failing analyzer and message tests**

```kotlin
@Test
fun rejectsActualFrameBelowMinimumResolutionBeforeOmr() {
    val options = AndroidOmrAnalyzerOptions(
        minimumAnalysisResolution = AnalysisResolution(1280, 960),
        enableFrameQualityGate = false,
    )
    // Analyze a 640x480 FakeImageProxy.
    assertEquals(ScanRejectionReason.RETAKE_LOW_RESOLUTION, received?.rejectionReason)
    assertFalse(processorCalled)
}
```

Add UI expectation: `当前相机分析分辨率不足，请靠近答题卡。`

- [ ] **Step 2: Verify RED on Fedora**

Expected: enum/type references do not compile.

- [ ] **Step 3: Implement minimum-resolution policy**

```kotlin
data class AnalysisResolution(val width: Int, val height: Int) {
    fun accepts(actualWidth: Int, actualHeight: Int): Boolean {
        val actualLong = maxOf(actualWidth, actualHeight)
        val actualShort = minOf(actualWidth, actualHeight)
        return actualLong >= maxOf(width, height) && actualShort >= minOf(width, height)
    }
}
```

After frame conversion and before frame-quality evaluation, return `RETAKE_LOW_RESOLUTION` when the actual crop fails this policy. Include actual/requested values in debug info.

- [ ] **Step 4: Request 1920×1440 and pass 1280×960 minimum**

```kotlin
val RequestedResolution = Size(1920, 1440)
val MinimumResolution = AnalysisResolution(1280, 960)
```

- [ ] **Step 5: Verify GREEN and existing analyzer tests**

Expected: targeted analyzer/UI tests pass.

- [ ] **Step 6: Commit**

```sh
git add app omr-core
git commit -m "feat(camera): prefer high resolution analysis safely"
```

### Task 4: P1 Fixed 16×16 Projective Cell Sampling

**Files:**
- Create: `omr-core/src/main/java/com/answercard/grader/miniprogram/MiniProgramCellSampler.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/MiniProgramBubbleReader.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidOmrEngine.kt`
- Test: `omr-core/src/test/java/com/answercard/grader/miniprogram/MiniProgramCellSamplerTest.kt`
- Test: `app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrImageScanTest.kt`

**Interfaces:**
- Produces `MiniProgramCellSourceMetrics(width, height, area, insideFrame)`.
- Produces `MiniProgramCellSampler.sample(frame, cell): MiniProgramCellSample` with 16 rows and 16 columns.

- [ ] **Step 1: Write failing sampler tests**

Cover 10×8 accepted, 9×8 rejected, area below 80 rejected, a point inside the 1px interpolation margin accepted, a boundary point rejected, and bilinear sampling of a 2D gradient into a 16×16 grid.

```kotlin
@Test
fun acceptsMinimumTenByEightSourceCell() {
    val frame = gradientFrame(40, 30)
    val sample = MiniProgramCellSampler.sample(frame, rectangularCell(5.0, 5.0, 10.0, 8.0))
    assertNull(sample.failureReason)
    assertEquals(16, sample.rows)
    assertEquals(16, sample.columns)
    assertEquals(256, sample.grayValues.size)
}

private fun rectangularCell(left: Double, top: Double, width: Double, height: Double) =
    MiniProgramCell(
        row = 0,
        column = 0,
        leftTop = MiniProgramGridPoint(top, left),
        rightTop = MiniProgramGridPoint(top, left + width),
        leftBottom = MiniProgramGridPoint(top + height, left),
        rightBottom = MiniProgramGridPoint(top + height, left + width),
    )
```

- [ ] **Step 2: Verify RED on Fedora**

Expected: sampler type is missing.

- [ ] **Step 3: Implement metrics, validation, interpolation, and bilinear luma**

```kotlin
object MiniProgramCellSampler {
    const val TARGET_ROWS = 16
    const val TARGET_COLUMNS = 16
    const val MIN_SOURCE_WIDTH = 10
    const val MIN_SOURCE_HEIGHT = 8
    const val MIN_SOURCE_AREA = 80.0

    fun sample(frame: MiniProgramFrame, cell: MiniProgramCell): MiniProgramCellSample {
        val metrics = sourceMetrics(frame, cell)
        metrics.failureReason?.let { return MiniProgramCellSample.failure(metrics, it) }
        val values = IntArray(TARGET_ROWS * TARGET_COLUMNS)
        // Map each output center through MiniProgramGridBuilder.interpolate and bilinearly sample four luma pixels.
        return MiniProgramCellSample(TARGET_ROWS, TARGET_COLUMNS, values, metrics, null)
    }
}
```

- [ ] **Step 4: Route BubbleReader through the sampler**

Replace `sampleRows(cell)`, `sampleColumns(cell)`, floor-only reads, and `MIN_SAMPLE_SIZE` validation with sampler output. Keep all downstream mark logic on the returned 16×16 arrays.

- [ ] **Step 5: Replace projected-cell 14px validation**

`ProjectedCellSizeValidation.from(frame, projectedCells)` must aggregate sampler source metrics. Reject clipped cells separately from small cells; Task 6 adds the final clipped enum behavior.

- [ ] **Step 6: Add a failing-then-green scale regression**

Add `formalScanReadsProductionCardAtScalePointEightFive()` using the existing filled coded template helper at `scale = 0.85f`. Before production changes it must fail at cell-size validation; after changes it must return the correct `1234`, answers, and score.

- [ ] **Step 7: Verify all OMR core and image scan tests**

Expected: new sampler tests, scale 0.85 regression, existing scale 0.7 rejection, and all prior true-image regressions pass.

- [ ] **Step 8: Commit**

```sh
git add omr-core app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrImageScanTest.kt
git commit -m "feat(omr): normalize low resolution cell sampling"
```

### Task 5: P1 Whole-Frame Blur Advisory and Marker Diagnostics

**Files:**
- Modify: `app/src/main/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzer.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/CodedCornerMarkerDetector.kt`
- Test: `app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzerTest.kt`
- Test: `omr-core/src/test/java/com/answercard/grader/miniprogram/CodedCornerMarkerDetectorTest.kt`

**Interfaces:**
- Whole-frame `RETAKE_BLUR` becomes diagnostic and continues to `AndroidOmrEngine`; whole-frame exposure rejection remains hard.
- `CodedCornerMarkerDiagnostics` adds component/candidate counts and selected edge lengths.

- [ ] **Step 1: Write failing analyzer policy tests**

```kotlin
@Test
fun preOmrBlurIsAdvisoryAndStillRunsProcessor() {
    // Inject a quality evaluator returning RETAKE_BLUR and a processor returning a known result.
    assertTrue(processorCalled)
    assertTrue(received!!.debugInfo.contains("frameBlurAdvisory=true"))
}

@Test
fun preOmrExposureFailureStillSkipsProcessor() {
    assertFalse(processorCalled)
    assertEquals(ScanRejectionReason.RETAKE_EXPOSURE, received?.rejectionReason)
}
```

- [ ] **Step 2: Verify RED on Fedora**

Expected: blur test shows processor was skipped.

- [ ] **Step 3: Implement advisory split**

```kotlin
val hardFrameQualityFailure = !quality.accepted &&
    quality.rejectionReason == ScanRejectionReason.RETAKE_EXPOSURE
if (options.enableFrameQualityGate && hardFrameQualityFailure) { /* existing rejection */ }
val qualityDebugInfo = quality.debugInfo("frame") +
    "frameBlurAdvisory=${quality.rejectionReason == ScanRejectionReason.RETAKE_BLUR}"
```

- [ ] **Step 4: Add diagnostics scan result**

Refactor coded candidate discovery to return raw component count, size/aspect candidate count, decoded count, and selected edge lengths without changing acceptance thresholds.

- [ ] **Step 5: Verify GREEN plus card ROI blur regressions**

Expected: analyzer policy tests pass and existing engine `RETAKE_BLUR` tests still prove no blurred card score is exposed.

- [ ] **Step 6: Commit**

```sh
git add app/src/main/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzer.kt app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzerTest.kt omr-core/src/main/java/com/answercard/grader/miniprogram/CodedCornerMarkerDetector.kt omr-core/src/test/java/com/answercard/grader/miniprogram/CodedCornerMarkerDetectorTest.kt
git commit -m "fix(omr): defer blur rejection to card region"
```

### Task 6: P2 Required-Cell Clipping Policy

**Files:**
- Create: `omr-core/src/main/java/com/answercard/grader/miniprogram/CodedCardFramePolicy.kt`
- Create: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidRequiredCellValidator.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/ScanSafety.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidOmrEngine.kt`
- Modify: `app/src/main/java/com/answercard/grader/ui/ScanDisplayResult.kt`
- Test: `omr-core/src/test/java/com/answercard/grader/miniprogram/CodedCardFramePolicyTest.kt`
- Test: `omr-core/src/test/java/com/answercard/grader/miniprogram/AndroidRequiredCellValidatorTest.kt`
- Test: `app/src/test/java/com/answercard/grader/ui/ScanDisplayResultTest.kt`

**Interfaces:**
- Adds `ScanRejectionReason.RETAKE_CARD_CLIPPED`.
- `AnchorLocationDecision` carries `inferredCodedMarkerCount`.
- Coded scans with exactly one inferred marker may skip anchor-center inset rejection, but never source-cell inside-frame validation.

- [ ] **Step 1: Write failing policy and UI tests**

```kotlin
@Test
fun codedPathWithExactlyOneInferredMarkerMaySkipAnchorInset() {
    assertFalse(CodedCardFramePolicy.requiresAnchorBorderInset(AnchorMode.CODED_ONLY, 1))
    assertTrue(CodedCardFramePolicy.requiresAnchorBorderInset(AnchorMode.CODED_ONLY, 0))
    assertTrue(CodedCardFramePolicy.requiresAnchorBorderInset(AnchorMode.LEGACY, 1))
}

@Test
fun requiredCellOutsideInterpolationMarginIsClipped() {
    val frame = MiniProgramFrame(100, 100, IntArray(10_000) { 255 })
    val cells = projectedCellsWithSingleQuestion(
        rectangularCell(left = 0.5, top = 10.0, width = 12.0, height = 10.0),
    )
    val validation = AndroidRequiredCellValidator.validate(frame, cells)
    assertEquals(RequiredCellFailure.CLIPPED, validation.failure)
}

@Test
fun requiredCellInsideFrameButBelowInformationFloorIsSmall() {
    val frame = MiniProgramFrame(100, 100, IntArray(10_000) { 255 })
    val cells = projectedCellsWithSingleQuestion(
        rectangularCell(left = 10.0, top = 10.0, width = 9.0, height = 8.0),
    )
    val validation = AndroidRequiredCellValidator.validate(frame, cells)
    assertEquals(RequiredCellFailure.TOO_SMALL, validation.failure)
}

private fun projectedCellsWithSingleQuestion(cell: MiniProgramCell) =
    AndroidPaperProjectedCells(
        questionCells = mapOf(AndroidPaperQuestionCellKey(0, 0) to cell),
        admissionNumberCells = emptyMap(),
        debugInfo = emptyList(),
    )

private fun rectangularCell(left: Double, top: Double, width: Double, height: Double) =
    MiniProgramCell(
        row = 0,
        column = 0,
        leftTop = MiniProgramGridPoint(top, left),
        rightTop = MiniProgramGridPoint(top, left + width),
        leftBottom = MiniProgramGridPoint(top + height, left),
        rightBottom = MiniProgramGridPoint(top + height, left + width),
    )
```

Add UI expectation: `请稍微远离，确保答题区域完整。`

- [ ] **Step 2: Verify RED on Fedora**

Expected: first fixture fails `anchors touch frame border`; clipped enum does not exist.

- [ ] **Step 3: Carry coded inference evidence**

```kotlin
AnchorLocationDecision(
    anchors = match.anchors,
    inferredCodedMarkerCount = match.diagnostics.inferredIds.size,
    // existing fields
)
```

Implement the policy and validator as pure JVM objects:

```kotlin
object CodedCardFramePolicy {
    fun requiresAnchorBorderInset(anchorMode: AnchorMode, inferredCodedMarkerCount: Int): Boolean =
        anchorMode != AnchorMode.CODED_ONLY || inferredCodedMarkerCount != 1
}

enum class RequiredCellFailure { CLIPPED, TOO_SMALL }

object AndroidRequiredCellValidator {
    fun validate(frame: MiniProgramFrame, cells: AndroidPaperProjectedCells): RequiredCellValidation {
        val metrics = (cells.questionCells.values + cells.admissionNumberCells.values)
            .map { MiniProgramCellSampler.sourceMetrics(frame, it) }
        val failure = when {
            metrics.any { !it.insideFrame } -> RequiredCellFailure.CLIPPED
            metrics.any { !it.hasEnoughSourceInformation } -> RequiredCellFailure.TOO_SMALL
            else -> null
        }
        return RequiredCellValidation(failure, metrics)
    }
}
```

- [ ] **Step 4: Apply mode-specific border and universal cell policy**

```kotlin
val mayInferOutsideFrame = anchorMode == AnchorMode.CODED_ONLY &&
    anchorDecision.inferredCodedMarkerCount == 1
val geometryFailure = cardGeometry.failureReason(
    requireAnchorBorderInset = !mayInferOutsideFrame,
)
```

If projected-cell validation reports any cell outside the 1px interpolation margin, return `RETAKE_CARD_CLIPPED`. Small-but-inside cells continue returning `RETAKE_CELL_SIZE`. Legacy always requires anchor inset.

- [ ] **Step 5: Verify GREEN and all coded/legacy safety tests**

Expected: both new fixtures pass their assertions; legacy ambiguity, border, overexposure, and blur tests remain fail-closed.

- [ ] **Step 6: Commit**

```sh
git add omr-core app/src/main/java/com/answercard/grader/ui/ScanDisplayResult.kt app/src/test/java/com/answercard/grader/ui/ScanDisplayResultTest.kt
git commit -m "feat(omr): allow safe coded card edge crops"
```

### Task 7: P2 Tap Focus and Intuitive Prompts

**Files:**
- Create: `app/src/main/java/com/answercard/grader/camera/CameraFocusActions.kt`
- Modify: `app/src/main/java/com/answercard/grader/camera/CameraPreview.kt`
- Modify: `app/src/main/java/com/answercard/grader/ui/ScanScreen.kt`
- Test: `app/src/test/java/com/answercard/grader/camera/CameraFocusActionsTest.kt`

**Interfaces:**
- `CameraFocusActions.DefaultSpec` exposes AF+AE flags and a 3-second duration.
- `CameraFocusActions.actionFor(point: MeteringPoint, spec: CameraFocusActionSpec = DefaultSpec): FocusMeteringAction` builds the CameraX request.
- `CameraPreview` triggers center focus after binding and touch focus on `ACTION_UP`.

- [ ] **Step 1: Write the failing focus-action test**

```kotlin
@Test
fun defaultFocusSpecUsesAfAeAndThreeSecondAutoCancel() {
    assertEquals(
        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        CameraFocusActions.DefaultSpec.flags,
    )
    assertEquals(3L, CameraFocusActions.DefaultSpec.autoCancelSeconds)
}
```

- [ ] **Step 2: Verify RED on Fedora**

Expected: `CameraFocusActions` is missing.

- [ ] **Step 3: Implement focus action**

```kotlin
data class CameraFocusActionSpec(val flags: Int, val autoCancelSeconds: Long)

val DefaultSpec = CameraFocusActionSpec(
    flags = FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
    autoCancelSeconds = 3L,
)

fun actionFor(
    point: MeteringPoint,
    spec: CameraFocusActionSpec = DefaultSpec,
): FocusMeteringAction =
    FocusMeteringAction.Builder(
        point,
        spec.flags,
    )
        .setAutoCancelDuration(spec.autoCancelSeconds, TimeUnit.SECONDS)
        .build()
```

- [ ] **Step 4: Wire center and tap focus**

Keep the bound `Camera` reference inside `DisposableEffect`. After binding, create a center point with `previewView.meteringPointFactory`; on `MotionEvent.ACTION_UP`, create a point at event x/y and call `camera.cameraControl.startFocusAndMetering(action)`. Clear the touch listener on dispose.

Display `轻触卡片可对焦` below the live status and keep `画面模糊，请持稳或轻触卡片对焦。` for blur.

- [ ] **Step 5: Verify GREEN and app compile**

Expected: focus test passes and `:app:compileDebugKotlin` succeeds.

- [ ] **Step 6: Commit**

```sh
git add app/src/main/java/com/answercard/grader/camera app/src/main/java/com/answercard/grader/ui/ScanScreen.kt app/src/test/java/com/answercard/grader/camera/CameraFocusActionsTest.kt
git commit -m "feat(camera): focus and meter on answer card"
```

### Task 8: Full Verification and Documentation

**Files:**
- Modify: `docs/camera-scan-followups.md`

**Interfaces:**
- Documents actual resolution policy, shared ViewPort, normalized cell limits, safe close-crop rule, and remaining two-device visual verification.

- [ ] **Step 1: Update follow-up status without claiming unrun device tests**

Record automated Fedora results separately from pending true-device WYSIWYG overlay and thermal checks.

- [ ] **Step 2: Copy the final changed tree files to Fedora**

Use `scp -P 22` for every tracked file changed since `39d12c3`; verify remote `git diff --name-only` matches local `git diff 39d12c3 --name-only` except the untracked `images/` regression fixtures.

- [ ] **Step 3: Run focused verification**

```sh
sh gradlew :omr-core:test
sh gradlew :app:testDebugUnitTest
```

Expected: both successful, zero failed tests.

- [ ] **Step 4: Run repository verification and APK build**

```sh
sh gradlew test
sh gradlew assembleDebug
```

Expected: both successful. Native symbol-strip notices may remain informational.

- [ ] **Step 5: Inspect worktree and diff**

```sh
git status --short
git diff --check
git log --oneline 39d12c3..HEAD
```

Expected: only the documentation update is uncommitted before the final commit; no whitespace errors.

- [ ] **Step 6: Commit documentation**

```sh
git add docs/camera-scan-followups.md
git commit -m "docs(camera): record wider scan range implementation"
```

- [ ] **Step 7: Re-run final status and report pending device-only checks**

Expected: clean worktree. Do not claim the 3dp preview-overlay, two-device, ten-minute thermal, or P95≤1.5s acceptance items until actual devices are run.
