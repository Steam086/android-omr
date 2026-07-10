# Low-Latency Camera Recognition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make handheld answer-card recognition lock within a 1.5-second P95 target without weakening pixel-quality, geometry, or full-answer consensus safety.

**Architecture:** Keep motion and Camera2 AF/AE as observable advisory signals, process only the newest frame every 250ms, and confirm three matching full signatures inside a four-result/three-second window. Convert the CameraX Y plane into the final rotated grayscale array in one pass to reduce allocation pressure.

**Tech Stack:** Kotlin, CameraX/Camera2 interop, Jetpack Compose, JUnit 4, Gradle, pure JVM `omr-core`.

## Global Constraints

- Keep `omr-core` free of Android, AndroidX, Compose, CameraX, and `android.graphics` imports.
- Keep the legacy `vision` path intact.
- Keep analysis resolution at 1280x960 and `STRATEGY_KEEP_ONLY_LATEST`.
- Add no third-party dependency.
- Use 4-space Kotlin indentation and Conventional Commit-style messages.
- Verify on the Fedora JDK 21 environment described by `LOCAL_DEVELOPMENT.md` because this Mac has no Java runtime.

---

### Task 1: Record advisory state and analyzer stage timing

**Files:**
- Modify: `app/src/main/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzer.kt`
- Test: `app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzerTest.kt`

**Interfaces:**
- Consumes: existing `FrameCaptureMetadata`, `CaptureStateEvaluator`, `isDeviceStableProvider`, and `AndroidOmrFrameProcessor`.
- Produces: debug keys `deviceStable`, `captureGateAccepted`, `captureGateRejection`, `frameAdapterElapsedMs`, `frameQualityElapsedMs`, `omrElapsedMs`, and `analyzerElapsedMs`; constructor parameter `nanoTimeProvider: () -> Long` for deterministic tests.

- [ ] **Step 1: Write the failing diagnostics test**

Add a test that supplies stable capture metadata, a crisp frame, and a monotonically increasing fake nanosecond clock:

```kotlin
@Test
fun analyzeReportsAdvisoryStateAndStageTimings() {
    var tick = 0L
    var received: AndroidOmrResult? = null
    val analyzer = AndroidOmrImageAnalyzer(
        templateProvider = { TemplateState.default() },
        onResult = { received = it },
        frameAdapter = { crispFrame(marker = 40) },
        processor = AndroidOmrFrameProcessor { _, _ -> result(success = true) },
        options = AndroidOmrAnalyzerOptions(minAnalyzeIntervalMs = 0L),
        isDeviceStableProvider = { true },
        captureMetadataProvider = { focusedMetadata(it) },
        nanoTimeProvider = { tick++.times(1_000_000L) },
    )

    analyzer.analyze(FakeImageProxy(timestamp = 42L))

    val debug = received?.debugInfo.orEmpty()
    assertTrue(debug.contains("deviceStable=true"))
    assertTrue(debug.contains("captureGateAccepted=true"))
    assertTrue(debug.contains("captureGateRejection=none"))
    assertTrue(debug.any { it.startsWith("frameAdapterElapsedMs=") })
    assertTrue(debug.any { it.startsWith("frameQualityElapsedMs=") })
    assertTrue(debug.any { it.startsWith("omrElapsedMs=") })
    assertTrue(debug.any { it.startsWith("analyzerElapsedMs=") })
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.miniprogram.AndroidOmrImageAnalyzerTest
```

Expected: compilation fails because `nanoTimeProvider` and the timing debug keys do not exist.

- [ ] **Step 3: Implement advisory and timing diagnostics without changing gates**

Add the monotonic provider:

```kotlin
private val nanoTimeProvider: () -> Long = System::nanoTime,
```

Evaluate and append advisory state before current early returns:

```kotlin
val deviceStable = isDeviceStableProvider()
val captureGate = CaptureStateEvaluator.evaluate(metadata)
val captureDebugInfo = (metadata?.debugInfo() ?: listOf("captureMetadata=unavailable")) + listOf(
    "deviceStable=$deviceStable",
    "captureGateAccepted=${captureGate.accepted}",
    "captureGateRejection=${captureGate.rejectionReason?.name ?: "none"}",
)
```

Measure synchronous stages with `nanoTimeProvider()` and append millisecond fields to rejection and successful result debug lists. Keep the current stability and capture early returns in this task so the commit is diagnostics-only.

- [ ] **Step 4: Run the focused tests**

Run the Task 1 command again. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```sh
git add app/src/main/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzer.kt app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzerTest.kt
git commit -m "feat(camera): report analyzer stage timings"
```

### Task 2: Keep handheld analysis live and limit processing cadence

**Files:**
- Modify: `app/src/main/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzer.kt`
- Modify: `app/src/main/java/com/answercard/grader/ui/ScanScreen.kt`
- Test: `app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzerTest.kt`

**Interfaces:**
- Consumes: Task 1 advisory debug keys and existing `FrameQualityEvaluator` hard gate.
- Produces: motion and AF/AE advisory-only behavior; app configuration `minAnalyzeIntervalMs = 250L`, `candidateWindowMs = 0L`.

- [ ] **Step 1: Replace hard-gate tests with failing soft-signal tests**

Replace `analyzeWaitsForAutofocusBeforeAdaptingFrame` and add an unstable-device case:

```kotlin
@Test
fun analyzeContinuesWhileAutofocusIsScanning() {
    var processCount = 0
    var received: AndroidOmrResult? = null
    val analyzer = AndroidOmrImageAnalyzer(
        templateProvider = { TemplateState.default() },
        onResult = { received = it },
        frameAdapter = { crispFrame(marker = 40) },
        processor = AndroidOmrFrameProcessor { _, _ ->
            processCount++
            result(success = true)
        },
        captureMetadataProvider = { scanningMetadata(it) },
    )

    analyzer.analyze(FakeImageProxy(timestamp = 42L))

    assertEquals(1, processCount)
    assertTrue(received?.debugInfo.orEmpty().contains("captureGateAccepted=false"))
    assertTrue(received?.debugInfo.orEmpty().contains("captureGateRejection=WAIT_FOCUS"))
}

@Test
fun analyzeContinuesWhileDeviceIsMoving() {
    var processCount = 0
    var received: AndroidOmrResult? = null
    val analyzer = AndroidOmrImageAnalyzer(
        templateProvider = { TemplateState.default() },
        onResult = { received = it },
        frameAdapter = { crispFrame(marker = 40) },
        processor = AndroidOmrFrameProcessor { _, _ ->
            processCount++
            result(success = true)
        },
        isDeviceStableProvider = { false },
    )

    analyzer.analyze(FakeImageProxy())

    assertEquals(1, processCount)
    assertTrue(received?.debugInfo.orEmpty().contains("deviceStable=false"))
}
```

- [ ] **Step 2: Run tests and verify the old hard gates fail them**

Run the Task 1 app test command. Expected: both tests fail because processor count remains zero.

- [ ] **Step 3: Remove only the motion and capture early returns**

Delete the `WAIT_STABILITY`, `WAIT_FOCUS`, and `WAIT_EXPOSURE` early-return blocks from `analyze()`. Preserve the advisory debug list from Task 1 and the existing pixel-quality rejection block.

Configure the live screen:

```kotlin
options = AndroidOmrAnalyzerOptions(
    minAnalyzeIntervalMs = 250L,
    candidateWindowMs = 0L,
    analysisOrientationMode = analysisOrientationMode,
    requestedAnalysisResolutionLabel = CameraAnalysisConfig.RequestedResolutionLabel,
)
```

- [ ] **Step 4: Run analyzer, stability, quality, and capture-state tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.miniprogram.AndroidOmrImageAnalyzerTest --tests com.answercard.grader.camera.StabilityEvaluatorTest :omr-core:test --tests com.answercard.grader.miniprogram.CaptureStateEvaluatorTest --tests com.answercard.grader.miniprogram.FrameQualityEvaluatorTest
```

Expected: `BUILD SUCCESSFUL`; blur/exposure tests still reject before OMR.

- [ ] **Step 5: Commit**

```sh
git add app/src/main/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzer.kt app/src/main/java/com/answercard/grader/ui/ScanScreen.kt app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzerTest.kt
git commit -m "fix(camera): keep handheld analysis live"
```

### Task 3: Align consensus with the new cadence

**Files:**
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/ScanConsensusTracker.kt`
- Test: `omr-core/src/test/java/com/answercard/grader/miniprogram/ScanConsensusTrackerTest.kt`

**Interfaces:**
- Consumes: full result signature from `ScanConsensusTracker.signatureOf`.
- Produces: defaults `requiredFrames=3`, `windowSize=4`, `maxWindowDurationMs=3_000L`; unchanged 900ms card-absence reset.

- [ ] **Step 1: Change tests to specify the desired 3/4 behavior**

```kotlin
@Test
fun locksAfterThreeConsistentFrames() {
    val tracker = ScanConsensusTracker()
    repeat(2) { assertTrue(tracker.offer(result()) is ScanConsensusDecision.Pending) }
    assertTrue(tracker.offer(result()) is ScanConsensusDecision.Locked)
}

@Test
fun locksThreeMatchingFramesInsideFourFrameWindow() {
    val tracker = ScanConsensusTracker()
    listOf(4.0, 6.0, 4.0).forEach { score ->
        assertTrue(tracker.offer(result(totalScore = score)) is ScanConsensusDecision.Pending)
    }
    assertTrue(tracker.offer(result(totalScore = 4.0)) is ScanConsensusDecision.Locked)
}

@Test
fun slowValidResultsFitInsideThreeSecondWindow() {
    var nowMs = 0L
    val tracker = ScanConsensusTracker(nowMsProvider = { nowMs })
    assertTrue(tracker.offer(result()) is ScanConsensusDecision.Pending)
    nowMs = 1_100L
    assertTrue(tracker.offer(result()) is ScanConsensusDecision.Pending)
    nowMs = 2_200L
    assertTrue(tracker.offer(result()) is ScanConsensusDecision.Locked)
}
```

Update other default-count expectations from four to three, while retaining answer-jitter, locking, and card-removal assertions.

- [ ] **Step 2: Run the consensus test and verify failure**

Run:

```sh
sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.ScanConsensusTrackerTest
```

Expected: tests fail because defaults remain 4/5/2000ms.

- [ ] **Step 3: Update only the default constructor parameters**

```kotlin
class ScanConsensusTracker(
    private val requiredFrames: Int = 3,
    private val windowSize: Int = 4,
    private val maxWindowDurationMs: Long = 3_000L,
    private val cardAbsentResetMs: Long = 900L,
    // unchanged providers
)
```

- [ ] **Step 4: Run the entire omr-core test suite**

Run: `sh gradlew :omr-core:test`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```sh
git add omr-core/src/main/java/com/answercard/grader/miniprogram/ScanConsensusTracker.kt omr-core/src/test/java/com/answercard/grader/miniprogram/ScanConsensusTrackerTest.kt
git commit -m "fix(scan): align consensus with camera cadence"
```

### Task 4: Convert CameraX Y planes in one pass

**Files:**
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/CameraYPlaneFrameAdapter.kt`
- Modify: `app/src/main/java/com/answercard/grader/miniprogram/CameraImageProxyFrameAdapter.kt`
- Test: `app/src/test/java/com/answercard/grader/miniprogram/CameraYPlaneFrameAdapterTest.kt`
- Test: `app/src/test/java/com/answercard/grader/miniprogram/CameraImageProxyFrameAdapterTest.kt`

**Interfaces:**
- Consumes: synchronous `ImageProxy.PlaneProxy.buffer` access before the analyzer closes the image.
- Produces: `YPlaneFrameInput.yData: ByteBuffer`; one final `IntArray` allocation for crop plus rotation.

- [ ] **Step 1: Update tests for ByteBuffer and preserved source position**

Change the helper to:

```kotlin
private fun bytes(vararg values: Int): ByteBuffer =
    ByteBuffer.wrap(ByteArray(values.size) { index -> values[index].toByte() })
```

Add:

```kotlin
@Test
fun conversionDoesNotChangeInputBufferPosition() {
    val input = inputForRotation(rotationDegrees = 90)
    input.yData.position(2)

    CameraYPlaneFrameAdapter.toMiniProgramFrame(input)

    assertEquals(2, input.yData.position())
}
```

Wrap the signed-byte test input with `ByteBuffer.wrap(...)`.

- [ ] **Step 2: Run adapter tests and verify compilation failure**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.miniprogram.CameraYPlaneFrameAdapterTest --tests com.answercard.grader.miniprogram.CameraImageProxyFrameAdapterTest
```

Expected: compilation fails because `YPlaneFrameInput.yData` still requires `ByteArray`.

- [ ] **Step 3: Implement direct conversion**

Change the input type:

```kotlin
val yData: ByteBuffer,
```

In `toMiniProgramFrame`, validate once, duplicate and rewind the buffer, allocate final output dimensions, and map each cropped source coordinate directly:

```kotlin
val outputWidth = if (input.rotationDegrees == 90 || input.rotationDegrees == 270) {
    input.cropHeight
} else {
    input.cropWidth
}
val outputHeight = if (input.rotationDegrees == 90 || input.rotationDegrees == 270) {
    input.cropWidth
} else {
    input.cropHeight
}
val output = IntArray(outputWidth * outputHeight)
val source = input.yData.duplicate().also(ByteBuffer::rewind)
for (row in 0 until input.cropHeight) {
    for (column in 0 until input.cropWidth) {
        val sourceIndex = (input.cropTop + row) * input.rowStride +
            (input.cropLeft + column) * input.pixelStride
        val targetIndex = when (input.rotationDegrees) {
            0 -> row * outputWidth + column
            90 -> column * outputWidth + (input.cropHeight - 1 - row)
            180 -> (input.cropHeight - 1 - row) * outputWidth +
                (input.cropWidth - 1 - column)
            270 -> (input.cropWidth - 1 - column) * outputWidth + row
            else -> error("validated rotation")
        }
        output[targetIndex] = source.get(sourceIndex).toInt() and 0xff
    }
}
```

Pass `yPlane.buffer.duplicate()` directly from `CameraImageProxyFrameAdapter` and remove `toByteArrayFromStart()`.

- [ ] **Step 4: Run adapter and analyzer tests**

Run:

```sh
sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.miniprogram.CameraYPlaneFrameAdapterTest --tests com.answercard.grader.miniprogram.CameraImageProxyFrameAdapterTest --tests com.answercard.grader.miniprogram.AndroidOmrImageAnalyzerTest
```

Expected: `BUILD SUCCESSFUL` with all rotation and crop expectations unchanged.

- [ ] **Step 5: Commit**

```sh
git add omr-core/src/main/java/com/answercard/grader/miniprogram/CameraYPlaneFrameAdapter.kt app/src/main/java/com/answercard/grader/miniprogram/CameraImageProxyFrameAdapter.kt app/src/test/java/com/answercard/grader/miniprogram/CameraYPlaneFrameAdapterTest.kt app/src/test/java/com/answercard/grader/miniprogram/CameraImageProxyFrameAdapterTest.kt
git commit -m "perf(camera): convert y plane without intermediate copies"
```

### Task 5: Final verification and operational documentation

**Files:**
- Modify: `docs/camera-scan-followups.md`

**Interfaces:**
- Consumes: final configuration, debug keys, and verification output from Tasks 1-4.
- Produces: an operator-facing description of soft advisory signals, 250ms cadence, 3/4 consensus, and true remaining device measurements.

- [ ] **Step 1: Update the camera follow-up document**

Document the final pipeline in this order:

```text
CameraX latest frame → 250ms throttle → advisory motion/AF/AE → frame quality → OMR/card ROI quality → 3-of-4 consensus.
```

Record the seven debug fields from Task 1 and state that P50/P95 must be captured on physical devices; do not claim the 1.5-second target from JVM tests alone.

- [ ] **Step 2: Run repository verification**

Run:

```sh
sh gradlew :omr-core:test
sh gradlew test
sh gradlew assembleDebug
```

Expected: all three commands finish with `BUILD SUCCESSFUL`.

- [ ] **Step 3: Inspect the staged diff and commit documentation**

```sh
git diff --check
git status --short
git add docs/camera-scan-followups.md
git commit -m "docs(camera): document low-latency scan pipeline"
```

Expected: only the intended documentation remains before the final commit; afterward the worktree is clean.

