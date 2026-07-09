# OMR Read Parity A+B Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix OMR read semantics and bubble anti-contamination behavior so multiple-choice marks, solid-mark fusion, edge cleaning, and 3x3 solidity evidence match the approved A+B design.

**Architecture:** Keep recognition math in `omr-core`; app UI only exposes already-modeled multiple-choice state. Add small focused helpers instead of expanding geometry code. Tests lead every behavior change, and each task produces a passing, independently reviewable slice.

**Tech Stack:** Kotlin, JUnit 4, Gradle, Android-free `omr-core` logic, Jetpack Compose for template editing UI.

## Global Constraints

- Use `/home/jjr/pyenvs/vllm-py312/.venv/bin/python` as global venv when Python is needed.
- Run commands from `/data/Sources/android-omr`.
- Keep `omr-core` free of Android, AndroidX, Compose, CameraX, and `android.graphics` imports.
- Use JDK 21 for Gradle: `JAVA_HOME=/usr/lib/jvm/java-21`.
- Do not implement line snapping, banded homography, arc extrapolation, corner direction disambiguation, or 5 mini-program question types in this plan.
- Do not remove L-bracket fallback or rewrite template geometry.
- Preserve existing solid marker, perspective mapping, consensus, and blank admission number behavior unless a task explicitly updates tests for the approved A+B semantics.

---

## File Structure

- Modify `omr-core/src/main/java/com/answercard/grader/miniprogram/MiniProgramBubbleReadResult.kt`: add read metrics needed by scoring and debugging.
- Modify `omr-core/src/main/java/com/answercard/grader/miniprogram/MiniProgramBubbleReader.kt`: compute central gray mean, 3x3 `containCount`, solidity bounds, and marked decision.
- Create `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidPaperEdgeCleanDirections.kt`: map answer/admission cell positions to edge-clean directions.
- Modify `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidAnswerAreaReader.kt`: pass edge clean directions, accept question types, fuse solid marks by union, keep multiple-choice selections, and downgrade per-cell bubble failures.
- Modify `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidAdmissionNumberReader.kt`: pass edge clean directions, fuse solid marks by union, and downgrade candidate bubble failures.
- Modify `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidOmrEngine.kt`: pass template question types into answer reading.
- Modify `omr-core/src/main/java/com/answercard/grader/template/TemplateState.kt`: preserve multiple-choice type and normalize multi-answer strings.
- Modify `app/src/main/java/com/answercard/grader/template/TemplateJson.kt`: deserialize multi-answer strings correctly.
- Modify `app/src/main/java/com/answercard/grader/ui/TemplateEditSheet.kt`: expose the existing `QuestionType.MULTIPLE` model in add/edit/batch dialogs and answer pills.
- Modify tests under `app/src/test/java/...` and `omr-core/src/test/java/...` listed per task.

---

### Task 1: Bubble Solidity Evidence

**Files:**
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/MiniProgramBubbleReadResult.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/MiniProgramBubbleReader.kt`
- Test: `app/src/test/java/com/answercard/grader/miniprogram/MiniProgramBubbleReaderTest.kt`

**Interfaces:**
- Produces: `MiniProgramBubbleReadResult.centralMeanGray: Double`
- Produces: `MiniProgramBubbleReadResult.solidBoundsWidth: Int`
- Produces: `MiniProgramBubbleReadResult.solidBoundsHeight: Int`
- Produces: `MiniProgramBubbleReader.read(...)` where `containCount` is true 3x3 solidity evidence.

- [ ] **Step 1: Write failing bubble solidity tests**

Add these tests to `MiniProgramBubbleReaderTest`:

```kotlin
@Test
fun narrowLineDoesNotMarkCellEvenWhenCentralAreaHasBlackPixels() {
    val frame = frame(width = 80, height = 80, value = 230)
    fillRect(frame, row = 10, column = 20, height = 24, width = 3, value = 20)

    val result = MiniProgramBubbleReader.read(frame, cell(row = 10, column = 10, size = 24))

    assertFalse(result.isMarked)
    assertTrue(result.centralBlackCount > 0)
    assertTrue(result.solidBoundsWidth < 6)
    assertTrue(result.centralMeanGray < 230.0)
}

@Test
fun solidBlockReportsContainCountAndBounds() {
    val frame = frame(width = 80, height = 80, value = 230)
    fillRect(frame, row = 16, column = 16, height = 12, width = 12, value = 20)

    val result = MiniProgramBubbleReader.read(frame, cell(row = 10, column = 10, size = 24))

    assertTrue(result.isMarked)
    assertTrue(result.containCount >= 4)
    assertTrue(result.solidBoundsWidth >= 6)
    assertTrue(result.solidBoundsHeight >= 6)
    assertTrue(result.centralMeanGray < 230.0)
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21 GRADLE_USER_HOME=/tmp/codex-gradle sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.miniprogram.MiniProgramBubbleReaderTest
```

Expected: FAIL with unresolved references for `centralMeanGray`, `solidBoundsWidth`, or `solidBoundsHeight`, and the narrow line still marked under old central black ratio behavior.

- [ ] **Step 3: Extend `MiniProgramBubbleReadResult`**

Update `MiniProgramBubbleReadResult`:

```kotlin
data class MiniProgramBubbleReadResult(
    val isMarked: Boolean,
    val containCount: Int,
    val blackThreshold: Int,
    val totalBlackCount: Int,
    val centralBlackCount: Int,
    val cleanedTotalBlackCount: Int,
    val centralMeanGray: Double,
    val solidBoundsWidth: Int,
    val solidBoundsHeight: Int,
    val noiseComponentsRemoved: Int,
    val noisePixelsRemoved: Int,
    val componentsKept: Int,
    val largestComponentArea: Int,
    val sampleRows: Int,
    val sampleColumns: Int,
    val edgeCleanDirections: Set<MiniProgramEdgeCleanDirection>,
    val failureReason: String?,
    val debugMatrix: List<String> = emptyList(),
)
```

Update the failure result in `MiniProgramBubbleReader.failure(...)` with:

```kotlin
centralMeanGray = 255.0,
solidBoundsWidth = 0,
solidBoundsHeight = 0,
```

- [ ] **Step 4: Implement 3x3 solidity in `MiniProgramBubbleReader`**

Replace the old central-ratio decision block in `read(...)` with this shape:

```kotlin
val totalBlackCount = binary.count { it == 0 }
val cleanedTotalBlackCount = cleanedBinary.count { it == 0 }
val centralBlackCount = centralBlackCount(cleanedBinary, sampleRows, sampleColumns)
val centralArea = centralArea(sampleRows, sampleColumns)
val centralMeanGray = centralMeanGray(grayValues, sampleRows, sampleColumns)
val solidity = solidityEvidence(cleanedBinary, sampleRows, sampleColumns)
val minContainCount = maxOf(4, (centralArea * MIN_CONTAIN_RATIO).toInt())
val minSolidWidth = (sampleColumns * MIN_SOLID_BOUNDS_RATIO).toInt().coerceAtLeast(1)
val minSolidHeight = (sampleRows * MIN_SOLID_BOUNDS_RATIO).toInt().coerceAtLeast(1)
val isMarked = solidity.containCount >= minContainCount &&
    solidity.boundsWidth >= minSolidWidth &&
    solidity.boundsHeight >= minSolidHeight
```

Add constants near the top:

```kotlin
private const val MIN_SOLID_NEIGHBOR_BLACK_COUNT = 8
private const val MIN_CONTAIN_RATIO = 0.05
private const val MIN_SOLID_BOUNDS_RATIO = 0.25
```

Add helper types and functions:

```kotlin
private data class SolidityEvidence(
    val containCount: Int,
    val boundsWidth: Int,
    val boundsHeight: Int,
)

private fun centralMeanGray(grayValues: IntArray, sampleRows: Int, sampleColumns: Int): Double {
    val rowStart = sampleRows / 4
    val rowEnd = sampleRows - rowStart
    val columnStart = sampleColumns / 4
    val columnEnd = sampleColumns - columnStart
    var sum = 0L
    var count = 0
    for (row in rowStart until rowEnd) {
        for (column in columnStart until columnEnd) {
            sum += grayValues[row * sampleColumns + column]
            count += 1
        }
    }
    return if (count == 0) 255.0 else sum.toDouble() / count.toDouble()
}

private fun solidityEvidence(binary: IntArray, sampleRows: Int, sampleColumns: Int): SolidityEvidence {
    val rowStart = maxOf(1, sampleRows / 4)
    val rowEnd = minOf(sampleRows - 1, sampleRows - sampleRows / 4)
    val columnStart = maxOf(1, sampleColumns / 4)
    val columnEnd = minOf(sampleColumns - 1, sampleColumns - sampleColumns / 4)
    var count = 0
    var minRow = Int.MAX_VALUE
    var maxRow = Int.MIN_VALUE
    var minColumn = Int.MAX_VALUE
    var maxColumn = Int.MIN_VALUE
    for (row in rowStart until rowEnd) {
        for (column in columnStart until columnEnd) {
            if (blackNeighbors(binary, sampleRows, sampleColumns, row, column) >= MIN_SOLID_NEIGHBOR_BLACK_COUNT) {
                count += 1
                minRow = minOf(minRow, row)
                maxRow = maxOf(maxRow, row)
                minColumn = minOf(minColumn, column)
                maxColumn = maxOf(maxColumn, column)
            }
        }
    }
    val width = if (count == 0) 0 else maxColumn - minColumn + 1
    val height = if (count == 0) 0 else maxRow - minRow + 1
    return SolidityEvidence(containCount = count, boundsWidth = width, boundsHeight = height)
}

private fun blackNeighbors(
    binary: IntArray,
    sampleRows: Int,
    sampleColumns: Int,
    row: Int,
    column: Int,
): Int {
    var count = 0
    for (r in row - 1..row + 1) {
        for (c in column - 1..column + 1) {
            if (r in 0 until sampleRows && c in 0 until sampleColumns && binary[r * sampleColumns + c] == 0) {
                count += 1
            }
        }
    }
    return count
}
```

Set result fields:

```kotlin
containCount = solidity.containCount,
centralMeanGray = centralMeanGray,
solidBoundsWidth = solidity.boundsWidth,
solidBoundsHeight = solidity.boundsHeight,
```

- [ ] **Step 5: Run task tests**

Run:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21 GRADLE_USER_HOME=/tmp/codex-gradle sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.miniprogram.MiniProgramBubbleReaderTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add omr-core/src/main/java/com/answercard/grader/miniprogram/MiniProgramBubbleReadResult.kt \
        omr-core/src/main/java/com/answercard/grader/miniprogram/MiniProgramBubbleReader.kt \
        app/src/test/java/com/answercard/grader/miniprogram/MiniProgramBubbleReaderTest.kt
git commit -m "fix(omr): use solidity evidence for bubble marks"
```

---

### Task 2: Edge Clean Direction Routing

**Files:**
- Create: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidPaperEdgeCleanDirections.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidAnswerAreaReader.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidAdmissionNumberReader.kt`
- Test: `app/src/test/java/com/answercard/grader/miniprogram/AndroidAnswerAreaReaderTest.kt`
- Test: `app/src/test/java/com/answercard/grader/miniprogram/AndroidAdmissionNumberReaderTest.kt`

**Interfaces:**
- Produces: `AndroidPaperEdgeCleanDirections.forQuestion(mapping: AndroidPaperQuestionMapping, layout: AndroidPaperTemplateLayout): Set<MiniProgramEdgeCleanDirection>`
- Produces: `AndroidPaperEdgeCleanDirections.forAdmission(mapping: AndroidPaperAdmissionNumberMapping, layout: AndroidPaperTemplateLayout): Set<MiniProgramEdgeCleanDirection>`
- Consumes: `MiniProgramBubbleReader.read(..., edgeCleanDirections = directions)`

- [ ] **Step 1: Write failing edge-routing tests**

In `AndroidAnswerAreaReaderTest`, add:

```kotlin
@Test
fun passesEdgeCleanDirectionsForAnswerAreaBoundaries() {
    val fixture = Fixture(questionCount = 5)

    val result = AndroidAnswerAreaReader.read(fixture.frame(), fixture.grid, fixture.layout)

    val firstOption = result.questions.single { it.questionIndex == 0 }.optionResults.single { it.optionIndex == 0 }
    val lastOption = result.questions.single { it.questionIndex == 0 }.optionResults.single { it.optionIndex == 3 }
    assertTrue(firstOption.readResult.edgeCleanDirections.contains(MiniProgramEdgeCleanDirection.LEFT))
    assertTrue(firstOption.readResult.edgeCleanDirections.contains(MiniProgramEdgeCleanDirection.UP))
    assertTrue(lastOption.readResult.edgeCleanDirections.contains(MiniProgramEdgeCleanDirection.RIGHT))
}
```

In `AndroidAdmissionNumberReaderTest`, add:

```kotlin
@Test
fun passesEdgeCleanDirectionsForAdmissionNumberBoundaries() {
    val fixture = Fixture()

    val result = AndroidAdmissionNumberReader.read(fixture.frame(), fixture.grid, fixture.layout)

    val zero = result.digitResults.first().candidates.single { it.numberValue == 0 }
    val nine = result.digitResults.first().candidates.single { it.numberValue == 9 }
    assertTrue(zero.readResult.edgeCleanDirections.contains(MiniProgramEdgeCleanDirection.LEFT))
    assertTrue(zero.readResult.edgeCleanDirections.contains(MiniProgramEdgeCleanDirection.UP))
    assertTrue(zero.readResult.edgeCleanDirections.contains(MiniProgramEdgeCleanDirection.DOWN))
    assertTrue(nine.readResult.edgeCleanDirections.contains(MiniProgramEdgeCleanDirection.RIGHT))
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21 GRADLE_USER_HOME=/tmp/codex-gradle sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.miniprogram.AndroidAnswerAreaReaderTest --tests com.answercard.grader.miniprogram.AndroidAdmissionNumberReaderTest
```

Expected: FAIL because all production reads currently pass empty edge-clean direction sets.

- [ ] **Step 3: Create edge clean helper**

Create `AndroidPaperEdgeCleanDirections.kt`:

```kotlin
package com.answercard.grader.miniprogram

object AndroidPaperEdgeCleanDirections {
    fun forQuestion(
        mapping: AndroidPaperQuestionMapping,
        layout: AndroidPaperTemplateLayout,
    ): Set<MiniProgramEdgeCleanDirection> {
        val questionMappings = layout.questionMappings.filter { it.questionIndex == mapping.questionIndex }
        val columns = questionMappings.map { it.column }
        val directions = mutableSetOf<MiniProgramEdgeCleanDirection>()
        if (columns.isNotEmpty() && mapping.column == columns.minOrNull()) {
            directions += MiniProgramEdgeCleanDirection.LEFT
        }
        if (columns.isNotEmpty() && mapping.column == columns.maxOrNull()) {
            directions += MiniProgramEdgeCleanDirection.RIGHT
        }
        if (mapping.row == layout.answerArea.startRow) {
            directions += MiniProgramEdgeCleanDirection.UP
        }
        if (mapping.row == layout.answerArea.endRow - 1) {
            directions += MiniProgramEdgeCleanDirection.DOWN
        }
        return directions
    }

    fun forAdmission(
        mapping: AndroidPaperAdmissionNumberMapping,
        layout: AndroidPaperTemplateLayout,
    ): Set<MiniProgramEdgeCleanDirection> {
        val directions = mutableSetOf<MiniProgramEdgeCleanDirection>()
        if (mapping.numberValue == layout.admissionNumberArea.startColumn) {
            directions += MiniProgramEdgeCleanDirection.LEFT
        }
        if (mapping.numberValue == layout.admissionNumberArea.endColumn - 1) {
            directions += MiniProgramEdgeCleanDirection.RIGHT
        }
        directions += MiniProgramEdgeCleanDirection.UP
        directions += MiniProgramEdgeCleanDirection.DOWN
        return directions
    }
}
```

- [ ] **Step 4: Route directions in readers**

In `AndroidAnswerAreaReader`, replace:

```kotlin
val readResult = MiniProgramBubbleReader.read(frame = frame, cell = cell)
```

with:

```kotlin
val edgeCleanDirections = AndroidPaperEdgeCleanDirections.forQuestion(mapping, layout)
val readResult = MiniProgramBubbleReader.read(
    frame = frame,
    cell = cell,
    edgeCleanDirections = edgeCleanDirections,
)
```

Change debug setup from:

```kotlin
debugInfo += "edgeCleanDirections=none"
```

to:

```kotlin
debugInfo += "edgeCleanDirections=active"
```

In `AndroidAdmissionNumberReader`, replace:

```kotlin
val readResult = MiniProgramBubbleReader.read(frame = frame, cell = cell)
```

with:

```kotlin
val edgeCleanDirections = AndroidPaperEdgeCleanDirections.forAdmission(mapping, layout)
val readResult = MiniProgramBubbleReader.read(
    frame = frame,
    cell = cell,
    edgeCleanDirections = edgeCleanDirections,
)
```

Change admission debug setup to `edgeCleanDirections=active`.

- [ ] **Step 5: Run task tests**

Run:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21 GRADLE_USER_HOME=/tmp/codex-gradle sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.miniprogram.AndroidAnswerAreaReaderTest --tests com.answercard.grader.miniprogram.AndroidAdmissionNumberReaderTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidPaperEdgeCleanDirections.kt \
        omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidAnswerAreaReader.kt \
        omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidAdmissionNumberReader.kt \
        app/src/test/java/com/answercard/grader/miniprogram/AndroidAnswerAreaReaderTest.kt \
        app/src/test/java/com/answercard/grader/miniprogram/AndroidAdmissionNumberReaderTest.kt
git commit -m "fix(omr): route edge cleaning for paper cells"
```

---

### Task 3: Answer Reader Semantics and Solid Union

**Files:**
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidAnswerAreaReader.kt`
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidOmrEngine.kt`
- Test: `app/src/test/java/com/answercard/grader/miniprogram/AndroidAnswerAreaReaderTest.kt`

**Interfaces:**
- Consumes: `MiniProgramBubbleReadResult.centralMeanGray`
- Consumes: `AndroidPaperEdgeCleanDirections.forQuestion(...)`
- Produces: `AndroidAnswerAreaReader.read(..., questionTypesByQuestion: List<QuestionType> = emptyList(), ...)`
- Produces: solid union behavior where `effectiveMarked = bubbleMarked || solidMarked`.

- [ ] **Step 1: Write failing answer-reader tests**

Add imports:

```kotlin
import com.answercard.grader.template.QuestionType
```

Replace the old `keepsMultipleMarkedOptionsWithoutScoring` expectation with:

```kotlin
@Test
fun keepsMultipleMarkedOptionsForMultipleChoiceQuestion() {
    val fixture = Fixture(questionCount = 1)
    fixture.mark(questionIndex = 0, optionIndex = 0)
    fixture.mark(questionIndex = 0, optionIndex = 2)

    val result = AndroidAnswerAreaReader.read(
        frame = fixture.frame(),
        grid = fixture.grid,
        layout = fixture.layout,
        questionTypesByQuestion = listOf(QuestionType.MULTIPLE),
    )

    val question = result.questions.single { it.questionIndex == 0 }
    assertEquals(listOf(0, 2), question.selectedOptions)
    assertEquals(listOf("A", "C"), question.selectedLabels)
    assertFalse(question.isBlank)
    assertTrue(question.isMultiMarked)
}
```

Add solid union test using projected cells:

```kotlin
@Test
fun solidMarksAreUnionedWithBubbleMarksInsteadOfReplacingThem() {
    val fixture = Fixture(questionCount = 1)
    fixture.mark(questionIndex = 0, optionIndex = 0)
    val projectedCells = AndroidPaperProjectedCells(
        questionCells = fixture.layout.questionMappings.associate { mapping ->
            AndroidPaperQuestionCellKey(mapping.questionIndex, mapping.optionIndex) to
                fixture.grid.cell(mapping.row, mapping.column)
        },
        admissionNumberCells = emptyMap(),
        debugInfo = emptyList(),
    )
    val solidMarks = AndroidSolidMarkOverlay(
        questionCells = setOf(AndroidPaperQuestionCellKey(questionIndex = 0, optionIndex = 2)),
        admissionNumberCells = emptySet(),
        debugInfo = listOf("solid=test"),
    )

    val result = AndroidAnswerAreaReader.read(
        frame = fixture.frame(),
        layout = fixture.layout,
        projectedCells = projectedCells,
        optionLabelsByQuestion = listOf(listOf("A", "B", "C", "D")),
        questionTypesByQuestion = listOf(QuestionType.MULTIPLE),
        solidMarks = solidMarks,
    )

    val question = result.questions.single()
    assertEquals(listOf(0, 2), question.selectedOptions)
    assertTrue(result.debugInfo.contains("solidFusion=union"))
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21 GRADLE_USER_HOME=/tmp/codex-gradle sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.miniprogram.AndroidAnswerAreaReaderTest
```

Expected: FAIL because `questionTypesByQuestion` does not exist, multiple-choice still collapses, and solid marks replace bubble marks.

- [ ] **Step 3: Add question type parameters**

In `AndroidAnswerAreaReader`, import:

```kotlin
import com.answercard.grader.template.QuestionType
```

Add `questionTypesByQuestion` to both public `read(...)` overloads:

```kotlin
questionTypesByQuestion: List<QuestionType> = emptyList(),
```

Pass it into the private `read(...)`. Add this private parameter:

```kotlin
questionTypeResolver: (AndroidPaperQuestionMapping) -> QuestionType,
```

For public overloads, pass:

```kotlin
questionTypeResolver = { mapping ->
    questionTypesByQuestion.getOrNull(mapping.questionIndex) ?: QuestionType.SINGLE
},
```

- [ ] **Step 4: Implement solid union**

Replace:

```kotlin
val effectiveReadResult = solidMarkResolver?.let { resolver ->
    readResult.copy(isMarked = resolver(mapping))
} ?: readResult
```

with:

```kotlin
val solidMarked = solidMarkResolver?.invoke(mapping) == true
val bubbleMarked = readResult.isMarked
if (solidMarked && bubbleMarked) bothMarks += 1
if (solidMarked && !bubbleMarked) solidOnlyMarks += 1
if (!solidMarked && bubbleMarked) bubbleOnlyMarks += 1
val effectiveReadResult = readResult.copy(isMarked = bubbleMarked || solidMarked)
```

Declare counters before the mapping loop:

```kotlin
var solidOnlyMarks = 0
var bubbleOnlyMarks = 0
var bothMarks = 0
```

After building questions, add debug entries:

```kotlin
debugInfo += "solidFusion=union"
debugInfo += "solidOnlyMarks=$solidOnlyMarks"
debugInfo += "bubbleOnlyMarks=$bubbleOnlyMarks"
debugInfo += "bothMarks=$bothMarks"
```

- [ ] **Step 5: Implement multiple-choice preservation and single-choice strength**

Add constants and helpers inside `AndroidAnswerAreaReader`:

```kotlin
private const val SINGLE_CHOICE_KEEP_RATIO = 0.84

private fun selectedOptionsForQuestion(
    questionType: QuestionType,
    marked: List<AndroidOptionReadResult>,
): List<AndroidOptionReadResult> {
    if (questionType == QuestionType.MULTIPLE || marked.size <= 1) {
        return marked.sortedBy { it.optionIndex }
    }
    val best = marked.maxWithOrNull(
        compareBy<AndroidOptionReadResult> { markStrength(it.readResult) }
            .thenBy { it.readResult.containCount }
            .thenBy { it.readResult.cleanedTotalBlackCount }
            .thenBy { it.readResult.totalBlackCount },
    ) ?: return emptyList()
    return listOf(best)
}

private fun markStrength(readResult: MiniProgramBubbleReadResult): Double =
    (readResult.blackThreshold.toDouble() - readResult.centralMeanGray).coerceAtLeast(0.0)

private fun isAmbiguousSingleChoice(
    questionType: QuestionType,
    marked: List<AndroidOptionReadResult>,
): Boolean {
    if (questionType != QuestionType.SINGLE || marked.size <= 1) return false
    val bestStrength = marked.maxOf { markStrength(it.readResult) }
    if (bestStrength <= 0.0) return marked.size > 1
    return marked.count { markStrength(it.readResult) >= bestStrength * SINGLE_CHOICE_KEEP_RATIO } > 1
}
```

In the question grouping block, replace the unconditional collapse with:

```kotlin
val questionType = questionTypeResolver(options.first().mapping())
val selected = selectedOptionsForQuestion(questionType, marked)
if (isAmbiguousSingleChoice(questionType, marked)) ambiguousSingleChoiceCount += 1
```

Because `AndroidOptionReadResult` does not currently expose its original mapping, use its existing fields:

```kotlin
private fun AndroidOptionReadResult.mapping(): AndroidPaperQuestionMapping =
    AndroidPaperQuestionMapping(
        questionIndex = questionIndex,
        optionIndex = optionIndex,
        row = row,
        column = column,
    )
```

Declare `var ambiguousSingleChoiceCount = 0` before grouping and add:

```kotlin
debugInfo += "singleChoiceAmbiguous=$ambiguousSingleChoiceCount"
```

- [ ] **Step 6: Downgrade per-cell bubble read failures**

Replace the `return failure("bubble read failed: ...")` branch with:

```kotlin
if (readResult.failureReason != null) {
    optionReadFailures += 1
    debugInfo += "optionReadFailure=questionIndex=${mapping.questionIndex},optionIndex=${mapping.optionIndex},reason=${readResult.failureReason}"
}
```

Declare:

```kotlin
var optionReadFailures = 0
```

Use `readResult.copy(isMarked = false)` when `readResult.failureReason != null` before solid fusion:

```kotlin
val bubbleReadResult = if (readResult.failureReason == null) {
    readResult
} else {
    readResult.copy(isMarked = false)
}
```

Use `bubbleReadResult` for `bubbleMarked`.

Add debug after grouping:

```kotlin
debugInfo += "optionReadFailures=$optionReadFailures"
```

Keep cell resolver failures as full answer-area failures.

- [ ] **Step 7: Pass question types from engine**

In `AndroidOmrEngine.scanWithLayoutAndGrid`, add:

```kotlin
val questionTypesByQuestion = template.questions.map { it.type }
```

Pass it to both answer reader calls:

```kotlin
questionTypesByQuestion = questionTypesByQuestion,
```

- [ ] **Step 8: Run task tests**

Run:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21 GRADLE_USER_HOME=/tmp/codex-gradle sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.miniprogram.AndroidAnswerAreaReaderTest --tests com.answercard.grader.miniprogram.AndroidOmrEngineTest
```

Expected: PASS after updating any old single-choice test names/expectations that still describe global multi-mark collapse.

- [ ] **Step 9: Commit**

```bash
git add omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidAnswerAreaReader.kt \
        omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidOmrEngine.kt \
        app/src/test/java/com/answercard/grader/miniprogram/AndroidAnswerAreaReaderTest.kt \
        app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrEngineTest.kt
git commit -m "fix(omr): preserve answer read semantics"
```

---

### Task 4: Admission Reader Solid Union and Candidate Failures

**Files:**
- Modify: `omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidAdmissionNumberReader.kt`
- Test: `app/src/test/java/com/answercard/grader/miniprogram/AndroidAdmissionNumberReaderTest.kt`

**Interfaces:**
- Consumes: `AndroidPaperEdgeCleanDirections.forAdmission(...)`
- Produces: admission solid union where solid evidence can add marks but cannot erase bubble marks.

- [ ] **Step 1: Write failing admission solid-union test**

Add this test to `AndroidAdmissionNumberReaderTest`:

```kotlin
@Test
fun solidMarksAreUnionedWithBubbleAdmissionMarksInsteadOfReplacingThem() {
    val fixture = Fixture()
    fixture.mark(digitIndex = 0, numberValue = 1)
    fixture.mark(digitIndex = 1, numberValue = 2)
    fixture.mark(digitIndex = 2, numberValue = 3)
    fixture.mark(digitIndex = 3, numberValue = 4)
    val projectedCells = AndroidPaperProjectedCells(
        questionCells = emptyMap(),
        admissionNumberCells = fixture.layout.admissionNumberMappings.associate { mapping ->
            AndroidPaperAdmissionNumberCellKey(mapping.digitIndex, mapping.numberValue) to
                fixture.grid.cell(mapping.row, mapping.column)
        },
        debugInfo = emptyList(),
    )
    val solidMarks = AndroidSolidMarkOverlay(
        questionCells = emptySet(),
        admissionNumberCells = setOf(AndroidPaperAdmissionNumberCellKey(digitIndex = 0, numberValue = 7)),
        debugInfo = listOf("solid=test"),
    )

    val result = AndroidAdmissionNumberReader.read(
        frame = fixture.frame(),
        layout = fixture.layout,
        projectedCells = projectedCells,
        solidMarks = solidMarks,
    )

    assertTrue(result.success)
    assertEquals(7, result.digitResults[0].selectedNumber)
    assertTrue(result.digitResults[0].isMultiMarked)
    assertTrue(result.debugInfo.contains("solidFusion=union"))
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21 GRADLE_USER_HOME=/tmp/codex-gradle sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.miniprogram.AndroidAdmissionNumberReaderTest
```

Expected: FAIL because solid-only mode clears bubble marks and `solidFusion=union` is absent.

- [ ] **Step 3: Implement admission solid union**

In `AndroidAdmissionNumberReader`, add counters before the mapping loop:

```kotlin
var solidOnlyMarks = 0
var bubbleOnlyMarks = 0
var bothMarks = 0
var candidateReadFailures = 0
```

Replace the solid override block:

```kotlin
val effectiveReadResult = solidMarkResolver?.let { resolver ->
    readResult.copy(isMarked = resolver(mapping))
} ?: readResult
```

with:

```kotlin
if (readResult.failureReason != null) {
    candidateReadFailures += 1
    debugInfo += "candidateReadFailure=digitIndex=${mapping.digitIndex},numberValue=${mapping.numberValue},reason=${readResult.failureReason}"
}
val bubbleReadResult = if (readResult.failureReason == null) readResult else readResult.copy(isMarked = false)
val solidMarked = solidMarkResolver?.invoke(mapping) == true
val bubbleMarked = bubbleReadResult.isMarked
if (solidMarked && bubbleMarked) bothMarks += 1
if (solidMarked && !bubbleMarked) solidOnlyMarks += 1
if (!solidMarked && bubbleMarked) bubbleOnlyMarks += 1
val effectiveReadResult = bubbleReadResult.copy(isMarked = bubbleMarked || solidMarked)
```

Add debug entries before returning:

```kotlin
debugInfo += "solidFusion=union"
debugInfo += "solidOnlyMarks=$solidOnlyMarks"
debugInfo += "bubbleOnlyMarks=$bubbleOnlyMarks"
debugInfo += "bothMarks=$bothMarks"
debugInfo += "candidateReadFailures=$candidateReadFailures"
```

Do not change mapping-outside-grid behavior; those failures still return immediately.

- [ ] **Step 4: Run task tests**

Run:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21 GRADLE_USER_HOME=/tmp/codex-gradle sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.miniprogram.AndroidAdmissionNumberReaderTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add omr-core/src/main/java/com/answercard/grader/miniprogram/AndroidAdmissionNumberReader.kt \
        app/src/test/java/com/answercard/grader/miniprogram/AndroidAdmissionNumberReaderTest.kt
git commit -m "fix(omr): union solid admission marks"
```

---

### Task 5: Template Multiple-Choice State and UI

**Files:**
- Modify: `omr-core/src/main/java/com/answercard/grader/template/TemplateState.kt`
- Modify: `app/src/main/java/com/answercard/grader/template/TemplateJson.kt`
- Modify: `app/src/main/java/com/answercard/grader/ui/TemplateEditSheet.kt`
- Test: `app/src/test/java/com/answercard/grader/template/TemplateAddQuestionsTest.kt`
- Test: `app/src/test/java/com/answercard/grader/template/TemplateEditOperationsTest.kt`
- Test: add `app/src/test/java/com/answercard/grader/template/TemplateJsonTest.kt` if it does not already exist.

**Interfaces:**
- Produces: `TemplateState.addQuestions` preserves `AddQuestionRequest.type`.
- Produces: `TemplateState.editQuestion` preserves `EditQuestionRequest.type`.
- Produces: `TemplateState.batchEditSelectedQuestions(score: Int, optionCount: Int, type: QuestionType)`.
- Produces: multi-answer normalization that stores `AC` in option order.

- [ ] **Step 1: Write failing template model tests**

In `TemplateAddQuestionsTest`, replace `addQuestionRequestRejectsMultipleChoiceForNow` with:

```kotlin
@Test
fun addQuestionRequestPreservesMultipleChoice() {
    val template = TemplateState.default().addQuestions(
        AddQuestionRequest(
            startNumber = 16,
            count = 1,
            score = 2,
            optionCount = 4,
            type = QuestionType.MULTIPLE,
        ),
    )

    assertEquals(QuestionType.MULTIPLE, template.questions.last().type)
}
```

In `TemplateEditOperationsTest`, add:

```kotlin
@Test
fun editQuestionPreservesMultipleChoiceAndNormalizesAnswer() {
    val template = TemplateState.default()
        .withAnswer(3, "A")
        .editQuestion(
            originalNumber = 3,
            request = EditQuestionRequest(
                number = 30,
                score = 6,
                optionCount = 4,
                type = QuestionType.MULTIPLE,
            ),
        )
        .toggleAnswer(30, "C")

    val edited = template.questions.single { it.number == 30 }
    assertEquals(QuestionType.MULTIPLE, edited.type)
    assertEquals("AC", edited.answer)
}

@Test
fun batchEditSelectedQuestionsCanSetMultipleChoice() {
    val template = TemplateState.default()
        .toggleQuestionSelection(2)
        .toggleQuestionSelection(4)
        .batchEditSelectedQuestions(score = 3, optionCount = 3, type = QuestionType.MULTIPLE)

    val edited = template.questions.filter { it.number in setOf(2, 4) }
    assertEquals(listOf(QuestionType.MULTIPLE, QuestionType.MULTIPLE), edited.map { it.type })
    assertFalse(edited.any { it.selected })
}
```

Create or update `TemplateJsonTest`:

```kotlin
package com.answercard.grader.template

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateJsonTest {
    @Test
    fun roundTripsMultipleChoiceAnswer() {
        val template = TemplateState(
            name = "multi",
            questions = listOf(
                QuestionSetting(
                    number = 1,
                    answer = "CA",
                    score = 4,
                    type = QuestionType.MULTIPLE,
                ),
            ),
        )

        val decoded = TemplateJson.fromJson(TemplateJson.toJson(template))

        val question = decoded.questions.single()
        assertEquals(QuestionType.MULTIPLE, question.type)
        assertEquals("AC", question.answer)
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21 GRADLE_USER_HOME=/tmp/codex-gradle sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.template.TemplateAddQuestionsTest --tests com.answercard.grader.template.TemplateEditOperationsTest --tests com.answercard.grader.template.TemplateJsonTest
```

Expected: FAIL because multiple-choice is forced to single, batch signature lacks `type`, and JSON rejects `AC`.

- [ ] **Step 3: Implement template answer normalization**

In `TemplateState.kt`, add helpers near `optionLabels`:

```kotlin
fun normalizeQuestionAnswer(answer: String, labels: List<String>, type: QuestionType): String {
    val trimmed = answer.trim()
    return if (type == QuestionType.MULTIPLE) {
        labels.filter { label -> trimmed.contains(label) }.joinToString(separator = "")
    } else {
        labels.firstOrNull { label -> trimmed == label }.orEmpty()
    }
}
```

Update `toggleAnswer`:

```kotlin
fun toggleAnswer(question: Int, answer: String): TemplateState =
    copy(questions = questions.map {
        if (it.number != question) {
            it
        } else if (it.type == QuestionType.MULTIPLE) {
            val current = it.options.filter { label -> it.answer.contains(label) }.toSet()
            val next = if (answer in current) current - answer else current + answer
            it.copy(answer = it.options.filter { label -> label in next }.joinToString(separator = ""))
        } else if (it.answer == answer) {
            it.copy(answer = "", score = 0)
        } else {
            it.copy(answer = answer)
        }
    })
```

Update `withQuestionOptions` answer assignment:

```kotlin
answer = normalizeQuestionAnswer(it.answer, labels, it.type),
```

Update `addQuestions`:

```kotlin
val type = request.type
```

Update `editQuestion`:

```kotlin
val type = request.type
...
answer = normalizeQuestionAnswer(question.answer, labels, type),
type = type,
```

Change `batchEditSelectedQuestions` signature and body:

```kotlin
fun batchEditSelectedQuestions(score: Int, optionCount: Int, type: QuestionType = QuestionType.SINGLE): TemplateState {
    val safeOptionCount = optionCount.coerceIn(2, 4)
    val labels = optionLabels(safeOptionCount)
    return copy(
        questions = questions.map { question ->
            if (question.selected) {
                question.copy(
                    score = score.coerceAtLeast(0),
                    optionCount = safeOptionCount,
                    options = labels,
                    answer = normalizeQuestionAnswer(question.answer, labels, type),
                    type = type,
                    selected = false,
                )
            } else {
                question
            }
        },
    )
}
```

- [ ] **Step 4: Update TemplateJson**

Import the helper if needed; `TemplateJson` is in the same package, so call it directly:

```kotlin
answer = normalizeQuestionAnswer(item.optString("answer", "A"), labels, type),
```

Replace the old `.takeIf { it in labels }.orEmpty()` expression.

- [ ] **Step 5: Update TemplateEditSheet UI state**

Add import:

```kotlin
import com.answercard.grader.template.QuestionType
```

Change answer pill selected state:

```kotlin
AnswerPill(
    label = option,
    selected = if (question.type == QuestionType.MULTIPLE) {
        question.answer.contains(option)
    } else {
        question.answer == option
    },
    onClick = { onAnswerChange(option) },
)
```

In `AddQuestionDialog`, add:

```kotlin
var type by remember { mutableStateOf(QuestionType.SINGLE) }
...
type = QuestionType.SINGLE
```

Call:

```kotlin
TypeAndOptions(
    optionCount = optionCount,
    type = type,
    onTypeChange = { type = it },
    onOptionCountChange = { optionCount = it },
)
```

Pass `type = type` into `AddQuestionRequest`.

In `EditQuestionDialog`, add:

```kotlin
var type by remember { mutableStateOf(question.type) }
...
type = question.type
```

Pass `type` through `TypeAndOptions` and `EditQuestionRequest`.

In `BatchEditDialog`, add `type` state, pass it to `TypeAndOptions`, change `onConfirm` to `(Int, Int, QuestionType) -> Unit`, and call:

```kotlin
else -> onConfirm(parsedScore, optionCount, type)
```

In `TemplateEditSheet`, update batch callback:

```kotlin
onConfirm = { score, optionCount, type ->
    onTemplateChange(template.batchEditSelectedQuestions(score = score, optionCount = optionCount, type = type))
    batchDialogOpen = false
},
```

Replace `TypeAndOptions` and `DisabledChoice` with clickable choices:

```kotlin
@Composable
private fun TypeAndOptions(
    optionCount: Int,
    type: QuestionType,
    onTypeChange: (QuestionType) -> Unit,
    onOptionCountChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("题型:", fontSize = 15.sp, color = UiTokens.TextPrimary)
        Choice("单选", selected = type == QuestionType.SINGLE) { onTypeChange(QuestionType.SINGLE) }
        Choice("多选", selected = type == QuestionType.MULTIPLE) { onTypeChange(QuestionType.MULTIPLE) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("选项个数:", fontSize = 15.sp, color = UiTokens.TextPrimary)
        StepperButton("-", enabled = optionCount > 2) { onOptionCountChange(optionCount - 1) }
        Text("$optionCount", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        StepperButton("+", enabled = optionCount < 4) { onOptionCountChange(optionCount + 1) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("选项内容:", fontSize = 15.sp, color = UiTokens.TextPrimary)
        optionLabels(optionCount).forEach { label ->
            Box(
                Modifier
                    .width(42.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(UiTokens.SecondaryBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 15.sp, color = UiTokens.TextPrimary)
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (selected) UiTokens.Red else UiTokens.Separator),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        }
        Text(label, color = if (selected) UiTokens.TextPrimary else UiTokens.TextSecondary, fontSize = 15.sp)
    }
}
```

- [ ] **Step 6: Run task tests**

Run:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21 GRADLE_USER_HOME=/tmp/codex-gradle sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.template.TemplateAddQuestionsTest --tests com.answercard.grader.template.TemplateEditOperationsTest --tests com.answercard.grader.template.TemplateJsonTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add omr-core/src/main/java/com/answercard/grader/template/TemplateState.kt \
        app/src/main/java/com/answercard/grader/template/TemplateJson.kt \
        app/src/main/java/com/answercard/grader/ui/TemplateEditSheet.kt \
        app/src/test/java/com/answercard/grader/template/TemplateAddQuestionsTest.kt \
        app/src/test/java/com/answercard/grader/template/TemplateEditOperationsTest.kt \
        app/src/test/java/com/answercard/grader/template/TemplateJsonTest.kt
git commit -m "feat(template): enable multiple choice questions"
```

---

### Task 6: Engine Integration and Regression Verification

**Files:**
- Modify: `app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrEngineTest.kt`
- Optionally modify: `docs/omr-stability-optimizations.md` only if observed debug behavior changes need documentation.

**Interfaces:**
- Consumes: all previous tasks.
- Produces: end-to-end proof that multiple-choice marks can score full credit and solid union does not erase bubble marks.

- [ ] **Step 1: Write failing engine integration test**

Add import:

```kotlin
import com.answercard.grader.template.QuestionType
```

Add this test to `AndroidOmrEngineTest`:

```kotlin
@Test
fun scansMultipleChoiceQuestionWithFullScore() {
    val template = TemplateState(
        name = "multi",
        questions = listOf(
            QuestionSetting(
                number = 1,
                answer = "AC",
                score = 4,
                type = QuestionType.MULTIPLE,
                partialScore = 1,
            ),
        ),
    )
    val synthetic = AndroidOmrSyntheticFrameFactory(template)
    synthetic.markAdmissionNumber("1234")
    synthetic.markAnswer(questionIndex = 0, optionIndex = 0)
    synthetic.markAnswer(questionIndex = 0, optionIndex = 2)

    val result = AndroidOmrEngine.scanWithPrecomputedGridForTest(
        frame = synthetic.frame(),
        template = template,
        grid = synthetic.grid,
    )

    assertTrue(result.failureReason ?: result.debugInfo.joinToString(), result.success)
    assertEquals(listOf("A", "C"), result.answerArea?.questions?.single()?.selectedLabels)
    assertEquals(4.0, result.score?.totalScore ?: -1.0, 0.0)
}
```

- [ ] **Step 2: Run test to verify failure on old behavior if earlier tasks were skipped**

Run:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21 GRADLE_USER_HOME=/tmp/codex-gradle sh gradlew :app:testDebugUnitTest --tests com.answercard.grader.miniprogram.AndroidOmrEngineTest
```

Expected before earlier tasks: FAIL because selected labels collapse to one option or template multiple type is unavailable. Expected after Tasks 1-5: PASS.

- [ ] **Step 3: Run omr-core verification**

Run:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21 GRADLE_USER_HOME=/tmp/codex-gradle sh gradlew :omr-core:test
```

Expected: PASS.

- [ ] **Step 4: Run app unit verification**

Run:

```bash
env JAVA_HOME=/usr/lib/jvm/java-21 GRADLE_USER_HOME=/tmp/codex-gradle sh gradlew test
```

Expected: PASS, or only the previously documented Robolectric graphics baseline failures. If failures appear, compare against the current known-failing set before changing code.

- [ ] **Step 5: Inspect git diff for scope**

Run:

```bash
git diff --stat HEAD
git diff --name-only HEAD
```

Expected: only files listed in this plan changed. No geometry line-snapping, homography, corner detector, or renderer geometry changes.

- [ ] **Step 6: Commit final integration test or docs updates**

```bash
git add app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrEngineTest.kt docs/omr-stability-optimizations.md
git commit -m "test(omr): cover multiple choice scan parity"
```

If `docs/omr-stability-optimizations.md` was not changed, run:

```bash
git add app/src/test/java/com/answercard/grader/miniprogram/AndroidOmrEngineTest.kt
git commit -m "test(omr): cover multiple choice scan parity"
```

---

## Self-Review

**Spec coverage:**
- Multiple-choice preservation: Task 3 and Task 6.
- Single-choice scoped disambiguation: Task 3.
- Solid per-cell union: Task 3 for answers, Task 4 for admission number.
- Edge clean direction routing: Task 2.
- 3x3 solidity evidence: Task 1.
- Local bubble failure downgrade: Task 3 for answers, Task 4 for admission candidates.
- Template multiple-choice enablement: Task 5.
- Geometry C exclusions: Global Constraints and Task 6 diff scope check.

**Placeholder scan:** The plan contains concrete code snippets, commands, expected outcomes, and commit steps for every task.

**Type consistency:** New fields are added in Task 1 before Task 3 uses `centralMeanGray`; edge helper is added in Task 2 before reader tasks depend on it; `batchEditSelectedQuestions` gets a default `type` parameter to preserve old callers while enabling UI updates.
