# OMR Engine Implementation Plan

## Current Route

当前主线架构是：

```text
小程序识别底座 + Android 现有 5x3 纸面布局
```

阶段 1 到阶段 4 的识别底座继续保留，并作为后续 Android 纸面识别的基础：

- 角点检测参考小程序逻辑。
- 虚拟网格生成参考小程序虚拟网格思路。
- 单格读取参考小程序 `k(...)`。
- BFS 连通域去噪参考小程序 `y(...)`。
- 题目、选项、准考证号 mapping 使用 Android 现有纸面模板。

不再把小程序 `getSize(...)`、25/30 竖排模板、`readBody` 竖排 mapping 作为主线布局方案。

阶段 5A 的小程序模板布局迁移已暂停，不进入主线：

```text
stash@{0}: On master: wip(omr): pause mini-program template layout
```

阶段 5B 是当前主线：

- Android 现有 `5x3` 横排纸面模板。
- `AndroidPaperTemplateLayout`。
- `AndroidPaperTemplateBuilder`。
- 每个 band 有 3 个横向题组，每组 5 题纵向排列。
- 1-5 题在左组，6-10 题在中组，11-15 题在右组，16 题进入下一 band。
- A/B/C/D 横向排列。
- 准考证号固定 4 位，数字 0..9 从左到右，mapping 与旧 `ExamIdReader` 一致。

## Baseline

当前已提交基线：

```text
0f13450 feat(omr): add android paper template layout
```

旧 `com.answercard.grader.vision` 链路暂不删除，后续作为 fallback、debug 对照和回滚点。

旧扫描调用路径：

```text
ScanScreen
 -> CameraPreview(onFrame)
 -> ImageProxyBitmap.grayscaleBitmap(image)
 -> OmrScanner.scan(bitmap, template, layout, scale = 3f)
 -> CornerDetector.detect(oriented, layout)
 -> PerspectiveWarp.normalize(oriented, corners, layout, scale)
 -> AnswerReader.readAnswers(normalized, layout, scale)
 -> ExamIdReader.readExamId(normalized, layout, scale)
 -> Grader.grade(template, recognizedAnswers)
```

新 OMR 底座当前主要放在：

```text
app/src/main/java/com/answercard/grader/miniprogram/
```

虽然包名仍是 `miniprogram`，但主线含义已经调整为：识别算法底座参考小程序，纸面布局使用 Android 现有模板。后续可单独评估是否统一迁移到 `omr` 或 `recognition` 包，当前不为命名做大范围重构。

## Completed Stages

### Stage 1: Corner Anchor Detection

已完成并提交：

- `MiniProgramFrame.kt`
- `MiniProgramGeometry.kt`
- `CornerAnchorMatcher.kt`
- `MiniProgramGeometryTest.kt`
- `CornerAnchorMatcherTest.kt`

能力：

- 中心 20% 区域均值。
- 默认阈值偏移 `-30`。
- 小程序 `isQ` 四角几何校验。
- LU / LD / RU / RD 四角锚标检测。
- 缺一个角拒绝识别。
- 实心答案块不能冒充角点。
- 候选角点先过几何校验，再选更清晰的锚标。

### Stage 2: Virtual Grid Generation

已完成并提交：

- `MiniProgramGrid.kt`
- `MiniProgramCell.kt`
- `MiniProgramGridBuilder.kt`
- `MiniProgramGridBuilderTest.kt`

能力：

- 根据 LU / LD / RU / RD 四角点生成 `(rows + 1) * (columns + 1)` 网格交点。
- 支持 `MiniProgramGrid.point(row, column)`。
- 支持 `MiniProgramGrid.cell(row, column)`。
- 标准矩形均匀分布。
- 轻微透视四边形使用双线性插值。
- row 表示纵向，column 表示横向。
- 不做整图透视变换。

### Stage 3: Basic Bubble Cell Reader

已完成并提交：

- `MiniProgramBubbleReader.kt`
- `MiniProgramBubbleReadResult.kt`
- `MiniProgramBubbleReaderTest.kt`

能力：

- 输入 `MiniProgramFrame + MiniProgramCell`。
- 对非轴对齐 cell 做内部采样。
- 块内自适应阈值：
  - `blackRatio = 0.2`
  - `rangePercent = 0.9`
- 二值矩阵语义：
  - `0 = 黑`
  - `1 = 白`
- 支持 `LEFT / RIGHT / UP / DOWN` 边缘清理。
- 当前基础判断规则为 `centralBlackCount > 0`。

### Stage 4: Connected Component Noise Cleanup

已完成并提交：

- `MiniProgramConnectedComponentCleaner.kt`
- `MiniProgramConnectedComponentCleanerTest.kt`
- 已接入 `MiniProgramBubbleReader`。

能力：

- 对单格二值矩阵扫描黑色连通块。
- 使用 4 邻域，与小程序 `y(...)` 保持一致。
- 面积阈值为 `rows * columns * 0.125`。
- 面积 `< threshold` 的黑色连通块清理为白色 `1`。
- 面积 `== threshold` 的连通块保留。
- `centralBlackCount` 和 `isMarked` 基于清理后的矩阵。

### Stage 5: Android 5x3 Paper Template Layout

已完成并提交：

- `AndroidPaperTemplateLayout.kt`
- `AndroidPaperTemplateBuilder.kt`
- `AndroidPaperTemplateBuilderTest.kt`

主线布局规则：

- 使用 Android 当前 App 已有纸面模板。
- 每个 band 有 3 个横向题组。
- 每组 5 题纵向排列。
- 每个 band 共 15 题：
  - 1-5 题：左组。
  - 6-10 题：中组。
  - 11-15 题：右组。
  - 16 题：下一 band 左组第一行。
- 选项 A/B/C/D 横向排列：
  - A 对应 `column + 0`。
  - B 对应 `column + 1`。
  - C 对应 `column + 2`。
  - D 对应 `column + 3`。
- 每个题组固定占 4 列。
- 当前支持每题 2..4 个选项，和 Android 现有模板一致。
- 最多 60 题，和旧模板上限一致。
- 准考证号固定 4 位：
  - `digitIndex` 对应纵向 row。
  - `numberValue` 对应横向 column。
  - 数字 0..9 从左到右。
  - mapping 与旧 `ExamIdReader` / `TemplateGeometry.examIdDigitBox(...)` 一致。

阶段 5A 已暂停，不进入主线：

- 小程序 `getSize(...)` 作为主线布局。
- 25/30 列选择作为主线布局。
- 小程序竖排题目 mapping。
- 小程序 `readBody` 竖排布局直接迁移。

这些内容只作为 stash 参考，不作为当前 Android 纸面识别主线。

## Remaining Stages

### Stage 6: Answer Area Reading Based on AndroidPaperTemplateLayout

目标：

- 使用 `AndroidPaperTemplateLayout.questionMappings` 遍历答案区。
- 对每个 mapping 调用 `MiniProgramGrid.cell(row, column)` 获取 cell。
- 对每个 cell 调用 `MiniProgramBubbleReader`。
- 输出每题每个选项的读取结果和 debug 数据。

不做：

- 不评分。
- 不接 CameraX。
- 不改 UI。
- 不删除旧识别链路。

### Stage 7: Admission Number Reading Based on AndroidPaperTemplateLayout

目标：

- 使用 `AndroidPaperTemplateLayout.admissionNumberMappings` 遍历准考证号区域。
- 每一位数字按 0..9 横向候选读取。
- 与旧 `ExamIdReader` 的物理方向保持一致。
- 输出准考证号字符串、失败原因和 debug 数据。

不做：

- 不评分。
- 不接 CameraX。
- 不改 UI。

### Stage 8: Scoring Logic

目标：

- 基于 Stage 6 的答案读取结果接入评分。
- 保留旧 `Grader` 作为 fallback/debug 对照。
- 明确单选、多选、未涂、多涂等情况的规则。

### Stage 9: Local Image / Synthetic Full Recognition Tests

目标：

- 在不接相机的前提下，用本地图片或合成图完成完整识别闭环。
- 覆盖四角检测、网格、准考证号、答案区、评分。
- 建立回归测试样例。

### Stage 10: CameraX Y Plane Integration

目标：

- 新增相机帧适配层。
- 正确处理 `rowStride`、`pixelStride`、`rotationDegrees`。
- 将 CameraX Y plane 转为新 OMR 底座可读的灰度帧。

### Stage 11: Scan Screen Integration

目标：

- 将新 OMR engine 接入扫描界面。
- 保留旧 `OmrScanner` fallback 开关。
- 不在首次接入时删除旧链路。

### Stage 12: UI Overlay

目标：

- 显示四角点、虚拟网格、已识别气泡、准考证号、失败原因。
- 用于实测调参和 debug。

### Stage 13: Phone Testing and Parameter Tuning

目标：

- 使用真实手机拍摄样张测试。
- 调整阈值、边缘清理、BFS 阈值和失败原因。
- 保持参数集中管理，避免散落在业务代码里。

### Stage 14: Performance Optimization

目标：

- 复用大数组，减少每帧分配。
- 控制实时扫描频率。
- 优化热点循环，避免实时路径卡顿。
- 不引入 OpenCV。

## Global Constraints

- 不接 CameraX，直到 Stage 10。
- 不改扫描 UI，直到 Stage 11。
- 不做 UI 覆盖层，直到 Stage 12。
- 不删除旧识别链路。
- 不引入 OpenCV。
- 不做整图透视变换。
- row 表示纵向/y 方向。
- column 表示横向/x 方向。
- 图像坐标系左上角为原点。
- 二值矩阵语义保持 `0=黑, 1=白`。
- 基础虚拟 grid 仍可由 `MiniProgramGrid.cell(row, column)` 映射到原始图像坐标。
- Android 生产模板内容读取不再使用粗暴均分 grid；答案区和准考证号读取使用 `TemplateGeometry` 真实矩形，经四角 anchors 投影到原始图像坐标。

## Stage 9G Architecture Lock

The active Android OMR route after Stage 9G is:

```text
Mini-program recognition base + Android existing 5x3 paper layout
```

The production paper template uses the Android existing 5x3 content layout,
independent L-shaped corner anchors, and no closed full-page outer border.
Formal scans still start with `CornerAnchorMatcher` and still use the detected
four corner anchors. The engine does not run a full-image `PerspectiveWarp`, and
OpenCV is not introduced.

The Android production read model is no longer a coarse evenly divided virtual
grid. The virtual grid can still exist for diagnostics and for legacy test-only
precomputed-grid paths, but it is not the production read model for Android
paper answers or admission number digits.

The production read model is now:

```text
TemplateRenderer / TemplateGeometry draws the paper
-> TemplateGeometry provides the same logical rectangles
-> AndroidPaperProjectedCells projects those rectangles through the four anchors
-> AndroidAnswerAreaReader / AndroidAdmissionNumberReader read projected cells
-> MiniProgramBubbleReader reads each projected cell in the original frame
```

This locks the principle:

```text
The reader reads the same TemplateGeometry rectangles that the renderer draws.
```

`AndroidPaperProjectedCells` is the central production-coordinate component for
Android paper reads. `AndroidAnswerAreaReader` and
`AndroidAdmissionNumberReader` both support projected-cell read paths. The old
precomputed-grid path remains available for tests, but it is not the formal
`AndroidOmrEngine.scan(frame, template)` production path.

Stage 9G did not relax or change:

- `CornerAnchorMatcher` thresholding or `isQ` geometry checks.
- `MiniProgramBubbleReader` thresholding.
- `MiniProgramConnectedComponentCleaner` BFS/noise parameters.
- CameraX, UI, `ScanScreen`, `CameraPreview`, or the legacy `OmrScanner` path.

Stage 9G validation:

- Debug image: `app/build/omr-debug/production-template-filled.png`.
- Overlay: `app/build/omr-debug/production-template-filled-debug-overlay-after.png`.
- Admission number: `1234`.
- Answers: Q1=A, Q2=B, Q6=C, Q11=D, Q16=A.
- Score: `10.0 / 10.0`.
- `AndroidOmrEngine.scan(...)` success: `true`.
- Overlay-after showed reader centers and filled-block centers within less than
  1 pixel on the checked answer and admission cells.

## Stage 10 Boundary Lock

Stage 10 should be CameraX Y-plane adapter preparation, not UI integration.

The next-stage boundary is:

- Add an adapter from `ImageProxy` / Y plane data to `MiniProgramFrame`.
- Unit-test `rowStride`, `pixelStride`, `cropRect`, and rotation handling.
- Do not connect the adapter to UI yet.
- Do not run phone testing yet.
- Do not change OMR recognition thresholds.
- Do not change the Stage 9G TemplateGeometry projection coordinate model.
- Do not change CameraX screen flow, `ScanScreen`, or `CameraPreview` in this
  pre-adapter phase.

## Validation Rules

- 每个阶段都需要独立测试。
- 每个阶段结束后旧 `vision` 链路仍应可编译。
- `.\gradlew.bat :app:testDebugUnitTest` 必须通过。
- `.\gradlew.bat :app:assembleDebug` 必须通过。
- CameraX 接入前，本地图片或合成图完整识别测试必须通过。
- UI 接入前，fallback 开关必须存在。
