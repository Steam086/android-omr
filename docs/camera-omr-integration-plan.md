# Camera OMR Integration Plan

## Current State

Baseline: `7c155bd feat(camera): add analyzer frame gate`.

The new OMR path is ready up to a CameraX analyzer boundary, but it is not wired
into the UI yet:

```text
ImageProxy
 -> CameraImageProxyFrameAdapter.fromImageProxy(...)
 -> MiniProgramFrame
 -> AndroidOmrImageAnalyzer
 -> AndroidOmrFrameProcessor
 -> AndroidOmrEngine.scan(frame, template)
 -> AndroidOmrResult
```

The locked recognition route remains:

- Android paper content layout uses the existing 5x3 production template.
- Production template reads use `TemplateGeometry` real rectangles projected
  through the detected four independent L anchors.
- Formal OMR scan still uses `CornerAnchorMatcher`.
- No full-image `PerspectiveWarp` in the new engine.
- No OpenCV in the new engine path.
- No OMR threshold, BFS, or template coordinate changes are needed for UI
  integration.

## Existing UI And CameraX Structure

### UI Entry

The current scan UI entry is:

```text
AnswerCardApp
 -> HomeScreen(onOpenScan)
 -> Screen.Scan
 -> ScanScreen(templateId, template, ...)
```

`AnswerCardApp` owns the selected `StoredTemplate` from `TemplateCollection` and
passes both `selected.id` and `selected.template` into `ScanScreen`.

### TemplateState Source

`TemplateState` is loaded by `TemplateStore.loadCollection()` in
`AnswerCardApp`. The selected template is:

```kotlin
val selected = collection.selectedTemplate
val template = selected.template
```

`ScanScreen` receives the selected `TemplateState` directly as a parameter. It
also builds the legacy physical layout with:

```kotlin
val layout = remember(template) { TemplateGeometry.buildLayout(template) }
```

That layout is only required by the legacy `OmrScanner` path.

### Existing CameraX Preview And Analysis

`CameraPreview` already creates real CameraX use cases:

- `Preview.Builder().build()`
- `ImageAnalysis.Builder().setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)`
- `ProcessCameraProvider.getInstance(context)`
- `bindToLifecycle(context.requireLifecycleOwner(), DEFAULT_BACK_CAMERA, preview, analysis)`

`CameraPreview` currently exposes a high-level callback:

```kotlin
onFrame: (Bitmap) -> Unit
```

Inside its analyzer it converts the `ImageProxy` to a grayscale bitmap:

```text
ImageProxyBitmap.grayscaleBitmap(image)
```

and closes the `ImageProxy` in `finally`.

### Existing Permission Handling

`ScanScreen` handles camera permission with:

- `ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)`
- `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`
- `LaunchedEffect(Unit)` to request permission when missing
- `PermissionPrompt` to retry permission request

### Existing Scan Result Flow

The current UI scan path is:

```text
ScanScreen
 -> CameraPreview(onFrame)
 -> ImageProxyBitmap.grayscaleBitmap(image)
 -> OmrScanner.scan(bitmap, template, layout, scale = 3f)
 -> mainHandler.post { update Compose state, save record, speak score }
```

`ScanScreen` keeps:

- `lastScanAt` for UI-side 700ms throttling.
- `lastHandledKey` for duplicate save/speech suppression.
- `lastResult: OmrScanResult?`.
- `status: String`.

When a legacy result is not null, `ScanScreen` saves a `ScanRecord` using
`ScanRecordStore` and speaks the score through `ScoreSpeaker`.

### Legacy Recognition Path

The old scanner is still active in UI:

```text
OmrScanner.scan(bitmap, template, layout, scale = 3f)
 -> rotate through 0/90/180/270
 -> CornerDetector.detect(oriented, layout)
 -> PerspectiveWarp.normalize(oriented, corners, layout, scale)
 -> AnswerReader.readAnswers(normalized, layout, scale)
 -> ExamIdReader.readExamId(normalized, layout, scale)
 -> Grader.grade(template, recognizedAnswers)
```

So yes, the current UI path uses legacy `OmrScanner`, `CornerDetector`, and
`PerspectiveWarp`.

## New Analyzer Integration Point

The lowest-risk seam is `CameraPreview`, not `ScanScreen`.

Today `CameraPreview` hides CameraX lifecycle binding and exposes a bitmap frame
callback. For 10F, keep that lifecycle responsibility in `CameraPreview` and add
a second analyzer-oriented interface, for example:

```kotlin
fun CameraPreview(
    modifier: Modifier = Modifier,
    analyzer: ImageAnalysis.Analyzer,
)
```

or a nullable overload that keeps the old bitmap callback:

```kotlin
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFrame: ((Bitmap) -> Unit)? = null,
    analyzer: ImageAnalysis.Analyzer? = null,
)
```

The first option is cleaner, but the second option makes fallback easier without
duplicating CameraX setup. In either case, only `CameraPreview` should call
`setAnalyzer(...)`; `ScanScreen` should not create `ImageAnalysis`, bind
lifecycle, or close `ImageProxy` itself.

The 10F analyzer instance should be created in `ScanScreen` with:

```text
templateProvider = { template }
onResult = { result -> mainHandler.post { update UI state } }
onError = { error -> mainHandler.post { update failure state } }
```

This preserves the thread seam: analyzer work happens on the CameraX executor,
and Compose state updates happen on the main thread.

## Required 10F UI State Shape

Do not mirror all `AndroidOmrResult` data into UI state. Keep a small scan status
model in `ScanScreen`:

```text
recognitionState:
  - Idle / Scanning / Recognized / NotRecognized / Error
displayExamId: String?
displayScore: String?
displayFailureReason: String?
lastHandledKey: String?
```

Minimum display for 10F:

- whether recognition succeeded
- admission number
- score
- failure reason

Avoid showing raw debug info or overlay in 10F.

## Callback And ImageProxy Ownership

`AndroidOmrImageAnalyzer` owns `ImageProxy.close()`. UI code and `CameraPreview`
must not close the same image when using this analyzer path.

For 10F, `CameraPreview` should use one of two branches:

```text
legacy bitmap branch:
  setAnalyzer { image ->
      try { onFrame(ImageProxyBitmap.grayscaleBitmap(image)) }
      finally { image.close() }
  }

new analyzer branch:
  setAnalyzer(executor, androidOmrImageAnalyzer)
```

Do not wrap the new analyzer in another lambda that also closes the image.

## Avoiding UI Refresh Noise

The analyzer already has:

- `minAnalyzeIntervalMs = 300`
- busy drop while processing
- `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` remains in `CameraPreview`

`ScanScreen` should still keep result-level dedupe with `lastHandledKey`, because
analyzer throttling limits work but does not decide whether a repeated valid
result should save/speak again.

For 10F:

- update UI state only when the displayed key changes, or when moving between
  success/failure states.
- keep save/speech dedupe using a key such as
  `"$examId:$totalScore/$maxScore"`.
- do not add extra debounce, thread pools, result cache, or ROI reuse yet.

## Fallback Strategy

Keep the legacy scanner as fallback in 10F. The safest first UI integration is a
local feature flag in `ScanScreen`, for example:

```kotlin
private const val USE_ANDROID_OMR_ANALYZER = true
```

This is intentionally simple and local. It avoids introducing app-wide settings
or UI switches before the new path has phone-test evidence.

Fallback options:

- `true`: use `AndroidOmrImageAnalyzer` and display `AndroidOmrResult`.
- `false`: keep the current `CameraPreview(onFrame)` + `OmrScanner` path.

Do not delete `OmrScanner`, `ImageProxyBitmap`, `CornerDetector`, or
`PerspectiveWarp` in 10F.

## Risk Points

1. Threading: `AndroidOmrImageAnalyzer` callbacks run on the analysis executor.
   Compose state, record persistence, and speech calls should be dispatched to
   the main thread from `ScanScreen`.
2. Double close: when `CameraPreview` uses `AndroidOmrImageAnalyzer`, do not also
   close `ImageProxy` in a wrapper lambda.
3. Duplicate records/speech: the new analyzer may return repeated successes.
   Keep `lastHandledKey`.
4. Data model mismatch: legacy `OmrScanResult.grade.totalScore` is `Int`; new
   `AndroidOmrResult.score.totalScore` is `Double`. 10F should format display
   carefully and only save records if the current `ScanRecord` model can safely
   represent the score.
5. Failure semantics: new engine returns explicit `failureReason`; UI should
   show it briefly but not treat every failure frame as a record update.
6. Template freshness: analyzer uses `templateProvider`. The provider should
   capture the current `template`, so reopening scan after editing uses the
   selected template.
7. Existing UI text is currently mojibake in source. 10F should avoid broad copy
   edits unless the task explicitly includes text cleanup.

## Stage 10F Minimal Implementation Plan

### Files To Modify

1. `app/src/main/java/com/answercard/grader/camera/CameraPreview.kt`
   - Add an analyzer-capable path or overload.
   - Keep lifecycle binding inside `CameraPreview`.
   - Keep `Preview` and `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`.
   - Ensure new analyzer path does not close `ImageProxy` outside the analyzer.

2. `app/src/main/java/com/answercard/grader/ui/ScanScreen.kt`
   - Add a local feature flag for new analyzer path.
   - Create `AndroidOmrImageAnalyzer` with `templateProvider`, `onResult`, and
     `onError`.
   - Convert `AndroidOmrResult` to minimal UI display state.
   - Keep legacy `OmrScanner` branch as fallback.
   - Keep existing permission flow.

3. Optional test files if practical:
   - A focused JVM test for result-to-display mapping if that mapping is
     extracted into a small function.
   - Do not write brittle Compose or CameraX lifecycle tests in 10F unless the
     project already has a stable pattern.

### New Analyzer Wiring

10F should wire:

```text
ScanScreen
 -> remember(template) { AndroidOmrImageAnalyzer(...) }
 -> CameraPreview(analyzer = analyzer)
 -> AndroidOmrResult callback
 -> mainHandler.post { update scan state, maybe save/speak }
```

`TemplateState` should be supplied by `templateProvider = { template }`.

`AndroidOmrResult` should map to UI as:

- `success == true`
  - display status: recognized
  - exam id: `result.admissionNumber?.digits`
  - score: `result.score?.totalScore / result.score?.maxScore`
- `success == false`
  - display status: not recognized
  - failure: `result.failureReason`
  - keep last successful result visible only if that is already the existing UX
    preference; otherwise display the current failure reason.

### What 10F Should Not Do

- No debug overlay.
- No auto-submit beyond the existing local `ScanRecordStore.saveRecord` behavior.
- No complex animation.
- No performance optimization beyond the analyzer gate already built in 10D.
- No history redesign.
- No settings screen or user-visible feature switch.
- No OMR threshold changes.
- No template coordinate model changes.
- No deletion of legacy scanner code.

## Phone Test Checklist After 10F

10F will be the first UI-connected CameraX path and should be followed by phone
testing.

Minimum phone checks:

1. Permission request appears and allows preview to start.
2. Preview remains visible after analyzer is attached.
3. `ImageProxy` frames are closed; preview does not freeze after repeated scans.
4. New production template with independent L anchors is detected.
5. Filled template reads admission number `1234` and expected answers on paper or
   a printed equivalent.
6. Failure reason is visible when no paper is in frame.
7. UI does not flicker or update every frame.
8. Duplicate save/speech suppression still works.
9. Back navigation disposes camera and speech resources.
10. Legacy fallback still works when the local flag is disabled.

## Answers To 10E Audit Questions

1. Current UI scan entry: `AnswerCardApp -> Screen.Scan -> ScanScreen`.
2. `TemplateState` comes from `TemplateStore.loadCollection()` through
   `AnswerCardApp` selected template state.
3. Current scan results return to UI through `CameraPreview(onFrame)` and
   `mainHandler.post` inside `ScanScreen`.
4. CameraX Preview already exists in `CameraPreview`.
5. ImageAnalysis already exists in `CameraPreview`.
6. The old scanning chain is currently running in UI.
7. The old chain uses `OmrScanner`, `CornerDetector`, and `PerspectiveWarp`.
8. Minimal new analyzer seam: add analyzer support to `CameraPreview`, instantiate
   `AndroidOmrImageAnalyzer` in `ScanScreen`.
9. Use a local feature flag and keep fallback rather than deleting replacement
   code in 10F.
10. Keep old scan behavior available as fallback; do not remove the old button or
    scan mode.
11. Do not add debug overlay in 10F.
12. Add minimal recognition status: scanning/recognized/not recognized/error.
13. Avoid UI churn with analyzer gate plus `lastHandledKey` and display-key
    dedupe.
14. Analyzer callbacks should post UI work onto the main thread.
15. `AndroidOmrImageAnalyzer` owns `ImageProxy.close()`; CameraPreview must not
    double-close in the new analyzer branch.
16. Phone testing after 10F should cover permission, preview stability, success,
    failure, duplicate suppression, and fallback.
