# OMR Camera-Scan Stability Optimizations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make phone-camera answer-card scanning produce stable, correct scores by switching templates to solid corner markers (with L-bracket fallback), perspective mapping, nearest-center mark matching, 3-frame consensus locking with TTS announcement, and a gyroscope stability gate.

**Architecture:** All recognition math stays in `omr-core` (pure JVM, no Android imports). New `SolidCornerMarkerDetector` runs before the legacy `CornerAnchorMatcher`; anchor `source == "solid-marker"` switches downstream reference points to marker centers. `PerspectiveMapping` (homography) replaces bilinear projection in both directions. The app layer adds `ScanConsensusTracker` gating record/TTS, and a `DeviceStabilityMonitor` gating frame analysis.

**Tech Stack:** Kotlin JVM (omr-core), Android + Compose + CameraX + SensorManager (app), JUnit 4, Robolectric.

**Design spec:** `docs/omr-stability-optimizations.md`

## Global Constraints

- Run everything from repo root with `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew <task>`.
- `omr-core` must stay free of Android/AndroidX/Compose/CameraX imports (`java.awt`/`ImageIO` allowed in omr-core **tests** only).
- Kotlin, 4-space indent, data classes, small focused objects; comments only where geometry is non-obvious.
- Commit style: Conventional Commits with scope, e.g. `feat(omr): ...`, `feat(app): ...`, ending with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- **Pre-existing failing tests (baseline, do NOT try to fix, just don't add new failures):**
  - `AndroidOmrImageScanTest`: `formalScanRejectsProjectedCellsThatAreTooSmallBeforeBubbleRead`, `formalScanRejectsDarkFalseCardInteriorBeforeReaders`, `formalScanRejectsTinyFalseAnchorsBeforeBubbleRead`, `formalScanReadsRenderedProductionImageRotated180Degrees`
  - `CornerAnchorMatcherTest`: `findsFourMiniProgramCornerAnchors`, `choosesInteriorAnchorsWhenFrameBorderCandidatesArePresent`, `reportsBestAndSelectedCornerCandidates`, `scorePrefersClearerLongerAnchor`
- Diagnostic probes `DesktopCameraInstabilityProbeTest` / `DesktopQ11FlipProbeTest` already exist in omr-core tests; they always pass (report writers). Leave them in place.

---

### Task 1: PerspectiveMapping (homography math)

**Files:**
- Create: `omr-core/src/main/java/com/answercard/grader/miniprogram/PerspectiveMapping.kt`
- Test: `omr-core/src/test/java/com/answercard/grader/miniprogram/PerspectiveMappingTest.kt`

**Interfaces:**
- Consumes: nothing (pure math).
- Produces: `data class PerspectivePoint(val x: Double, val y: Double)`; `class PerspectiveMapping` with `fun map(point: PerspectivePoint): PerspectivePoint`, `fun invert(point: PerspectivePoint): PerspectivePoint`, and `companion object { fun fromCorrespondences(source: List<PerspectivePoint>, target: List<PerspectivePoint>): PerspectiveMapping? }` (null when degenerate). Order convention everywhere: **lu, ru, rd, ld**.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PerspectiveMappingTest {
    private val unitSquare = listOf(
        PerspectivePoint(0.0, 0.0),
        PerspectivePoint(1.0, 0.0),
        PerspectivePoint(1.0, 1.0),
        PerspectivePoint(0.0, 1.0),
    )

    @Test
    fun identityMappingReturnsInput() {
        val mapping = PerspectiveMapping.fromCorrespondences(unitSquare, unitSquare)!!
        val mapped = mapping.map(PerspectivePoint(0.3, 0.7))
        assertEquals(0.3, mapped.x, 1e-9)
        assertEquals(0.7, mapped.y, 1e-9)
    }

    @Test
    fun mapsCorrespondenceCornersExactly() {
        val target = listOf(
            PerspectivePoint(10.0, 20.0),
            PerspectivePoint(110.0, 24.0),
            PerspectivePoint(104.0, 130.0),
            PerspectivePoint(6.0, 118.0),
        )
        val mapping = PerspectiveMapping.fromCorrespondences(unitSquare, target)!!
        unitSquare.zip(target).forEach { (source, expected) ->
            val mapped = mapping.map(source)
            assertEquals(expected.x, mapped.x, 1e-6)
            assertEquals(expected.y, mapped.y, 1e-6)
        }
    }

    @Test
    fun invertIsInverseOfMap() {
        val target = listOf(
            PerspectivePoint(10.0, 20.0),
            PerspectivePoint(110.0, 24.0),
            PerspectivePoint(104.0, 130.0),
            PerspectivePoint(6.0, 118.0),
        )
        val mapping = PerspectiveMapping.fromCorrespondences(unitSquare, target)!!
        val original = PerspectivePoint(0.25, 0.6)
        val roundTrip = mapping.invert(mapping.map(original))
        assertEquals(original.x, roundTrip.x, 1e-6)
        assertEquals(original.y, roundTrip.y, 1e-6)
    }

    @Test
    fun perspectiveMappingIsNotAffine() {
        // A true perspective target: parallel source lines must not stay parallel.
        val target = listOf(
            PerspectivePoint(0.0, 0.0),
            PerspectivePoint(100.0, 10.0),
            PerspectivePoint(90.0, 90.0),
            PerspectivePoint(10.0, 80.0),
        )
        val mapping = PerspectiveMapping.fromCorrespondences(unitSquare, target)!!
        val midTop = mapping.map(PerspectivePoint(0.5, 0.0))
        val midBottom = mapping.map(PerspectivePoint(0.5, 1.0))
        val naiveMidTop = PerspectivePoint(50.0, 5.0)
        // Bilinear would land exactly at the edge midpoint; homography must differ.
        assertNotNull(mapping)
        val differs = Math.abs(midTop.x - naiveMidTop.x) > 1e-6 || Math.abs(midTop.y - naiveMidTop.y) > 1e-6 ||
            Math.abs(midBottom.x - 50.0) > 1e-6
        org.junit.Assert.assertTrue(differs)
    }

    @Test
    fun degenerateCollinearPointsReturnNull() {
        val collinear = listOf(
            PerspectivePoint(0.0, 0.0),
            PerspectivePoint(1.0, 0.0),
            PerspectivePoint(2.0, 0.0),
            PerspectivePoint(3.0, 0.0),
        )
        assertNull(PerspectiveMapping.fromCorrespondences(collinear, unitSquare))
    }

    @Test(expected = IllegalArgumentException::class)
    fun requiresFourPoints() {
        PerspectiveMapping.fromCorrespondences(unitSquare.take(3), unitSquare.take(3))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.PerspectiveMappingTest`
Expected: compilation FAILURE ("unresolved reference: PerspectivePoint").

- [ ] **Step 3: Write the implementation**

```kotlin
package com.answercard.grader.miniprogram

import kotlin.math.abs

data class PerspectivePoint(val x: Double, val y: Double)

/**
 * 3x3 homography from 4 point correspondences (order lu, ru, rd, ld).
 * Coefficients are h11..h32 with h33 fixed to 1.
 */
class PerspectiveMapping private constructor(
    private val forward: DoubleArray,
    private val backward: DoubleArray,
) {
    fun map(point: PerspectivePoint): PerspectivePoint = apply(forward, point)

    fun invert(point: PerspectivePoint): PerspectivePoint = apply(backward, point)

    companion object {
        fun fromCorrespondences(
            source: List<PerspectivePoint>,
            target: List<PerspectivePoint>,
        ): PerspectiveMapping? {
            require(source.size == 4 && target.size == 4) { "exactly 4 correspondences required" }
            val forward = solve(source, target) ?: return null
            val backward = solve(target, source) ?: return null
            return PerspectiveMapping(forward = forward, backward = backward)
        }

        private fun apply(h: DoubleArray, point: PerspectivePoint): PerspectivePoint {
            val w = h[6] * point.x + h[7] * point.y + 1.0
            return PerspectivePoint(
                x = (h[0] * point.x + h[1] * point.y + h[2]) / w,
                y = (h[3] * point.x + h[4] * point.y + h[5]) / w,
            )
        }

        private fun solve(source: List<PerspectivePoint>, target: List<PerspectivePoint>): DoubleArray? {
            val m = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val s = source[i]
                val t = target[i]
                m[i * 2] = doubleArrayOf(s.x, s.y, 1.0, 0.0, 0.0, 0.0, -t.x * s.x, -t.x * s.y, t.x)
                m[i * 2 + 1] = doubleArrayOf(0.0, 0.0, 0.0, s.x, s.y, 1.0, -t.y * s.x, -t.y * s.y, t.y)
            }
            for (column in 0 until 8) {
                var pivot = column
                for (row in column + 1 until 8) {
                    if (abs(m[row][column]) > abs(m[pivot][column])) pivot = row
                }
                if (abs(m[pivot][column]) < 1e-12) return null
                val swap = m[column]
                m[column] = m[pivot]
                m[pivot] = swap
                for (row in 0 until 8) {
                    if (row == column) continue
                    val factor = m[row][column] / m[column][column]
                    for (k in column until 9) {
                        m[row][k] -= factor * m[column][k]
                    }
                }
            }
            return DoubleArray(8) { m[it][8] / m[it][it] }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.PerspectiveMappingTest`
Expected: BUILD SUCCESSFUL, all 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add omr-core/src/main/java/com/answercard/grader/miniprogram/PerspectiveMapping.kt \
        omr-core/src/test/java/com/answercard/grader/miniprogram/PerspectiveMappingTest.kt
git commit -m "feat(omr): add 4-point perspective mapping

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Solid corner-marker geometry in TemplateGeometry

**Files:**
- Modify: `omr-core/src/main/java/com/answercard/grader/template/TemplateGeometry.kt` (add after `cornerAnchorReference`, around line 158)
- Test: `omr-core/src/test/java/com/answercard/grader/template/TemplateGeometryCornerMarkerTest.kt`

**Interfaces:**
- Consumes: existing `CardLayout`, `Rect`, `TemplatePoint`, `CornerAnchorReference`, `renderedWidth/renderedHeight`.
- Produces:
  - `TemplateGeometry.CORNER_MARKER_SIZE: Float = 26f`
  - `enum class CornerMarkerStyle { SOLID_SQUARE, L_BRACKET }` (top level in `TemplateGeometry.kt`)
  - `data class CornerMarkerRects(val lu: Rect, val ru: Rect, val ld: Rect, val rd: Rect)` (top level)
  - `fun TemplateGeometry.cornerMarkerRects(layout: CardLayout): CornerMarkerRects` — rects in rendered coordinates
  - `fun TemplateGeometry.cornerMarkerCenters(layout: CardLayout): CornerAnchorReference` — marker centers

- [ ] **Step 1: Write the failing test**

```kotlin
package com.answercard.grader.template

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateGeometryCornerMarkerTest {
    @Test
    fun markerRectsSitInsideCornerMarginsOfRenderedCard() {
        val layout = TemplateGeometry.buildLayout()
        val rects = TemplateGeometry.cornerMarkerRects(layout)
        val margin = TemplateGeometry.CORNER_BRACKET_MARGIN
        val size = TemplateGeometry.CORNER_MARKER_SIZE
        val right = TemplateGeometry.renderedWidth(layout) - margin - size
        val bottom = TemplateGeometry.renderedHeight(layout) - margin - size

        assertEquals(margin, rects.lu.x, 1e-4f)
        assertEquals(margin, rects.lu.y, 1e-4f)
        assertEquals(right, rects.ru.x, 1e-4f)
        assertEquals(margin, rects.ru.y, 1e-4f)
        assertEquals(margin, rects.ld.x, 1e-4f)
        assertEquals(bottom, rects.ld.y, 1e-4f)
        assertEquals(right, rects.rd.x, 1e-4f)
        assertEquals(bottom, rects.rd.y, 1e-4f)
        listOf(rects.lu, rects.ru, rects.ld, rects.rd).forEach { rect ->
            assertEquals(size, rect.w, 1e-4f)
            assertEquals(size, rect.h, 1e-4f)
        }
    }

    @Test
    fun markerCentersAreRectCenters() {
        val layout = TemplateGeometry.buildLayout()
        val rects = TemplateGeometry.cornerMarkerRects(layout)
        val centers = TemplateGeometry.cornerMarkerCenters(layout)

        assertEquals(rects.lu.x + rects.lu.w / 2f, centers.lu.x, 1e-4f)
        assertEquals(rects.lu.y + rects.lu.h / 2f, centers.lu.y, 1e-4f)
        assertEquals(rects.ru.x + rects.ru.w / 2f, centers.ru.x, 1e-4f)
        assertEquals(rects.rd.y + rects.rd.h / 2f, centers.rd.y, 1e-4f)
        assertEquals(rects.ld.x + rects.ld.w / 2f, centers.ld.x, 1e-4f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test --tests com.answercard.grader.template.TemplateGeometryCornerMarkerTest`
Expected: compilation FAILURE ("unresolved reference: cornerMarkerRects").

- [ ] **Step 3: Implement in TemplateGeometry.kt**

Add constant next to the other corner constants (after `CORNER_BRACKET_THICKNESS`, line 35):

```kotlin
    const val CORNER_MARKER_SIZE = 26f
```

Add top-level declarations near `CornerAnchorReference` (bottom of file) and the functions inside `object TemplateGeometry` after `cornerAnchorReference`:

```kotlin
enum class CornerMarkerStyle {
    SOLID_SQUARE,
    L_BRACKET,
}

data class CornerMarkerRects(
    val lu: Rect,
    val ru: Rect,
    val ld: Rect,
    val rd: Rect,
)
```

```kotlin
    fun cornerMarkerRects(layout: CardLayout): CornerMarkerRects {
        val margin = CORNER_BRACKET_MARGIN
        val size = CORNER_MARKER_SIZE
        val right = renderedWidth(layout) - margin - size
        val bottom = renderedHeight(layout) - margin - size
        return CornerMarkerRects(
            lu = Rect(margin, margin, size, size),
            ru = Rect(right, margin, size, size),
            ld = Rect(margin, bottom, size, size),
            rd = Rect(right, bottom, size, size),
        )
    }

    fun cornerMarkerCenters(layout: CardLayout): CornerAnchorReference {
        val rects = cornerMarkerRects(layout)
        return CornerAnchorReference(
            lu = rects.lu.center(),
            ld = rects.ld.center(),
            ru = rects.ru.center(),
            rd = rects.rd.center(),
        )
    }

    private fun Rect.center(): TemplatePoint = TemplatePoint(x + w / 2f, y + h / 2f)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test --tests com.answercard.grader.template.TemplateGeometryCornerMarkerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add omr-core/src/main/java/com/answercard/grader/template/TemplateGeometry.kt \
        omr-core/src/test/java/com/answercard/grader/template/TemplateGeometryCornerMarkerTest.kt
git commit -m "feat(omr): add solid corner marker geometry

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Desktop test renderer square-marker mode

**Files:**
- Modify: `omr-core/src/test/java/com/answercard/grader/miniprogram/DesktopTemplateCardRenderer.kt`
- Test: `omr-core/src/test/java/com/answercard/grader/miniprogram/DesktopTemplateCardRendererMarkerTest.kt`

**Interfaces:**
- Consumes: `CornerMarkerStyle`, `TemplateGeometry.cornerMarkerRects` (Task 2).
- Produces: `DesktopTemplateCardRenderer(template, scale, markerStyle: CornerMarkerStyle = CornerMarkerStyle.L_BRACKET)` (default flips to `SOLID_SQUARE` in Task 7); helper `markAnswerShifted(questionNumber: Int, optionLabel: String, dxUnits: Float)` used by Task 6 tests.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.answercard.grader.miniprogram

import com.answercard.grader.template.CornerMarkerStyle
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopTemplateCardRendererMarkerTest {
    @Test
    fun solidSquareModeFillsMarkerRectsAndSkipsBracketArms() {
        val renderer = DesktopTemplateCardRenderer(
            template = TemplateState.default(),
            scale = 3f,
            markerStyle = CornerMarkerStyle.SOLID_SQUARE,
        )
        val frame = renderer.frame()
        val layout = TemplateGeometry.buildLayout(TemplateState.default())
        val rects = TemplateGeometry.cornerMarkerRects(layout)
        val scale = 3f

        // Center of each marker square is black.
        listOf(rects.lu, rects.ru, rects.ld, rects.rd).forEach { rect ->
            val row = ((rect.y + rect.h / 2f) * scale).toInt()
            val column = ((rect.x + rect.w / 2f) * scale).toInt()
            assertTrue("marker center should be dark", frame[row, column] < 100)
        }

        // A point on the old bracket top arm beyond the square (margin+30, margin+4) is white now.
        val armRow = ((TemplateGeometry.CORNER_BRACKET_MARGIN + 4f) * scale).toInt()
        val armColumn = ((TemplateGeometry.CORNER_BRACKET_MARGIN + 30f) * scale).toInt()
        assertTrue("bracket arm area should be white in square mode", frame[armRow, armColumn] > 200)
    }

    @Test
    fun defaultModeStillDrawsLBrackets() {
        val renderer = DesktopTemplateCardRenderer(template = TemplateState.default(), scale = 3f)
        val frame = renderer.frame()
        val scale = 3f
        val armRow = ((TemplateGeometry.CORNER_BRACKET_MARGIN + 4f) * scale).toInt()
        val armColumn = ((TemplateGeometry.CORNER_BRACKET_MARGIN + 30f) * scale).toInt()
        assertTrue("bracket arm should be dark in L mode", frame[armRow, armColumn] < 100)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.DesktopTemplateCardRendererMarkerTest`
Expected: compilation FAILURE (no `markerStyle` parameter).

- [ ] **Step 3: Implement renderer changes**

In `DesktopTemplateCardRenderer.kt`, change the constructor and corner drawing:

```kotlin
class DesktopTemplateCardRenderer(
    template: TemplateState,
    private val scale: Float = 3f,
    private val markerStyle: CornerMarkerStyle = CornerMarkerStyle.L_BRACKET,
) {
```

(add `import com.answercard.grader.template.CornerMarkerStyle`)

In `init`, replace `drawCornerBrackets(graphics)` with:

```kotlin
            when (markerStyle) {
                CornerMarkerStyle.SOLID_SQUARE -> drawCornerMarkers(graphics)
                CornerMarkerStyle.L_BRACKET -> drawCornerBrackets(graphics)
            }
```

Add next to `drawCornerBrackets`:

```kotlin
    private fun drawCornerMarkers(graphics: Graphics2D) {
        val rects = TemplateGeometry.cornerMarkerRects(layout)
        listOf(rects.lu, rects.ru, rects.ld, rects.rd).forEach { rect ->
            graphics.fill(Rectangle2D.Float(rect.x, rect.y, rect.w, rect.h))
        }
    }
```

Add the shifted-mark helper after `markAnswer`:

```kotlin
    fun markAnswerShifted(questionNumber: Int, optionLabel: String, dxUnits: Float) {
        val rect = layout.options.single { it.question == questionNumber && it.option == optionLabel }.rect
        val shifted = Rect(rect.x + dxUnits, rect.y, rect.w, rect.h)
        fillMark(TemplateGeometry.renderedRect(shifted))
    }
```

- [ ] **Step 4: Run tests (new + existing renderer users)**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test`
Expected: BUILD SUCCESSFUL (default stays L_BRACKET, so existing tests unaffected).

- [ ] **Step 5: Commit**

```bash
git add omr-core/src/test/java/com/answercard/grader/miniprogram/DesktopTemplateCardRenderer.kt \
        omr-core/src/test/java/com/answercard/grader/miniprogram/DesktopTemplateCardRendererMarkerTest.kt
git commit -m "test(omr): render solid corner markers in desktop card renderer

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Component scanner extraction + SolidCornerMarkerDetector

**Files:**
- Create: `omr-core/src/main/java/com/answercard/grader/miniprogram/MiniProgramComponentScanner.kt`
- Create: `omr-core/src/main/java/com/answercard/grader/miniprogram/SolidCornerMarkerDetector.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidSolidMarkDetector.kt` (replace private `findComponents`/`Component`/`ComponentRect`/`enqueue` with scanner usage)
- Test: `omr-core/src/test/java/com/answercard/grader/miniprogram/SolidCornerMarkerDetectorTest.kt`

**Interfaces:**
- Consumes: `MiniProgramFrame`, `MiniProgramGeometry.threshold`, `MiniProgramGeometry.isQuad`, `DesktopTemplateCardRenderer` with `SOLID_SQUARE` (tests).
- Produces:
  - `data class MiniProgramComponentRect(val left: Int, val top: Int, val right: Int, val bottom: Int)` with `width`/`height` getters
  - `data class MiniProgramComponent(val rect: MiniProgramComponentRect, val area: Int, val centroidRow: Double, val centroidColumn: Double)` with `fillRatio: Float`, `centerRow: Double`, `centerColumn: Double` (bbox centers, kept for the mark detector)
  - `object MiniProgramComponentScanner { fun scan(frame: MiniProgramFrame, threshold: Int): List<MiniProgramComponent> }`
  - `object SolidCornerMarkerDetector { const val SOURCE = "solid-marker"; fun findAnchors(frame: MiniProgramFrame, expectedAspectRatio: Double? = null): MiniProgramAnchors? }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.answercard.grader.miniprogram

import com.answercard.grader.template.CornerMarkerStyle
import com.answercard.grader.template.TemplateGeometry
import com.answercard.grader.template.TemplateState
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolidCornerMarkerDetectorTest {
    @Test
    fun findsFourMarkerCentroidsOnSquareCard() {
        val template = TemplateState.default()
        val renderer = DesktopTemplateCardRenderer(template, scale = 3f, markerStyle = CornerMarkerStyle.SOLID_SQUARE)
        renderer.markAnswer(1, "A")
        renderer.markAdmissionNumber("1234")
        val frame = renderer.frame()
        val layout = TemplateGeometry.buildLayout(template)
        val centers = TemplateGeometry.cornerMarkerCenters(layout)

        val anchors = SolidCornerMarkerDetector.findAnchors(
            frame = frame,
            expectedAspectRatio = TemplateGeometry.renderedWidth(layout).toDouble() /
                TemplateGeometry.renderedHeight(layout).toDouble(),
        )

        assertNotNull(anchors)
        val scale = 3f
        fun assertNear(point: MiniProgramPoint, x: Float, y: Float) {
            assertTrue("row ${point.row} vs ${(y * scale)}", abs(point.row - y * scale) <= 2.0)
            assertTrue("column ${point.column} vs ${(x * scale)}", abs(point.column - x * scale) <= 2.0)
        }
        assertNear(anchors!!.lu.point, centers.lu.x, centers.lu.y)
        assertNear(anchors.ru.point, centers.ru.x, centers.ru.y)
        assertNear(anchors.ld.point, centers.ld.x, centers.ld.y)
        assertNear(anchors.rd.point, centers.rd.x, centers.rd.y)
        assertEquals(SolidCornerMarkerDetector.SOURCE, anchors.lu.source)
        assertEquals(SolidCornerMarkerDetector.SOURCE, anchors.rd.source)
    }

    @Test
    fun returnsNullOnLBracketCard() {
        val renderer = DesktopTemplateCardRenderer(TemplateState.default(), scale = 3f)
        val anchors = SolidCornerMarkerDetector.findAnchors(renderer.frame())
        assertNull(anchors)
    }

    @Test
    fun answerMarksDoNotDisplaceCornerMarkers() {
        val template = TemplateState.default()
        val renderer = DesktopTemplateCardRenderer(template, scale = 3f, markerStyle = CornerMarkerStyle.SOLID_SQUARE)
        template.questions.forEach { question -> renderer.markAnswer(question.number, "A") }
        val frame = renderer.frame()
        val layout = TemplateGeometry.buildLayout(template)
        val centers = TemplateGeometry.cornerMarkerCenters(layout)

        val anchors = SolidCornerMarkerDetector.findAnchors(frame)

        assertNotNull(anchors)
        assertTrue(abs(anchors!!.lu.point.column - centers.lu.x * 3f) <= 2.0)
        assertTrue(abs(anchors.rd.point.row - centers.rd.y * 3f) <= 2.0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.SolidCornerMarkerDetectorTest`
Expected: compilation FAILURE ("unresolved reference: SolidCornerMarkerDetector").

- [ ] **Step 3: Create MiniProgramComponentScanner.kt**

```kotlin
package com.answercard.grader.miniprogram

data class MiniProgramComponentRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class MiniProgramComponent(
    val rect: MiniProgramComponentRect,
    val area: Int,
    val centroidRow: Double,
    val centroidColumn: Double,
) {
    val fillRatio: Float get() = area.toFloat() / (rect.width * rect.height).coerceAtLeast(1)
    val centerRow: Double get() = (rect.top + rect.bottom - 1) / 2.0
    val centerColumn: Double get() = (rect.left + rect.right - 1) / 2.0
}

object MiniProgramComponentScanner {
    fun scan(frame: MiniProgramFrame, threshold: Int): List<MiniProgramComponent> {
        val mask = BooleanArray(frame.width * frame.height) { frame.pixels[it] < threshold }
        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        val components = mutableListOf<MiniProgramComponent>()
        for (start in mask.indices) {
            if (visited[start] || !mask[start]) {
                visited[start] = true
                continue
            }
            var minRow = start / frame.width
            var maxRow = minRow
            var minColumn = start % frame.width
            var maxColumn = minColumn
            var count = 0
            var sumRow = 0L
            var sumColumn = 0L
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            while (head < tail) {
                val index = queue[head++]
                val row = index / frame.width
                val column = index % frame.width
                count += 1
                sumRow += row
                sumColumn += column
                minRow = minOf(minRow, row)
                maxRow = maxOf(maxRow, row)
                minColumn = minOf(minColumn, column)
                maxColumn = maxOf(maxColumn, column)
                tail = enqueue(mask, visited, queue, tail, frame.width, frame.height, row - 1, column)
                tail = enqueue(mask, visited, queue, tail, frame.width, frame.height, row + 1, column)
                tail = enqueue(mask, visited, queue, tail, frame.width, frame.height, row, column - 1)
                tail = enqueue(mask, visited, queue, tail, frame.width, frame.height, row, column + 1)
            }
            components += MiniProgramComponent(
                rect = MiniProgramComponentRect(
                    left = minColumn,
                    top = minRow,
                    right = maxColumn + 1,
                    bottom = maxRow + 1,
                ),
                area = count,
                centroidRow = sumRow.toDouble() / count,
                centroidColumn = sumColumn.toDouble() / count,
            )
        }
        return components
    }

    private fun enqueue(
        mask: BooleanArray,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int,
        width: Int,
        height: Int,
        row: Int,
        column: Int,
    ): Int {
        if (row !in 0 until height || column !in 0 until width) return tail
        val index = row * width + column
        if (visited[index]) return tail
        visited[index] = true
        if (!mask[index]) return tail
        queue[tail] = index
        return tail + 1
    }
}
```

- [ ] **Step 4: Create SolidCornerMarkerDetector.kt**

```kotlin
package com.answercard.grader.miniprogram

import kotlin.math.abs
import kotlin.math.roundToInt

object SolidCornerMarkerDetector {
    const val SOURCE = "solid-marker"
    private const val MIN_FILL_RATIO = 0.85f
    private const val MIN_SIZE_RATIO = 0.012
    private const val MAX_SIZE_RATIO = 0.12
    private const val MAX_BOX_ASPECT_DEVIATION = 0.35
    private const val MAX_CANDIDATES_PER_CORNER = 3
    private const val MAX_QUAD_ASPECT_RELATIVE_DEVIATION = 0.45
    private const val MAX_SIZE_SPREAD = 2.0

    fun findAnchors(frame: MiniProgramFrame, expectedAspectRatio: Double? = null): MiniProgramAnchors? {
        val threshold = MiniProgramGeometry.threshold(frame)
        val minSize = maxOf(8, (minOf(frame.width, frame.height) * MIN_SIZE_RATIO).toInt())
        val maxSize = (minOf(frame.width, frame.height) * MAX_SIZE_RATIO).toInt()
        val candidates = MiniProgramComponentScanner.scan(frame, threshold)
            .asSequence()
            .filter { it.fillRatio >= MIN_FILL_RATIO }
            .filter { it.rect.width in minSize..maxSize && it.rect.height in minSize..maxSize }
            .filter {
                val aspect = it.rect.width.toDouble() / it.rect.height.coerceAtLeast(1).toDouble()
                abs(aspect - 1.0) <= MAX_BOX_ASPECT_DEVIATION
            }
            .filter { it.rect.left > 0 && it.rect.top > 0 && it.rect.right < frame.width && it.rect.bottom < frame.height }
            .toList()
        if (candidates.size < 4) return null

        val lastRow = frame.height - 1.0
        val lastColumn = frame.width - 1.0
        val luChoices = nearest(candidates, row = 0.0, column = 0.0)
        val ruChoices = nearest(candidates, row = 0.0, column = lastColumn)
        val ldChoices = nearest(candidates, row = lastRow, column = 0.0)
        val rdChoices = nearest(candidates, row = lastRow, column = lastColumn)

        var best: MiniProgramAnchors? = null
        var bestScore = Double.MAX_VALUE
        for (lu in luChoices) for (ru in ruChoices) for (ld in ldChoices) for (rd in rdChoices) {
            val four = listOf(lu, ru, ld, rd)
            if (four.distinct().size != 4) continue
            val luPoint = lu.toAnchorPoint()
            val ruPoint = ru.toAnchorPoint()
            val ldPoint = ld.toAnchorPoint()
            val rdPoint = rd.toAnchorPoint()
            if (luPoint.column >= ruPoint.column || ldPoint.column >= rdPoint.column) continue
            if (luPoint.row >= ldPoint.row || ruPoint.row >= rdPoint.row) continue

            val sizes = four.map { minOf(it.rect.width, it.rect.height).toDouble() }
            val spread = sizes.max() / sizes.min()
            if (spread > MAX_SIZE_SPREAD) continue

            val width = (distance(luPoint, ruPoint) + distance(ldPoint, rdPoint)) / 2.0
            val height = (distance(luPoint, ldPoint) + distance(ruPoint, rdPoint)) / 2.0
            if (height <= 0.0) continue
            val aspect = width / height
            val aspectDeviation = expectedAspectRatio?.let { abs(aspect - it) / it } ?: 0.0
            if (aspectDeviation > MAX_QUAD_ASPECT_RELATIVE_DEVIATION) continue

            val score = aspectDeviation + (spread - 1.0)
            if (score < bestScore) {
                bestScore = score
                best = MiniProgramAnchors(
                    lu = lu.toCandidate(MiniProgramCornerKind.LU),
                    ld = ld.toCandidate(MiniProgramCornerKind.LD),
                    ru = ru.toCandidate(MiniProgramCornerKind.RU),
                    rd = rd.toCandidate(MiniProgramCornerKind.RD),
                    quadCheck = MiniProgramGeometry.isQuad(luPoint, ldPoint, ruPoint, rdPoint),
                )
            }
        }
        return best
    }

    private fun nearest(candidates: List<MiniProgramComponent>, row: Double, column: Double): List<MiniProgramComponent> =
        candidates.sortedBy { component ->
            val dr = component.centroidRow - row
            val dc = component.centroidColumn - column
            dr * dr + dc * dc
        }.take(MAX_CANDIDATES_PER_CORNER)

    private fun MiniProgramComponent.toAnchorPoint(): MiniProgramPoint =
        MiniProgramPoint(row = centroidRow.roundToInt(), column = centroidColumn.roundToInt())

    private fun MiniProgramComponent.toCandidate(kind: MiniProgramCornerKind): MiniProgramCornerCandidate =
        MiniProgramCornerCandidate(
            kind = kind,
            point = toAnchorPoint(),
            length = minOf(rect.width, rect.height),
            source = SOURCE,
        )

    private fun distance(a: MiniProgramPoint, b: MiniProgramPoint): Double {
        val dr = (a.row - b.row).toDouble()
        val dc = (a.column - b.column).toDouble()
        return kotlin.math.sqrt(dr * dr + dc * dc)
    }
}
```

- [ ] **Step 5: Refactor AndroidSolidMarkDetector to use the scanner**

In `AndroidSolidMarkDetector.kt`:
- Delete the private declarations `ComponentRect`, `Component`, `findComponents`, `enqueue`, and `ComponentRect.touchesBorder`.
- Replace `denseComponents` with:

```kotlin
    private fun denseComponents(frame: MiniProgramFrame): List<MiniProgramComponent> {
        val threshold = MiniProgramGeometry.threshold(frame)
        val minArea = maxOf(80, (frame.width * frame.height * MIN_COMPONENT_AREA_RATIO).toInt())
        val minSize = maxOf(8, (minOf(frame.width, frame.height) * MIN_COMPONENT_SIZE_RATIO).toInt())
        return MiniProgramComponentScanner.scan(frame, threshold)
            .filter { component -> component.area >= minArea }
            .filter { component -> component.rect.width >= minSize && component.rect.height >= minSize }
            .filter { component -> component.fillRatio >= MIN_COMPONENT_FILL_RATIO }
            .filterNot { component ->
                component.rect.left <= 0 || component.rect.top <= 0 ||
                    component.rect.right >= frame.width || component.rect.bottom >= frame.height
            }
    }
```

- Update `matchComponents` signature to `components: List<MiniProgramComponent>` (the body already uses `component.centerRow`/`component.centerColumn`, which `MiniProgramComponent` provides).

- [ ] **Step 6: Run tests**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test`
Expected: BUILD SUCCESSFUL — new detector tests pass, `DesktopWechatImageScanTest` and all existing omr-core tests still pass (scanner refactor is behavior-preserving).

- [ ] **Step 7: Commit**

```bash
git add omr-core/src/main/java/com/answercard/grader/miniprogram/MiniProgramComponentScanner.kt \
        omr-core/src/main/java/com/answercard/grader/miniprogram/SolidCornerMarkerDetector.kt \
        omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidSolidMarkDetector.kt \
        omr-core/src/test/java/com/answercard/grader/miniprogram/SolidCornerMarkerDetectorTest.kt
git commit -m "feat(omr): detect solid corner markers by centroid

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Engine dual-path anchors + perspective projection

**Files:**
- Create: `omr-core/src/main/java/com/answercard/grader/miniprogram/AnchorReferenceResolver.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidOmrEngine.kt` (scan() anchor stage, lines 28-45)
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidPaperProjectedCells.kt` (`AndroidPaperCoordinateProjector`, lines 72-107)
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidSolidMarkDetector.kt` (`sourceReferenceCandidates`, `invert` usage in `matchComponents`)
- Test: `omr-core/src/test/java/com/answercard/grader/miniprogram/SolidMarkerCardScanTest.kt`

**Interfaces:**
- Consumes: `SolidCornerMarkerDetector.findAnchors` (Task 4), `PerspectiveMapping` (Task 1), `TemplateGeometry.cornerMarkerCenters` (Task 2).
- Produces: `object AnchorReferenceResolver { fun isSolidMarker(anchors: MiniProgramAnchors): Boolean; fun projectionReference(cardLayout: CardLayout, anchors: MiniProgramAnchors): CornerAnchorReference }`. Engine debugInfo gains `anchorPath=solid-marker` / `anchorPath=l-bracket`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.answercard.grader.miniprogram

import com.answercard.grader.template.CornerMarkerStyle
import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolidMarkerCardScanTest {
    private fun template(): TemplateState =
        TemplateState(
            name = "solid marker scan",
            questions = (1..16).map { number ->
                val answer = when (number) {
                    1 -> "A"
                    2 -> "B"
                    6 -> "C"
                    11 -> "D"
                    16 -> "A"
                    else -> "A"
                }
                val score = if (number in listOf(1, 2, 6, 11, 16)) 2 else 0
                QuestionSetting(number = number, answer = answer, score = score)
            },
        )

    private fun renderedFrame(): MiniProgramFrame {
        val renderer = DesktopTemplateCardRenderer(template(), scale = 3f, markerStyle = CornerMarkerStyle.SOLID_SQUARE)
        renderer.markAnswer(1, "A")
        renderer.markAnswer(2, "B")
        renderer.markAnswer(6, "C")
        renderer.markAnswer(11, "D")
        renderer.markAnswer(16, "A")
        renderer.markAdmissionNumber("1234")
        return renderer.frame()
    }

    @Test
    fun flatSquareCardScansViaSolidMarkerPath() {
        val result = AndroidOmrEngine.scan(renderedFrame(), template())

        assertTrue(result.debugInfo.joinToString(), result.success)
        assertTrue(result.debugInfo.contains("anchorPath=solid-marker"))
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun warpedSquareCardScansCorrectly() {
        val warped = TestPerspectiveWarp.warp(
            frame = renderedFrame(),
            // pull corners inward asymmetrically (angled shot); inward keeps markers off the frame border
            luShift = 10 to 6,
            ruShift = -20 to 4,
            ldShift = 6 to -4,
            rdShift = -14 to -10,
        )
        val result = AndroidOmrEngine.scan(warped, template())

        assertTrue(result.debugInfo.joinToString(), result.success)
        assertTrue(result.debugInfo.contains("anchorPath=solid-marker"))
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
        assertEquals(10.0, result.score?.totalScore ?: -1.0, 0.0)
    }

    @Test
    fun lBracketCardStillScansViaFallback() {
        val renderer = DesktopTemplateCardRenderer(template(), scale = 3f, markerStyle = CornerMarkerStyle.L_BRACKET)
        renderer.markAnswer(1, "A")
        renderer.markAdmissionNumber("1234")
        val result = AndroidOmrEngine.scan(renderer.frame(), template())

        assertTrue(result.debugInfo.joinToString(), result.success)
        assertTrue(result.debugInfo.contains("anchorPath=l-bracket"))
        assertEquals("1234", result.admissionNumber?.digits)
        assertEquals(2.0, result.score?.totalScore ?: -1.0, 0.0)
    }
}

/** Warps a frame so that its four corners move by the given (dx, dy) pixel shifts. */
object TestPerspectiveWarp {
    fun warp(
        frame: MiniProgramFrame,
        luShift: Pair<Int, Int>,
        ruShift: Pair<Int, Int>,
        ldShift: Pair<Int, Int>,
        rdShift: Pair<Int, Int>,
    ): MiniProgramFrame {
        val w = frame.width - 1.0
        val h = frame.height - 1.0
        val sourceCorners = listOf(
            PerspectivePoint(0.0, 0.0),
            PerspectivePoint(w, 0.0),
            PerspectivePoint(w, h),
            PerspectivePoint(0.0, h),
        )
        val targetCorners = listOf(
            PerspectivePoint(0.0 + luShift.first, 0.0 + luShift.second),
            PerspectivePoint(w + ruShift.first, 0.0 + ruShift.second),
            PerspectivePoint(w + rdShift.first, h + rdShift.second),
            PerspectivePoint(0.0 + ldShift.first, h + ldShift.second),
        )
        // For each destination pixel, look up the source pixel (inverse warp).
        val mapping = PerspectiveMapping.fromCorrespondences(targetCorners, sourceCorners)!!
        val pixels = IntArray(frame.width * frame.height)
        for (row in 0 until frame.height) {
            for (column in 0 until frame.width) {
                val source = mapping.map(PerspectivePoint(column.toDouble(), row.toDouble()))
                val sr = Math.round(source.y).toInt()
                val sc = Math.round(source.x).toInt()
                pixels[row * frame.width + column] =
                    if (sr in 0 until frame.height && sc in 0 until frame.width) frame.pixels[sr * frame.width + sc] else 255
            }
        }
        return MiniProgramFrame(width = frame.width, height = frame.height, pixels = pixels)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.SolidMarkerCardScanTest`
Expected: FAIL — `anchorPath=` debug entries missing (and the solid path does not exist yet, so the square card may fail entirely or scan via bracket references with wrong geometry).

- [ ] **Step 3: Create AnchorReferenceResolver.kt**

```kotlin
package com.answercard.grader.miniprogram

import com.answercard.grader.template.CardLayout
import com.answercard.grader.template.CornerAnchorReference
import com.answercard.grader.template.TemplateGeometry

object AnchorReferenceResolver {
    fun isSolidMarker(anchors: MiniProgramAnchors): Boolean =
        listOf(anchors.lu, anchors.ld, anchors.ru, anchors.rd)
            .all { it.source == SolidCornerMarkerDetector.SOURCE }

    fun projectionReference(cardLayout: CardLayout, anchors: MiniProgramAnchors): CornerAnchorReference =
        if (isSolidMarker(anchors)) {
            TemplateGeometry.cornerMarkerCenters(cardLayout)
        } else {
            TemplateGeometry.cornerAnchorReference(cardLayout)
        }
}
```

- [ ] **Step 4: Engine dual path (AndroidOmrEngine.scan)**

Replace lines 28-45 (`val cornerMatch = ...` through the `?: return` block) with:

```kotlin
        val solidAnchors = SolidCornerMarkerDetector.findAnchors(
            frame = frame,
            expectedAspectRatio = expectedRenderedAspectRatio(template),
        )
        val cornerMatch = if (solidAnchors == null) {
            CornerAnchorMatcher.findAnchorsWithDiagnostics(
                frame = frame,
                expectedAspectRatio = expectedRenderedAspectRatio(template),
            )
        } else {
            null
        }
        val anchorPath = if (solidAnchors != null) "anchorPath=solid-marker" else "anchorPath=l-bracket"
        val cornerDebugInfo = cornerMatch?.diagnostics?.debugInfo().orEmpty() + anchorPath
        val anchors = solidAnchors ?: cornerMatch?.anchors
            ?: return AndroidOmrResult(
                success = false,
                failureReason = "corner anchors not found",
                layout = layout,
                anchors = null,
                grid = null,
                answerArea = null,
                admissionNumber = null,
                score = null,
                warnings = emptyList(),
                debugInfo = debugInfo + cornerDebugInfo + "failureStage=corner" + "corner anchors not found",
            )
```

- [ ] **Step 5: Perspective projection in AndroidPaperCoordinateProjector**

Replace the `AndroidPaperCoordinateProjector` class body (lines 72-107 of `AndroidPaperProjectedCells.kt`) with:

```kotlin
private class AndroidPaperCoordinateProjector(
    cardLayout: CardLayout,
    anchors: MiniProgramAnchors,
) {
    private val source = AnchorReferenceResolver.projectionReference(cardLayout, anchors)
    private val targetLu = anchors.lu.point.toGridPoint()
    private val targetLd = anchors.ld.point.toGridPoint()
    private val targetRu = anchors.ru.point.toGridPoint()
    private val targetRd = anchors.rd.point.toGridPoint()
    private val mapping: PerspectiveMapping? = PerspectiveMapping.fromCorrespondences(
        source = listOf(source.lu, source.ru, source.rd, source.ld)
            .map { PerspectivePoint(it.x.toDouble(), it.y.toDouble()) },
        target = listOf(targetLu, targetRu, targetRd, targetLd)
            .map { PerspectivePoint(it.column, it.row) },
    )

    fun cell(row: Int, column: Int, rect: Rect): MiniProgramCell =
        MiniProgramCell(
            row = row,
            column = column,
            leftTop = project(TemplatePoint(rect.x, rect.y)),
            rightTop = project(TemplatePoint(rect.x + rect.w, rect.y)),
            leftBottom = project(TemplatePoint(rect.x, rect.y + rect.h)),
            rightBottom = project(TemplatePoint(rect.x + rect.w, rect.y + rect.h)),
        )

    private fun project(point: TemplatePoint): MiniProgramGridPoint {
        val mapped = mapping?.map(PerspectivePoint(point.x.toDouble(), point.y.toDouble()))
        if (mapped != null) return MiniProgramGridPoint(row = mapped.y, column = mapped.x)
        // Degenerate anchors: fall back to bilinear interpolation.
        val rowRatio = ((point.y - source.lu.y) / (source.ld.y - source.lu.y)).toDouble()
        val columnRatio = ((point.x - source.lu.x) / (source.ru.x - source.lu.x)).toDouble()
        return MiniProgramGridBuilder.interpolate(
            lu = targetLu,
            ld = targetLd,
            ru = targetRu,
            rd = targetRd,
            rowRatio = rowRatio,
            columnRatio = columnRatio,
        )
    }

    private fun MiniProgramPoint.toGridPoint(): MiniProgramGridPoint =
        MiniProgramGridPoint(row = row.toDouble(), column = column.toDouble())
}
```

- [ ] **Step 6: Marker-center reference + perspective inversion in AndroidSolidMarkDetector**

Replace `sourceReferenceCandidates` with:

```kotlin
    private fun sourceReferenceCandidates(
        cardLayout: CardLayout,
        anchors: MiniProgramAnchors,
    ): List<Pair<String, CornerAnchorReferencePoints>> {
        if (AnchorReferenceResolver.isSolidMarker(anchors)) {
            val centers = TemplateGeometry.cornerMarkerCenters(cardLayout)
            return listOf(
                "markerCenters" to CornerAnchorReferencePoints(
                    lu = centers.lu,
                    ld = centers.ld,
                    ru = centers.ru,
                    rd = centers.rd,
                ),
            )
        }
        val reference = TemplateGeometry.cornerAnchorReference(cardLayout)
        val outer = CornerAnchorReferencePoints(
            lu = reference.lu,
            ld = reference.ld,
            ru = reference.ru,
            rd = reference.rd,
        )

        val adjustedRight = reference.ru.x -
            (TemplateGeometry.CORNER_BRACKET_SIZE - TemplateGeometry.CORNER_BRACKET_THICKNESS)
        val innerRight = CornerAnchorReferencePoints(
            lu = reference.lu,
            ld = reference.ld,
            ru = TemplatePoint(adjustedRight, reference.ru.y),
            rd = TemplatePoint(adjustedRight, reference.rd.y),
        )

        val strongTraceCount = listOf(anchors.lu, anchors.ld, anchors.ru, anchors.rd)
            .count { it.source == "strong-trace" }
        return if (strongTraceCount < 3) {
            listOf("outer" to outer, "innerRight" to innerRight)
        } else {
            listOf("innerRight" to innerRight, "outer" to outer)
        }
    }
```

In `matchComponents`, build a perspective mapping once and use it for inversion, keeping the Newton fallback:

```kotlin
    private fun matchComponents(
        components: List<MiniProgramComponent>,
        anchors: MiniProgramAnchors,
        source: CornerAnchorReferencePoints,
        cardLayout: CardLayout,
        questionIndexByNumber: Map<Int, Int>,
        admissionNumberDigits: Int,
        referenceName: String,
    ): ComponentMatchResult {
        val questionCells = mutableSetOf<AndroidPaperQuestionCellKey>()
        val admissionNumberCells = mutableSetOf<AndroidPaperAdmissionNumberCellKey>()
        var matchedComponents = 0
        var totalCenterDistance = 0.0
        val mapping = PerspectiveMapping.fromCorrespondences(
            source = listOf(source.lu, source.ru, source.rd, source.ld)
                .map { PerspectivePoint(it.x.toDouble(), it.y.toDouble()) },
            target = listOf(anchors.lu, anchors.ru, anchors.rd, anchors.ld)
                .map { PerspectivePoint(it.point.column.toDouble(), it.point.row.toDouble()) },
        )
        components.forEach { component ->
            val sourcePoint = mapping
                ?.invert(PerspectivePoint(component.centerColumn, component.centerRow))
                ?.let { TemplatePoint(it.x.toFloat(), it.y.toFloat()) }
                ?: invert(
                    target = MiniProgramGridPoint(row = component.centerRow, column = component.centerColumn),
                    anchors = anchors,
                    source = source,
                )
                ?: return@forEach
            val question = matchQuestionCell(cardLayout, questionIndexByNumber, sourcePoint)
            val admission = matchAdmissionNumberCell(cardLayout, admissionNumberDigits, sourcePoint)
            question?.let { match ->
                questionCells += match.key
                totalCenterDistance += match.centerDistance
            }
            admission?.let { match ->
                admissionNumberCells += match.key
                totalCenterDistance += match.centerDistance
            }
            if (question != null || admission != null) matchedComponents += 1
        }
        return ComponentMatchResult(
            referenceName = referenceName,
            questionCells = questionCells,
            admissionNumberCells = admissionNumberCells,
            matchedComponents = matchedComponents,
            totalCenterDistance = totalCenterDistance,
        )
    }
```

- [ ] **Step 7: Run all omr-core tests**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test`
Expected: BUILD SUCCESSFUL — new `SolidMarkerCardScanTest` passes; `DesktopWechatImageScanTest` (L photo, fallback + perspective mapping on bracket references) passes; headerless tests pass.

- [ ] **Step 8: Commit**

```bash
git add omr-core/src/main/java/com/answercard/grader/miniprogram/AnchorReferenceResolver.kt \
        omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidOmrEngine.kt \
        omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidPaperProjectedCells.kt \
        omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidSolidMarkDetector.kt \
        omr-core/src/test/java/com/answercard/grader/miniprogram/SolidMarkerCardScanTest.kt
git commit -m "feat(omr): dual-path anchors with perspective projection

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Nearest-center mark-to-cell matching

**Files:**
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidSolidMarkDetector.kt` (`matchQuestionCell`, `matchAdmissionNumberCell`, remove `RECT_TOLERANCE`)
- Test: add cases to `omr-core/src/test/java/com/answercard/grader/miniprogram/SolidMarkerCardScanTest.kt`

**Interfaces:**
- Consumes: `DesktopTemplateCardRenderer.markAnswerShifted` (Task 3), `TemplateGeometry` option constants.
- Produces: no API change; matching semantics become nearest-center with per-axis half-gap tolerance.

- [ ] **Step 1: Write the failing test (add to SolidMarkerCardScanTest)**

```kotlin
    @Test
    fun borderlineMarkGoesToNearestOptionCenterNotLeftNeighbor() {
        val renderer = DesktopTemplateCardRenderer(template(), scale = 3f, markerStyle = CornerMarkerStyle.SOLID_SQUARE)
        // Q11 option D shifted 9.5 template units LEFT of its box: center lands between C and D,
        // clearly nearer to D (distance 9.5) than to C (distance 12).
        renderer.markAnswerShifted(11, "D", dxUnits = -9.5f)
        renderer.markAdmissionNumber("1234")
        val result = AndroidOmrEngine.scan(renderer.frame(), template())

        assertTrue(result.debugInfo.joinToString(), result.success)
        assertEquals(listOf("D"), result.answerArea?.questions?.single { it.questionIndex == 10 }?.selectedLabels)
    }

    @Test
    fun markFarFromAnyCellIsIgnoredInsteadOfClaimedByNeighbor() {
        val renderer = DesktopTemplateCardRenderer(template(), scale = 3f, markerStyle = CornerMarkerStyle.SOLID_SQUARE)
        renderer.markAnswer(1, "A")
        renderer.markAdmissionNumber("1234")
        // A stray blob far right of Q11 D (well beyond half-gap tolerance) must not become an answer.
        renderer.markAnswerShifted(11, "D", dxUnits = 12f)
        val result = AndroidOmrEngine.scan(renderer.frame(), template())

        assertTrue(result.answerArea?.questions?.single { it.questionIndex == 10 }?.isBlank == true)
    }
```

Note: with the current `firstOrNull` + `RECT_TOLERANCE=4` logic the first test selects **C** (point falls inside C's expanded rect first), so it fails before the change.

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.SolidMarkerCardScanTest`
Expected: `borderlineMarkGoesToNearestOptionCenterNotLeftNeighbor` FAILS (selects "C").

- [ ] **Step 3: Implement nearest-center matching**

In `AndroidSolidMarkDetector.kt`, replace the constant `RECT_TOLERANCE` and both match functions plus the `Rect.contains` helper:

```kotlin
    private val OPTION_TOLERANCE_X = (TemplateGeometry.OPTION_STEP_X - TemplateGeometry.OPTION_BOX_W) / 2f
    private val OPTION_TOLERANCE_Y = (TemplateGeometry.QUESTION_ROW_STEP_Y - TemplateGeometry.OPTION_BOX_H) / 2f
    private val DIGIT_TOLERANCE_X = (TemplateGeometry.EXAM_DIGIT_STEP_X - TemplateGeometry.EXAM_DIGIT_BOX_W) / 2f
    private val DIGIT_TOLERANCE_Y = (TemplateGeometry.EXAM_ROW_STEP_Y - TemplateGeometry.EXAM_DIGIT_BOX_H) / 2f
```

```kotlin
    private fun matchQuestionCell(
        cardLayout: CardLayout,
        questionIndexByNumber: Map<Int, Int>,
        sourcePoint: TemplatePoint,
    ): CellMatch<AndroidPaperQuestionCellKey>? {
        val option = cardLayout.options
            .filter { candidate ->
                TemplateGeometry.renderedRect(candidate.rect)
                    .containsWithTolerance(sourcePoint, OPTION_TOLERANCE_X, OPTION_TOLERANCE_Y)
            }
            .minByOrNull { candidate -> TemplateGeometry.renderedRect(candidate.rect).centerDistance(sourcePoint) }
            ?: return null
        val questionIndex = questionIndexByNumber[option.question] ?: return null
        val optionIndex = cardLayout.options
            .filter { it.question == option.question }
            .indexOfFirst { it.option == option.option }
            .takeIf { it >= 0 } ?: return null
        return CellMatch(
            key = AndroidPaperQuestionCellKey(questionIndex, optionIndex),
            centerDistance = TemplateGeometry.renderedRect(option.rect).centerDistance(sourcePoint),
        )
    }

    private fun matchAdmissionNumberCell(
        cardLayout: CardLayout,
        admissionNumberDigits: Int,
        sourcePoint: TemplatePoint,
    ): CellMatch<AndroidPaperAdmissionNumberCellKey>? {
        if (cardLayout.examIdRows.isEmpty()) return null
        var best: CellMatch<AndroidPaperAdmissionNumberCellKey>? = null
        for (digitIndex in 0 until admissionNumberDigits) {
            for (numberValue in 0..9) {
                val rect = TemplateGeometry.renderedRect(
                    TemplateGeometry.examIdDigitBox(
                        layout = cardLayout,
                        column = digitIndex,
                        digit = numberValue,
                    ),
                )
                if (!rect.containsWithTolerance(sourcePoint, DIGIT_TOLERANCE_X, DIGIT_TOLERANCE_Y)) continue
                val distance = rect.centerDistance(sourcePoint)
                if (best == null || distance < best.centerDistance) {
                    best = CellMatch(
                        key = AndroidPaperAdmissionNumberCellKey(digitIndex, numberValue),
                        centerDistance = distance,
                    )
                }
            }
        }
        return best
    }

    private fun Rect.containsWithTolerance(point: TemplatePoint, toleranceX: Float, toleranceY: Float): Boolean =
        point.x >= x - toleranceX &&
            point.x <= x + w + toleranceX &&
            point.y >= y - toleranceY &&
            point.y <= y + h + toleranceY
```

Delete the old `Rect.contains` extension and `RECT_TOLERANCE`.

- [ ] **Step 4: Run all omr-core tests**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test`
Expected: BUILD SUCCESSFUL, including `DesktopWechatImageScanTest` — with perspective mapping + nearest-center, the WeChat photo must still read Q11=D and score 10.0.

- [ ] **Step 5: Commit**

```bash
git add omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidSolidMarkDetector.kt \
        omr-core/src/test/java/com/answercard/grader/miniprogram/SolidMarkerCardScanTest.kt
git commit -m "fix(omr): match solid marks to nearest cell center

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: Flip renderers to solid squares by default

**Files:**
- Modify: `omr-core/src/test/java/com/answercard/grader/miniprogram/DesktopTemplateCardRenderer.kt` (default `markerStyle = CornerMarkerStyle.SOLID_SQUARE`)
- Modify: `app/src/main/java/com/answercard/grader/template/TemplateRenderer.kt` (draw squares, lines 46 and 71-89)
- Modify: `omr-core/src/test/java/com/answercard/grader/miniprogram/DesktopTemplateCardRendererMarkerTest.kt` (`defaultModeStillDrawsLBrackets` becomes `defaultModeDrawsSolidSquares`)

**Interfaces:**
- Consumes: engine dual path (Task 5) — rendered cards now scan via the solid-marker path.
- Produces: all newly rendered/printed templates carry solid square markers.

- [ ] **Step 1: Update the renderer default and its test**

In `DesktopTemplateCardRenderer.kt`: `markerStyle: CornerMarkerStyle = CornerMarkerStyle.SOLID_SQUARE`.

In `DesktopTemplateCardRendererMarkerTest.kt`, replace `defaultModeStillDrawsLBrackets` with:

```kotlin
    @Test
    fun defaultModeDrawsSolidSquares() {
        val renderer = DesktopTemplateCardRenderer(template = TemplateState.default(), scale = 3f)
        val frame = renderer.frame()
        val scale = 3f
        val armRow = ((TemplateGeometry.CORNER_BRACKET_MARGIN + 4f) * scale).toInt()
        val armColumn = ((TemplateGeometry.CORNER_BRACKET_MARGIN + 30f) * scale).toInt()
        assertTrue("bracket arm area should be white by default", frame[armRow, armColumn] > 200)
    }
```

- [ ] **Step 2: App renderer draws squares**

In `TemplateRenderer.kt` line 46 replace `drawCornerBrackets(canvas, layout, fill)` with `drawCornerMarkers(canvas, layout, fill)`, and replace the `drawCornerBrackets` function (lines 71-89) with:

```kotlin
    private fun drawCornerMarkers(canvas: Canvas, layout: CardLayout, paint: Paint) {
        val rects = TemplateGeometry.cornerMarkerRects(layout)
        listOf(rects.lu, rects.ru, rects.ld, rects.rd).forEach { rect ->
            canvas.drawRect(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, paint)
        }
    }
```

- [ ] **Step 3: Run both suites**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test test`
Expected: omr-core green. App module: the 8 known-failing baseline tests may still fail (see Global Constraints) but **no new failures** — in particular `AndroidOmrImageScanTest.formalScanReadsRenderedProductionImageWithAnchorsAnswersAdmissionAndScore`, `...LightRenderedProductionImageNoise`, `RealImageScanTest`, and `TemplateRendererTest` must pass. If a previously passing app test now fails, compare its debugInfo (`anchorPath=` entry) and fix the solid path rather than the test.

- [ ] **Step 4: Commit**

```bash
git add omr-core/src/test/java/com/answercard/grader/miniprogram/DesktopTemplateCardRenderer.kt \
        omr-core/src/test/java/com/answercard/grader/miniprogram/DesktopTemplateCardRendererMarkerTest.kt \
        app/src/main/java/com/answercard/grader/template/TemplateRenderer.kt
git commit -m "feat(app): render solid corner markers on templates

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 8: ScanConsensusTracker

**Files:**
- Create: `omr-core/src/main/java/com/answercard/grader/miniprogram/ScanConsensusTracker.kt`
- Test: `omr-core/src/test/java/com/answercard/grader/miniprogram/ScanConsensusTrackerTest.kt`

**Interfaces:**
- Consumes: `AndroidOmrResult`.
- Produces:
  - `sealed interface ScanConsensusDecision` with `data class Pending(val streak: Int, val required: Int)`, `data class Locked(val signature: String, val result: AndroidOmrResult)`, `data class AlreadyLocked(val signature: String)`
  - `class ScanConsensusTracker(requiredFrames: Int = 3, signatureProvider: (AndroidOmrResult) -> String? = Companion::signatureOf)` with `fun offer(result: AndroidOmrResult): ScanConsensusDecision`, `fun reset()`, and `companion object { fun signatureOf(result: AndroidOmrResult): String? }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.answercard.grader.miniprogram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanConsensusTrackerTest {
    private fun result(
        success: Boolean = true,
        admission: String = "1234",
        answers: List<Pair<Int, List<String>>> = listOf(0 to listOf("A"), 1 to listOf("B")),
        totalScore: Double = 4.0,
    ): AndroidOmrResult {
        val questions = answers.map { (index, labels) ->
            AndroidQuestionReadResult(
                questionIndex = index,
                selectedOptions = labels.map { 0 },
                selectedLabels = labels,
                optionResults = emptyList(),
                isBlank = labels.isEmpty(),
                isMultiMarked = false,
            )
        }
        return AndroidOmrResult(
            success = success,
            failureReason = null,
            layout = null,
            anchors = null,
            grid = null,
            answerArea = AndroidAnswerAreaReadResult(questions = questions, failureReason = null, debugInfo = emptyList()),
            admissionNumber = AndroidAdmissionNumberReadResult(
                digits = admission,
                digitResults = emptyList(),
                success = true,
                failureReason = null,
                debugInfo = emptyList(),
            ),
            score = AndroidOmrScoreResult(
                totalScore = totalScore,
                maxScore = 10.0,
                items = emptyList(),
                success = true,
                warnings = emptyList(),
            ),
            warnings = emptyList(),
            debugInfo = emptyList(),
        )
    }

    @Test
    fun locksAfterThreeConsecutiveIdenticalFrames() {
        val tracker = ScanConsensusTracker()
        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Pending)
        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Pending)
        val third = tracker.offer(result())
        assertTrue(third is ScanConsensusDecision.Locked)
    }

    @Test
    fun failureFrameResetsStreak() {
        val tracker = ScanConsensusTracker()
        tracker.offer(result())
        tracker.offer(result())
        tracker.offer(result(success = false))
        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Pending)
        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Pending)
        assertTrue(tracker.offer(result()) is ScanConsensusDecision.Locked)
    }

    @Test
    fun alternatingSignaturesNeverLock() {
        val tracker = ScanConsensusTracker()
        repeat(5) {
            assertTrue(tracker.offer(result(totalScore = 4.0)) is ScanConsensusDecision.Pending)
            assertTrue(tracker.offer(result(totalScore = 6.0)) is ScanConsensusDecision.Pending)
        }
    }

    @Test
    fun sameSignatureAfterLockDoesNotRetrigger() {
        val tracker = ScanConsensusTracker()
        repeat(3) { tracker.offer(result()) }
        val next = tracker.offer(result())
        assertTrue(next is ScanConsensusDecision.AlreadyLocked)
    }

    @Test
    fun newCardLocksAgainAutomatically() {
        val tracker = ScanConsensusTracker()
        repeat(3) { tracker.offer(result(admission = "1234")) }
        repeat(2) { assertTrue(tracker.offer(result(admission = "5678")) is ScanConsensusDecision.Pending) }
        val locked = tracker.offer(result(admission = "5678"))
        assertTrue(locked is ScanConsensusDecision.Locked)
        assertTrue((locked as ScanConsensusDecision.Locked).signature.startsWith("5678|"))
    }

    @Test
    fun signatureIsNullForFailedOrScorelessResults() {
        assertNull(ScanConsensusTracker.signatureOf(result(success = false)))
        assertNull(ScanConsensusTracker.signatureOf(result().copy(score = null)))
    }

    @Test
    fun customSignatureProviderIsRespected() {
        val tracker = ScanConsensusTracker(signatureProvider = { r -> r.admissionNumber?.digits?.takeIf { it.isNotBlank() } })
        repeat(2) { tracker.offer(result()) }
        val third = tracker.offer(result())
        assertTrue(third is ScanConsensusDecision.Locked)
        assertEquals("1234", (third as ScanConsensusDecision.Locked).signature)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.ScanConsensusTrackerTest`
Expected: compilation FAILURE.

- [ ] **Step 3: Implement ScanConsensusTracker.kt**

```kotlin
package com.answercard.grader.miniprogram

sealed interface ScanConsensusDecision {
    data class Pending(val streak: Int, val required: Int) : ScanConsensusDecision
    data class Locked(val signature: String, val result: AndroidOmrResult) : ScanConsensusDecision
    data class AlreadyLocked(val signature: String) : ScanConsensusDecision
}

/**
 * Accepts a scan only after [requiredFrames] consecutive successful frames agree on the
 * same signature (admission number + per-question selections + score).
 */
class ScanConsensusTracker(
    private val requiredFrames: Int = 3,
    private val signatureProvider: (AndroidOmrResult) -> String? = Companion::signatureOf,
) {
    init {
        require(requiredFrames >= 1) { "requiredFrames must be at least 1" }
    }

    private var currentSignature: String? = null
    private var streak = 0
    private var lockedSignature: String? = null

    fun offer(result: AndroidOmrResult): ScanConsensusDecision {
        val signature = signatureProvider(result)
        if (signature == null) {
            currentSignature = null
            streak = 0
            return ScanConsensusDecision.Pending(streak = 0, required = requiredFrames)
        }
        if (signature == currentSignature) {
            streak += 1
        } else {
            currentSignature = signature
            streak = 1
        }
        if (streak < requiredFrames) {
            return ScanConsensusDecision.Pending(streak = streak, required = requiredFrames)
        }
        if (signature == lockedSignature) {
            return ScanConsensusDecision.AlreadyLocked(signature)
        }
        lockedSignature = signature
        return ScanConsensusDecision.Locked(signature = signature, result = result)
    }

    fun reset() {
        currentSignature = null
        streak = 0
        lockedSignature = null
    }

    companion object {
        fun signatureOf(result: AndroidOmrResult): String? {
            if (!result.success) return null
            val score = result.score ?: return null
            val answers = result.answerArea?.questions
                ?.sortedBy { it.questionIndex }
                ?.joinToString(";") { question ->
                    "${question.questionIndex}:${question.selectedLabels.joinToString(",")}"
                }
                ?: return null
            val admission = result.admissionNumber?.digits.orEmpty()
            return "$admission|$answers|${score.totalScore}/${score.maxScore}"
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.ScanConsensusTrackerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add omr-core/src/main/java/com/answercard/grader/miniprogram/ScanConsensusTracker.kt \
        omr-core/src/test/java/com/answercard/grader/miniprogram/ScanConsensusTrackerTest.kt
git commit -m "feat(omr): add multi-frame scan consensus tracker

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 9: Stability evaluator, monitor, and analyzer gate

**Files:**
- Create: `app/src/main/java/com/answercard/grader/camera/StabilityEvaluator.kt`
- Create: `app/src/main/java/com/answercard/grader/camera/DeviceStabilityMonitor.kt`
- Modify: `app/src/main/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzer.kt` (new `stabilityGate` param, drop path)
- Test: `app/src/test/java/com/answercard/grader/camera/StabilityEvaluatorTest.kt`
- Test: add gate test to `app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzerTest.kt`

**Interfaces:**
- Consumes: existing analyzer constructor and `FakeImageProxy`/`result(...)` helpers already defined in `AndroidOmrImageAnalyzerTest.kt`.
- Produces:
  - `class StabilityEvaluator(maxAngularSpeedRadPerSec: Float = 0.15f, requiredStableDurationMs: Long = 300L)` with `fun onSample(timestampMs: Long, angularSpeed: Float)`, `fun isStable(nowMs: Long): Boolean`
  - `class DeviceStabilityMonitor(context, evaluator, onStabilityChanged)` with `start()`, `stop()`, `isStable(nowMs: Long = System.currentTimeMillis()): Boolean`, `hasGyroscope: Boolean` (no gyroscope ⇒ always stable)
  - `AndroidOmrImageAnalyzer(..., stabilityGate: (() -> Boolean)? = null)`; unstable frames dropped with `droppedReason="unstable"`.

- [ ] **Step 1: Write the failing evaluator test**

```kotlin
package com.answercard.grader.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityEvaluatorTest {
    @Test
    fun unstableUntilQuietForRequiredDuration() {
        val evaluator = StabilityEvaluator(maxAngularSpeedRadPerSec = 0.15f, requiredStableDurationMs = 300L)
        evaluator.onSample(timestampMs = 0L, angularSpeed = 0.05f)
        assertFalse(evaluator.isStable(nowMs = 100L))
        assertTrue(evaluator.isStable(nowMs = 300L))
    }

    @Test
    fun shakeResetsTheQuietWindow() {
        val evaluator = StabilityEvaluator()
        evaluator.onSample(0L, 0.02f)
        evaluator.onSample(200L, 0.5f)
        assertFalse(evaluator.isStable(400L))
        evaluator.onSample(450L, 0.02f)
        assertFalse(evaluator.isStable(500L))
        assertTrue(evaluator.isStable(750L))
    }

    @Test
    fun neverStableWithoutAnySample()  {
        val evaluator = StabilityEvaluator()
        assertFalse(evaluator.isStable(10_000L))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.camera.StabilityEvaluatorTest`
Expected: compilation FAILURE.

- [ ] **Step 3: Implement StabilityEvaluator.kt**

```kotlin
package com.answercard.grader.camera

class StabilityEvaluator(
    private val maxAngularSpeedRadPerSec: Float = 0.15f,
    private val requiredStableDurationMs: Long = 300L,
) {
    private var stableSinceMs: Long? = null

    @Synchronized
    fun onSample(timestampMs: Long, angularSpeed: Float) {
        stableSinceMs = if (angularSpeed >= maxAngularSpeedRadPerSec) {
            null
        } else {
            stableSinceMs ?: timestampMs
        }
    }

    @Synchronized
    fun isStable(nowMs: Long): Boolean {
        val since = stableSinceMs ?: return false
        return nowMs - since >= requiredStableDurationMs
    }
}
```

- [ ] **Step 4: Implement DeviceStabilityMonitor.kt**

```kotlin
package com.answercard.grader.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class DeviceStabilityMonitor(
    context: Context,
    private val evaluator: StabilityEvaluator = StabilityEvaluator(),
    private val onStabilityChanged: (Boolean) -> Unit = {},
) : SensorEventListener {
    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var lastReported: Boolean? = null

    val hasGyroscope: Boolean get() = gyroscope != null

    fun start() {
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        if (gyroscope != null) sensorManager.unregisterListener(this)
    }

    fun isStable(nowMs: Long = System.currentTimeMillis()): Boolean =
        gyroscope == null || evaluator.isStable(nowMs)

    override fun onSensorChanged(event: SensorEvent) {
        val speed = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2],
        )
        evaluator.onSample(timestampMs = System.currentTimeMillis(), angularSpeed = speed)
        val stable = isStable()
        if (stable != lastReported) {
            lastReported = stable
            onStabilityChanged(stable)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
```

- [ ] **Step 5: Write the failing analyzer gate test (add to AndroidOmrImageAnalyzerTest)**

```kotlin
    @Test
    fun analyzeDropsFramesWhenStabilityGateReportsUnstable() {
        var resultCount = 0
        var processCount = 0
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = { resultCount++ },
            frameAdapter = { MiniProgramFrame(width = 1, height = 1, pixels = intArrayOf(255)) },
            processor = AndroidOmrFrameProcessor { _, _ ->
                processCount++
                result(success = true)
            },
            stabilityGate = { false },
        )
        val image = FakeImageProxy()

        analyzer.analyze(image)

        assertEquals(0, processCount)
        assertEquals(0, resultCount)
        assertTrue(image.closed)
    }

    @Test
    fun analyzeProcessesFramesWhenStabilityGateReportsStable() {
        var resultCount = 0
        val analyzer = AndroidOmrImageAnalyzer(
            templateProvider = { TemplateState.default() },
            onResult = { resultCount++ },
            frameAdapter = { MiniProgramFrame(width = 1, height = 1, pixels = intArrayOf(255)) },
            processor = AndroidOmrFrameProcessor { _, _ -> result(success = true) },
            stabilityGate = { true },
        )
        val image = FakeImageProxy()

        analyzer.analyze(image)

        assertEquals(1, resultCount)
        assertTrue(image.closed)
    }
```

Reuse the file's existing `FakeImageProxy` and `result(...)` helpers.

- [ ] **Step 6: Implement the analyzer gate**

In `AndroidOmrImageAnalyzer.kt`:
- Add constructor parameter after `frameAdapter`: `private val stabilityGate: (() -> Boolean)? = null,`
- Add counter beside the others: `private val unstableFrameCount = AtomicLong(0L)`
- At the top of `analyze` (before `tryEnterAnalysis`):

```kotlin
        if (stabilityGate?.invoke() == false) {
            droppedFrameCount.incrementAndGet()
            unstableFrameCount.incrementAndGet()
            lastDroppedReason.set("unstable")
            image.close()
            return
        }
```

- Add `"unstableFrameCount=${unstableFrameCount.get()}"` to `ImageProxy.debugInfo` next to `busyFrameCount`.

- [ ] **Step 7: Run app tests**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :app:testDebugUnitTest --tests "com.answercard.grader.camera.StabilityEvaluatorTest" --tests "com.answercard.grader.miniprogram.AndroidOmrImageAnalyzerTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/answercard/grader/camera/StabilityEvaluator.kt \
        app/src/main/java/com/answercard/grader/camera/DeviceStabilityMonitor.kt \
        app/src/main/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzer.kt \
        app/src/test/java/com/answercard/grader/camera/StabilityEvaluatorTest.kt \
        app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrImageAnalyzerTest.kt
git commit -m "feat(app): gate analysis frames on gyroscope stability

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 10: ScanScreen — consensus lock, big score display, TTS, stability toggle

**Files:**
- Modify: `app/src/main/java/com/answercard/grader/ui/ScanScreen.kt`

**Interfaces:**
- Consumes: `ScanConsensusTracker`/`ScanConsensusDecision` (Task 8), `DeviceStabilityMonitor` (Task 9), analyzer `stabilityGate` (Task 9), existing `ScoreSpeaker`, `ScoreSpeechText`, `ScanRecordStore`.
- Produces: user-visible behavior — score is recorded/announced once per card after 3 agreeing frames; big locked-score overlay; "Steady: On/Off" toggle; "Hold steady" status while shaking.

- [ ] **Step 1: Add imports and state**

Add imports:

```kotlin
import com.answercard.grader.camera.DeviceStabilityMonitor
import com.answercard.grader.miniprogram.ScanConsensusDecision
import com.answercard.grader.miniprogram.ScanConsensusTracker
```

After `var displayResult ...` (line 79) add:

```kotlin
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
```

- [ ] **Step 2: Replace the onResult accept logic**

Replace the body of `onResult = { result -> mainHandler.post { ... } }` (lines 93-125) with:

```kotlin
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
```

Note: `lastHandledKey` stays — it is still used by the legacy `OmrScanner` branch below.

- [ ] **Step 3: Wire the analyzer gate**

In the `AndroidOmrImageAnalyzer(...)` construction, after the `options = ...` argument add:

```kotlin
            stabilityGate = { !currentStabilityGateEnabled.value || stabilityMonitor.isStable() },
```

- [ ] **Step 4: Add the Steady toggle button**

In the top-bar inner `Row` (next to the Sound button, line 175-177), add before the Sound button:

```kotlin
                OutlinedButton(onClick = { stabilityGateEnabled = !stabilityGateEnabled }) {
                    Text(if (stabilityGateEnabled) "Steady: On" else "Steady: Off")
                }
```

- [ ] **Step 5: Locked-score overlay and hold-steady hint**

Inside the camera `Box` (after the `if (hasCameraPermission) { ... } else { ... }` block, before `ScanStatusPanel`), add:

```kotlin
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
```

- [ ] **Step 6: Build and run the full app test suite**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew assembleDebug test`
Expected: `assembleDebug` BUILD SUCCESSFUL; test failures limited to the 8 known-failing baseline tests.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/answercard/grader/ui/ScanScreen.kt
git commit -m "feat(app): lock score via 3-frame consensus with TTS announce

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 11: Stability regression test + docs update

**Files:**
- Create: `omr-core/src/test/java/com/answercard/grader/miniprogram/SolidMarkerCardStabilityTest.kt`
- Modify: `docs/omr-stability-optimizations.md` (append verification results)

**Interfaces:**
- Consumes: everything above; `TestPerspectiveWarp` from Task 5's test file.
- Produces: a regression test proving frame-to-frame score stability on square cards under camera-like noise.

- [ ] **Step 1: Write the stability regression test**

```kotlin
package com.answercard.grader.miniprogram

import com.answercard.grader.template.CornerMarkerStyle
import com.answercard.grader.template.QuestionSetting
import com.answercard.grader.template.TemplateState
import java.util.Random
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for camera-frame score stability: the same square-marker card,
 * downscaled to analysis resolution with blur and per-frame sensor noise,
 * must yield the same correct score on every frame that succeeds — and most
 * frames must succeed.
 */
class SolidMarkerCardStabilityTest {
    @Test
    fun noisyBlurredFramesAgreeOnTheCorrectScore() {
        val template = TemplateState(
            name = "stability",
            questions = (1..16).map { number ->
                val answer = when (number) {
                    1 -> "A"
                    2 -> "B"
                    6 -> "C"
                    11 -> "D"
                    16 -> "A"
                    else -> "A"
                }
                QuestionSetting(number = number, answer = answer, score = if (number in listOf(1, 2, 6, 11, 16)) 2 else 0)
            },
        )
        val renderer = DesktopTemplateCardRenderer(template, scale = 3f, markerStyle = CornerMarkerStyle.SOLID_SQUARE)
        renderer.markAnswer(1, "A")
        renderer.markAnswer(2, "B")
        renderer.markAnswer(6, "C")
        renderer.markAnswer(11, "D")
        renderer.markAnswer(16, "A")
        renderer.markAdmissionNumber("1234")
        val base = boxBlur(downscaleToWidth(renderer.frame(), 1280), radius = 1)

        var successes = 0
        for (seed in 1..8) {
            val frame = gaussianNoise(base, sigma = 3.0, seed = seed.toLong())
            val result = AndroidOmrEngine.scan(frame, template)
            if (!result.success) continue
            successes += 1
            assertEquals("seed $seed admission", "1234", result.admissionNumber?.digits)
            assertEquals("seed $seed score", 10.0, result.score?.totalScore ?: -1.0, 0.0)
        }
        assertTrue("at least 6 of 8 noisy frames should scan, got $successes", successes >= 6)
    }

    private fun downscaleToWidth(frame: MiniProgramFrame, targetWidth: Int): MiniProgramFrame {
        val scale = frame.width.toDouble() / targetWidth
        val targetHeight = (frame.height / scale).roundToInt()
        val pixels = IntArray(targetWidth * targetHeight)
        for (row in 0 until targetHeight) {
            val rowStart = (row * scale).toInt()
            val rowEnd = (((row + 1) * scale).toInt()).coerceAtMost(frame.height).coerceAtLeast(rowStart + 1)
            for (column in 0 until targetWidth) {
                val columnStart = (column * scale).toInt()
                val columnEnd = (((column + 1) * scale).toInt()).coerceAtMost(frame.width).coerceAtLeast(columnStart + 1)
                var sum = 0L
                var count = 0
                for (r in rowStart until rowEnd) {
                    for (c in columnStart until columnEnd) {
                        sum += frame.pixels[r * frame.width + c]
                        count += 1
                    }
                }
                pixels[row * targetWidth + column] = (sum / count).toInt()
            }
        }
        return MiniProgramFrame(width = targetWidth, height = targetHeight, pixels = pixels)
    }

    private fun boxBlur(frame: MiniProgramFrame, radius: Int): MiniProgramFrame {
        val pixels = IntArray(frame.pixels.size)
        for (row in 0 until frame.height) {
            for (column in 0 until frame.width) {
                var sum = 0
                var count = 0
                for (dr in -radius..radius) {
                    for (dc in -radius..radius) {
                        val r = row + dr
                        val c = column + dc
                        if (r in 0 until frame.height && c in 0 until frame.width) {
                            sum += frame.pixels[r * frame.width + c]
                            count += 1
                        }
                    }
                }
                pixels[row * frame.width + column] = sum / count
            }
        }
        return MiniProgramFrame(width = frame.width, height = frame.height, pixels = pixels)
    }

    private fun gaussianNoise(frame: MiniProgramFrame, sigma: Double, seed: Long): MiniProgramFrame {
        val random = Random(seed)
        return MiniProgramFrame(
            width = frame.width,
            height = frame.height,
            pixels = IntArray(frame.pixels.size) { index ->
                (frame.pixels[index] + random.nextGaussian() * sigma).roundToInt().coerceIn(0, 255)
            },
        )
    }
}
```

- [ ] **Step 2: Run it**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.SolidMarkerCardStabilityTest`
Expected: PASS. If any seed produces a wrong-but-successful score, that is a real defect in the new pipeline — debug via the seed's debugInfo (`solidMarkReference`, `solidQuestionMarks`) before touching thresholds.

- [ ] **Step 3: Re-run the diagnostic probe and append results to the design doc**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew :omr-core:test --tests com.answercard.grader.miniprogram.DesktopCameraInstabilityProbeTest`
Then read `omr-core/build/reports/camera-instability-probe.txt` and append to `docs/omr-stability-optimizations.md`:

```markdown
## 验证结果（实施后）

- L 形微信照片探针（legacy 回退路径 + 透视映射 + 最近中心匹配）：ds1280 基线分数与全分辨率一致
  （之前 Q11 D→C 翻转已消除）；blur1+noise3 各种子分数一致或失败可见，无沉默错分。
- 方块卡稳定性回归（`SolidMarkerCardStabilityTest`）：1280 宽 + blur1 + noise3 × 8 种子,
  成功帧全部 10/10 且学号一致。
- 已知基线失败（与本次改动无关，改动前后均失败）：`AndroidOmrImageScanTest` 4 例、
  `CornerAnchorMatcherTest` 4 例。
```

Adjust the first bullet to match the actual probe output before committing.

- [ ] **Step 4: Full verification**

Run: `JAVA_HOME=/usr/lib/jvm/java-21 sh gradlew test`
Expected: only the 8 known-failing baseline tests fail; everything else green.

- [ ] **Step 5: Commit**

```bash
git add omr-core/src/test/java/com/answercard/grader/miniprogram/SolidMarkerCardStabilityTest.kt \
        docs/omr-stability-optimizations.md
git commit -m "test(omr): add square-card frame-stability regression

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
