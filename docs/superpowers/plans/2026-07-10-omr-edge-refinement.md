# OMR Edge Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct the legacy L-corner template and add evidence-gated local printed-edge refinement so curved or locally shifted cards keep their answer and admission cells aligned without weakening existing safety gates.

**Architecture:** Keep the current four-corner homography as the authoritative baseline. Extract the exact mini-program L templates, then refine projected cells in logical question/admission rows by fitting a robust horizontal residual from multiple printed rectangle edges. Every weak or invalid group returns its original cells byte-for-byte; downstream source-information validation, readers, scoring, and consensus remain unchanged.

**Tech Stack:** Kotlin/JVM, JUnit 4, Gradle, Java AWT test renderer, existing `MiniProgramFrame` and `PerspectiveMapping` primitives.

## Global Constraints

- Keep `omr-core` free of Android, AndroidX, Compose, CameraX, OpenCV, and `android.graphics` imports.
- Do not change coded-marker format, decoding tolerances, quality gates, required-cell validation, or 3/4 full-answer consensus.
- Keep the existing homography result exactly when printed-edge evidence is insufficient or invalid.
- Existing five real-image expectations for answers, admission number, and score must not change.
- Use JDK 21 and the Android SDK described in `LOCAL_DEVELOPMENT.md` for Fedora verification.

---

### Task 1: Exact Mini-Program L-Corner Templates

**Files:**
- Create: `omr-core/src/main/java/com/answercard/grader/miniprogram/MiniProgramCornerTemplate.kt`
- Create: `omr-core/src/test/java/com/answercard/grader/miniprogram/MiniProgramCornerTemplateTest.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/CornerAnchorMatcher.kt`

**Interfaces:**
- Consumes: `MiniProgramCornerKind` and the existing `large = frame.width > 288` decision.
- Produces: `MiniProgramCornerTemplate.points(kind: MiniProgramCornerKind, large: Boolean): List<MiniProgramCornerTemplatePoint>`.

- [ ] **Step 1: Write the failing coordinate-parity test**

Create a parameterized-by-loop JUnit test with the exact final JS coordinates:

```kotlin
private val largeLu = listOf(
    p(0, 0, 0), p(-6, 0, 1), p(0, -6, 1), p(6, 6, 1),
    p(0, 6, 0), p(-6, 6, 1), p(6, -6, 1), p(6, 0, 0),
    p(12, 6, 1), p(6, 12, 1), p(0, 2, 0), p(2, 0, 0),
    p(-6, 2, 1), p(2, -6, 1), p(6, 8, 1), p(8, 6, 1),
    p(0, 4, 0), p(4, 0, 0), p(-6, 4, 1), p(4, -6, 1),
    p(6, 10, 1), p(10, 6, 1),
)

private val largeLd = listOf(
    p(0, 0, 0), p(-6, 0, 1), p(0, 6, 1), p(6, -6, 1),
    p(6, 0, 0), p(0, -6, 0), p(6, 6, 1), p(-6, -6, 1),
    p(12, -6, 1), p(6, -12, 1), p(2, 0, 0), p(0, -2, 0),
    p(2, 6, 1), p(-6, -2, 1), p(8, -6, 1), p(6, -8, 1),
    p(4, 0, 0), p(0, -4, 0), p(4, 6, 1), p(-6, -4, 1),
    p(10, -6, 1), p(6, -10, 1),
)

private val largeRu = listOf(
    p(0, 0, 0), p(6, 0, 1), p(0, -6, 1), p(-6, 6, 1),
    p(0, 6, 0), p(-6, 0, 0), p(6, 6, 1), p(-6, -6, 1),
    p(-6, 12, 1), p(-12, 6, 1), p(0, 2, 0), p(-2, 0, 0),
    p(6, 2, 1), p(-2, -6, 1), p(-6, 8, 1), p(-8, 6, 1),
    p(0, 4, 0), p(-4, 0, 0), p(6, 4, 1), p(-4, -6, 1),
    p(-6, 10, 1), p(-10, 6, 1),
)

private val largeRd = listOf(
    p(0, 0, 0), p(0, 6, 1), p(6, 0, 1), p(-6, -6, 1),
    p(-6, 0, 0), p(0, -6, 0), p(-6, 6, 1), p(6, -6, 1),
    p(-12, -6, 1), p(-6, -12, 1), p(-2, 0, 0), p(0, -2, 0),
    p(-2, 6, 1), p(6, -2, 1), p(-8, -6, 1), p(-6, -8, 1),
    p(-4, 0, 0), p(0, -4, 0), p(-4, 6, 1), p(6, -4, 1),
    p(-10, -6, 1), p(-6, -10, 1),
)
```

Build `largeExpected` from these four lists and `smallExpected` by dividing every large row/column offset by two; all source offsets are even and the JS small branch is exactly the 6→3, 2→1, 4→2 scale. Assert exact order, size 22, and unique `(rowOffset,columnOffset,expected)` tuples for all eight templates.

- [ ] **Step 2: Run the test and verify RED**

Run:

```sh
sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.MiniProgramCornerTemplateTest
```

Expected: compilation failure because `MiniProgramCornerTemplate` and `MiniProgramCornerTemplatePoint` do not exist.

- [ ] **Step 3: Add the exact template object**

Create:

```kotlin
package com.answercard.grader.miniprogram

data class MiniProgramCornerTemplatePoint(
    val rowOffset: Int,
    val columnOffset: Int,
    val expected: Int,
)

object MiniProgramCornerTemplate {
    fun points(kind: MiniProgramCornerKind, large: Boolean): List<MiniProgramCornerTemplatePoint> {
        val main = if (large) 6 else 3
        val fine = if (large) 2 else 1
        return when (kind) {
            MiniProgramCornerKind.LU -> listOf(
                p(0, 0, 0), p(-main, 0, 1), p(0, -main, 1), p(main, main, 1),
                p(0, main, 0), p(-main, main, 1), p(main, -main, 1), p(main, 0, 0),
                p(main * 2, main, 1), p(main, main * 2, 1),
                p(0, fine, 0), p(fine, 0, 0), p(-main, fine, 1), p(fine, -main, 1),
                p(main, main + fine, 1), p(main + fine, main, 1),
                p(0, fine * 2, 0), p(fine * 2, 0, 0), p(-main, fine * 2, 1),
                p(fine * 2, -main, 1), p(main, main + fine * 2, 1), p(main + fine * 2, main, 1),
            )
            MiniProgramCornerKind.LD -> listOf(
                p(0, 0, 0), p(-main, 0, 1), p(0, main, 1), p(main, -main, 1),
                p(main, 0, 0), p(0, -main, 0), p(main, main, 1), p(-main, -main, 1),
                p(main * 2, -main, 1), p(main, -main * 2, 1),
                p(fine, 0, 0), p(0, -fine, 0), p(fine, main, 1), p(-main, -fine, 1),
                p(main + fine, -main, 1), p(main, -main - fine, 1),
                p(fine * 2, 0, 0), p(0, -fine * 2, 0), p(fine * 2, main, 1),
                p(-main, -fine * 2, 1), p(main + fine * 2, -main, 1), p(main, -main - fine * 2, 1),
            )
            MiniProgramCornerKind.RU -> listOf(
                p(0, 0, 0), p(main, 0, 1), p(0, -main, 1), p(-main, main, 1),
                p(0, main, 0), p(-main, 0, 0), p(main, main, 1), p(-main, -main, 1),
                p(-main, main * 2, 1), p(-main * 2, main, 1),
                p(0, fine, 0), p(-fine, 0, 0), p(main, fine, 1), p(-fine, -main, 1),
                p(-main, main + fine, 1), p(-main - fine, main, 1),
                p(0, fine * 2, 0), p(-fine * 2, 0, 0), p(main, fine * 2, 1),
                p(-fine * 2, -main, 1), p(-main, main + fine * 2, 1), p(-main - fine * 2, main, 1),
            )
            MiniProgramCornerKind.RD -> listOf(
                p(0, 0, 0), p(0, main, 1), p(main, 0, 1), p(-main, -main, 1),
                p(-main, 0, 0), p(0, -main, 0), p(-main, main, 1), p(main, -main, 1),
                p(-main * 2, -main, 1), p(-main, -main * 2, 1),
                p(-fine, 0, 0), p(0, -fine, 0), p(-fine, main, 1), p(main, -fine, 1),
                p(-main - fine, -main, 1), p(-main, -main - fine, 1),
                p(-fine * 2, 0, 0), p(0, -fine * 2, 0), p(-fine * 2, main, 1),
                p(main, -fine * 2, 1), p(-main - fine * 2, -main, 1), p(-main, -main - fine * 2, 1),
            )
        }
    }

    private fun p(row: Int, column: Int, expected: Int) =
        MiniProgramCornerTemplatePoint(row, column, expected)
}
```

- [ ] **Step 4: Wire the matcher to the object**

Replace both calls to the private `template(kind, large)` with `MiniProgramCornerTemplate.points(kind, large)`, change the two matcher method parameter types to `List<MiniProgramCornerTemplatePoint>`, and delete the private `TemplatePoint` plus the old `template` function.

- [ ] **Step 5: Run focused and legacy image tests**

Run:

```sh
sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.MiniProgramCornerTemplateTest \
  --tests com.answercard.grader.miniprogram.SolidMarkerCardScanTest \
  --tests com.answercard.grader.miniprogram.DesktopWechatImageScanTest
```

Expected: PASS; the real L-bracket image still reads admission `1234` and score `10.0`.

- [ ] **Step 6: Commit**

```sh
git add omr-core/src/main/java/com/answercard/grader/miniprogram/MiniProgramCornerTemplate.kt \
  omr-core/src/main/java/com/answercard/grader/miniprogram/CornerAnchorMatcher.kt \
  omr-core/src/test/java/com/answercard/grader/miniprogram/MiniProgramCornerTemplateTest.kt
git commit -m "fix(omr): match mini-program corner templates"
```

### Task 2: Evidence-Gated Projected-Cell Edge Refiner

**Files:**
- Create: `omr-core/src/main/java/com/answercard/grader/miniprogram/ProjectedCellEdgeRefiner.kt`
- Create: `omr-core/src/test/java/com/answercard/grader/miniprogram/ProjectedCellEdgeRefinerTest.kt`

**Interfaces:**
- Consumes: `MiniProgramFrame` and an existing `AndroidPaperProjectedCells` homography result.
- Produces: `ProjectedCellEdgeRefiner.refine(frame, projectedCells): AndroidPaperProjectedCells`, preserving original maps exactly for every rejected group.

- [ ] **Step 1: Write RED tests for correction and exact fallback**

Build a white 240×160 frame and predicted cells in two question groups. Draw actual black rectangle borders shifted by `+4 px` for question 0 and `-3 px` for question 1. Assert all four columns of each refined cell move by the known group shift within `1.0 px`, debug reports `edgeRefinement=active`, and rows do not change.

Add separate tests where only one rectangle exists, edge contrast is below 24, duplicate lines have equal scores, and one observation is an outlier. For every rejected group assert:

```kotlin
assertEquals(original.questionCells, refined.questionCells)
assertEquals(original.admissionNumberCells, refined.admissionNumberCells)
assertTrue(refined.debugInfo.any { it.startsWith("edgeRefinementFallbackGroups=") })
```

- [ ] **Step 2: Run and verify RED**

```sh
sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.ProjectedCellEdgeRefinerTest
```

Expected: compilation failure because `ProjectedCellEdgeRefiner` is missing.

- [ ] **Step 3: Implement edge observations**

Create a pure-JVM object with these fixed safety constants and internal records:

```kotlin
object ProjectedCellEdgeRefiner {
    private const val MAX_OFFSET_RATIO = 0.30
    private const val MIN_EDGE_CONTRAST = 24.0
    private const val MIN_WINNER_GAP = 6.0
    private const val MIN_VALID_SAMPLE_RATIO = 0.70
    private const val MIN_GROUP_SPAN_RATIO = 0.45
    private const val MIN_REFINED_WIDTH_RATIO = 0.70
    private const val MAX_REFINED_WIDTH_RATIO = 1.30

    fun refine(frame: MiniProgramFrame, cells: AndroidPaperProjectedCells): AndroidPaperProjectedCells

    private data class CellEntry<K>(val key: K, val cell: MiniProgramCell)
    private data class EdgeObservation(
        val expectedColumn: Double,
        val offset: Double,
        val contrast: Double,
    )
    private data class ResidualModel(val intercept: Double, val slope: Double) {
        fun offsetAt(column: Double): Double = intercept + slope * column
    }
}
```

For each projected edge, sample 11 positions from 10% through 90% of the edge height. Search integer column offsets in `[-floor(width*0.30), +floor(width*0.30)]`. For a left edge compare the line mean to the mean two pixels outside on the left; for a right edge use the right outside strip. Accept the candidate only when contrast, sample ratio, and best-vs-runner-up gap meet the constants.

- [ ] **Step 4: Implement robust per-group fitting and geometry guards**

Group question entries by `questionIndex` and admission entries by `digitIndex`. Require at least three observations from at least two cells. Remove observations whose offset differs from the median by more than `max(2.0, 2.5 * MAD)`, then fit `offset = intercept + slope * expectedColumn` by least squares.

Reject the entire group when coverage, RMSE, endpoint offset, finite-coordinate, width-ratio, ordering, or frame-bound checks fail. On success, apply the model to left and right columns only and keep every row coordinate unchanged. Append stable debug fields:

```text
edgeRefinement=active|fallback
edgeRefinementQuestionGroups=${refinedQuestionGroups}
edgeRefinementAdmissionGroups=${refinedAdmissionGroups}
edgeRefinementFallbackGroups=${fallbackGroups}
edgeRefinementObservations=${acceptedObservations}
edgeRefinementMaxOffset=${format(maxOffset)}
```

- [ ] **Step 5: Run focused tests and refactor with green tests**

```sh
sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.ProjectedCellEdgeRefinerTest
```

Expected: PASS with no warning output.

- [ ] **Step 6: Commit**

```sh
git add omr-core/src/main/java/com/answercard/grader/miniprogram/ProjectedCellEdgeRefiner.kt \
  omr-core/src/test/java/com/answercard/grader/miniprogram/ProjectedCellEdgeRefinerTest.kt
git commit -m "feat(omr): refine cells from printed edges"
```

### Task 3: Production Integration and Solid-Mark Consistency

**Files:**
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidPaperProjectedCells.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidOmrEngine.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidSolidMarkDetector.kt`
- Modify: `omr-core/src/test/java/com/answercard/grader/miniprogram/AndroidSolidMarkDetectorMatchingTest.kt`

**Interfaces:**
- Changes builder to `build(frame: MiniProgramFrame, template: TemplateState, layout: AndroidPaperTemplateLayout, anchors: MiniProgramAnchors)`.
- Adds refined-cell matching as a candidate inside `AndroidSolidMarkDetector.detect(..., projectedCells: AndroidPaperProjectedCells)`.

- [ ] **Step 1: Write failing tests for builder activation and refined solid mapping**

Add a solid-mark matching test with two neighboring projected cells whose image positions are shifted relative to homography. Place a dense component only inside the refined second cell and assert the overlay selects only its key. Assert ambiguous equal-distance refined matches reject rather than select a cell.

- [ ] **Step 2: Run and verify RED**

```sh
sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.AndroidSolidMarkDetectorMatchingTest
```

Expected: compilation failure because the detector does not accept projected cells.

- [ ] **Step 3: Integrate refinement into projected-cell construction**

Change `AndroidPaperProjectedCellBuilder.build` to construct `baseline`, then return:

```kotlin
return ProjectedCellEdgeRefiner.refine(frame, baseline)
```

Update `AndroidOmrEngine` to pass `frame`. Keep `AndroidRequiredCellValidator.validate(frame, projectedCells)` immediately after the refined result, so refinement cannot bypass clipping or information floors.

- [ ] **Step 4: Add projected-cell solid component matching**

Pass `projectedCells` into `AndroidSolidMarkDetector.detect`. Add a `matchProjectedComponents` candidate that compares every dense component center to refined cell quadrilaterals, uses normalized center distance, and accepts a cell only when the point lies within a 15% width/height tolerance and the nearest-vs-runner-up normalized distance gap is at least `0.20`. Include this candidate in the existing best/runner-up ambiguity machinery under reference name `projectedCells`; do not remove the current marker-center/legacy reference candidates.

- [ ] **Step 5: Run integration-focused regression**

```sh
sh gradlew :omr-core:test \
  --tests com.answercard.grader.miniprogram.AndroidSolidMarkDetectorMatchingTest \
  --tests com.answercard.grader.miniprogram.AndroidRequiredCellValidatorTest \
  --tests com.answercard.grader.miniprogram.CodedMarkerCardScanTest \
  --tests com.answercard.grader.miniprogram.SolidMarkerCardScanTest
```

Expected: PASS; existing ambiguity tests remain fail-closed.

- [ ] **Step 6: Commit**

```sh
git add omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidPaperProjectedCells.kt \
  omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidOmrEngine.kt \
  omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidSolidMarkDetector.kt \
  omr-core/src/test/java/com/answercard/grader/miniprogram/AndroidSolidMarkDetectorMatchingTest.kt
git commit -m "feat(omr): use refined geometry in scan path"
```

### Task 4: Curved-Card and Real-Image Accuracy Regression

**Files:**
- Create: `omr-core/src/test/java/com/answercard/grader/miniprogram/TestNonPlanarWarp.kt`
- Create: `omr-core/src/test/java/com/answercard/grader/miniprogram/CurvedCardScanTest.kt`
- Modify: `omr-core/src/test/java/com/answercard/grader/miniprogram/DesktopWechatImageScanTest.kt`

**Interfaces:**
- Produces deterministic non-planar test frames with corners preserved and a known row-dependent horizontal displacement.
- Verifies both coded and legacy production paths, not a test-only reader shortcut.

- [ ] **Step 1: Write the deterministic non-planar warp helper**

Implement inverse resampling with displacement zero at the top/bottom and maximum at the vertical center:

```kotlin
object TestNonPlanarWarp {
    fun horizontalBend(frame: MiniProgramFrame, amplitude: Double): MiniProgramFrame {
        val pixels = IntArray(frame.pixels.size) { 255 }
        val denominator = (frame.height - 1).coerceAtLeast(1).toDouble()
        for (row in 0 until frame.height) {
            val ratio = row / denominator
            val shift = amplitude * kotlin.math.sin(Math.PI * ratio)
            for (column in 0 until frame.width) {
                val sourceColumn = (column - shift).roundToInt()
                if (sourceColumn in 0 until frame.width) {
                    pixels[row * frame.width + column] = frame[row, sourceColumn]
                }
            }
        }
        return MiniProgramFrame(frame.width, frame.height, pixels)
    }
}
```

- [ ] **Step 2: Write coded and legacy curved-card tests**

Render the same known 16-question marks and admission `1234` used by existing scan tests. Bend coded and L-bracket frames with amplitude `12.0 px`, which is inside the 30% budget for the scale-3 option cells. Assert:

```kotlin
assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
assertEquals("1234", result.admissionNumber?.digits)
assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
assertTrue(result.debugInfo.any { it == "edgeRefinement=active" })
assertTrue(result.debugInfo.any { it.startsWith("edgeRefinementQuestionGroups=") })
```

Also add a `24.0 px` high-amplitude case beyond the correction budget and assert it returns `success=false` and `score=null` rather than an incorrect score.

- [ ] **Step 3: Run and tune only from failing evidence**

```sh
sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.CurvedCardScanTest
```

Expected: PASS. If `12.0 px` is already handled by homography, the unit test proving a recovered 12 px edge residual remains the required RED/GREEN evidence; do not increase amplitude or weaken confidence gates merely to force an engine failure before refinement.

- [ ] **Step 4: Strengthen real-image assertions**

In `DesktopWechatImageScanTest`, assert every real photo debug trace contains an explicit `edgeRefinement=active` or `edgeRefinement=fallback`, then keep all existing exact answer/admission/score assertions at both native and 1280 width.

- [ ] **Step 5: Run the scanner regression suite**

```sh
sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.DesktopWechatImageScanTest
sh gradlew :omr-core:test
```

Expected: all tests PASS; no real-image expectation changes.

- [ ] **Step 6: Commit**

```sh
git add omr-core/src/test/java/com/answercard/grader/miniprogram/TestNonPlanarWarp.kt \
  omr-core/src/test/java/com/answercard/grader/miniprogram/CurvedCardScanTest.kt \
  omr-core/src/test/java/com/answercard/grader/miniprogram/DesktopWechatImageScanTest.kt
git commit -m "test(omr): cover curved card recognition"
```

### Task 5: Fedora Full Verification and Completion Audit

**Files:**
- Modify only if verification exposes a regression covered by this design.

**Interfaces:**
- Produces authoritative build/test evidence for the complete repository.

- [ ] **Step 1: Synchronize the current commit to the Fedora mirror using the repository's existing workflow**

Confirm `/data/Sources/android-omr` points at the same commit and has no unrelated changes before running tests. Do not overwrite remote-only user changes.

- [ ] **Step 2: Run core and app unit tests with JDK 21**

```sh
ssh -p 22 fedora 'cd /data/Sources/android-omr && \
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/data/local/android-sdk ANDROID_SDK_ROOT=/data/local/android-sdk && \
  export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH && \
  sh gradlew :omr-core:test && sh gradlew test'
```

Expected: both commands exit 0.

- [ ] **Step 3: Build the debug APK**

```sh
ssh -p 22 fedora 'cd /data/Sources/android-omr && \
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/data/local/android-sdk ANDROID_SDK_ROOT=/data/local/android-sdk && \
  export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH && \
  sh gradlew assembleDebug'
```

Expected: `BUILD SUCCESSFUL` and a debug APK under `app/build/outputs/apk/debug/`.

- [ ] **Step 4: Audit every acceptance item**

Confirm from current files and fresh output:

- all eight 22-point templates equal the JS source;
- curved coded and legacy scans are exact;
- excessive/ambiguous corrections fail closed;
- five real photos keep exact results at native and analysis resolution;
- full tests and APK build pass;
- only the user's untracked comparison document remains unrelated and untouched.

- [ ] **Step 5: Handle verification failures in the owning task**

No verification-only commit is expected. If a failure appears, return to Task 1, 2, 3, or 4 according to the failing behavior, add a reproducing RED test there, implement the minimal fix, rerun that task's focused command, and then repeat all Task 5 commands.
