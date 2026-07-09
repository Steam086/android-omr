# OMR 读取语义与判格抗污染修正设计（A+B）

## 背景

当前仓库已经具备小程序算法的基础骨架：每格自适应阈值、BFS 去噪、实心角标检测、透视投影和多帧共识。但答案读取层还存在两类严重差距：

1. 读取语义会改写题目含义：多涂总是强制收敛为单选，`solidMarks` 一旦存在就全局覆盖 `BubbleReader`。
2. 单格判黑抗污染不足：`cleanEdges` 已实现但生产调用不传方向，判涂主要依赖中央区域 8% 黑占比。

本轮只修 A+B：读取语义与判格抗污染。几何局部线吸附、分带 homography、方向消歧属于后续 C，不在本轮实现。

## 目标

- 多选题能够保留多个真实涂项，并能通过现有多选评分逻辑拿到满分。
- 单选题仍能在污染导致多个候选时做消歧，但消歧只作用于单选题。
- `solidMarks` 不再让整张卡切换到 solid-only 模式；每格融合 bubble 与 solid 证据。
- 答案格和准考证号格按位置传入 `edgeCleanDirections`，让已实现的贴边线擦除真正生效。
- `MiniProgramBubbleReader` 使用 3x3 邻域实心度证据降低框线、斜划痕、细笔迹误判。
- 失败行为从“局部读格失败导致整答题区失败”降级为可观测的局部未识别，结构性错误仍失败。

## 非目标

- 不实现逐行边线吸附、圆弧外推、分带 homography 或纸张弯曲建模。
- 不改角标检测主流程，不删除 L-bracket fallback。
- 不引入小程序 5 种复杂题型；本轮只修现有 `SINGLE` / `MULTIPLE` 的读卡语义。
- 不重写模板渲染几何。

## 设计

### 1. 读取 API 带题型上下文

`AndroidAnswerAreaReader.read` 增加题型信息输入，优先传入 `template.questions` 派生的 `QuestionType` 列表。`AndroidOmrEngine.scanWithLayoutAndGrid` 负责传递题型与选项标签。

旧的测试辅助入口可以保留默认单选行为，以减少调用方 churn；真实扫描必须使用模板题型。

### 2. 多选不收敛，单选相对消歧

当前 `AndroidAnswerAreaReader` 在 `marked.size > 1` 时无条件选最黑一个。本轮改为：

- `QuestionType.MULTIPLE`：`selectedOptions = marked.map { optionIndex }`，保留所有涂项。
- `QuestionType.SINGLE`：仅单选题进入消歧。
- 单选消歧使用连续强度排序，不只简单取最大。强度定义为 `blackThreshold - centralMeanGray`，只对已判涂候选计算；保留 `strength >= bestStrength * 0.84` 的候选波段，再决定单选结果。

如果单选题多个候选落入 0.84 波段，结果仍保留 `strength` 最大的一个用于兼容当前评分，但 `isMultiMarked=true`，debug 中记录 `singleChoiceAmbiguous=true`。这样不会扩大当前业务行为，但能观察污染来源。

### 3. 开放模板多选语义

`TemplateState.addQuestions`、`editQuestion`、`batchEditSelectedQuestions` 不再把 `QuestionType.MULTIPLE` 强制改为 `SINGLE`。

UI 的 `TemplateEditSheet` 中 `TypeAndOptions` 从禁用展示改为可切换题型。多选题答案选择允许多个选项组合，例如 `AC`；单选题仍保持单项答案。

模板 JSON 已保存 `type` 和 `partialScore`，无需格式迁移；但 `fromJson` 当前 `answer.takeIf { it in labels }` 只支持单标签，本轮要改为按题型清洗答案：

- 单选：答案必须是一个合法 label。
- 多选：答案可由多个合法 label 组成，去重并按选项顺序排序。

### 4. solid 逐格融合，不全卡覆盖

删除答案区和准考证号读取中的 solid-only 覆盖语义。每格读取结果改为融合：

- `bubbleMarked = readResult.isMarked`
- `solidMarked = solidMarkResolver?.invoke(mapping) == true`
- `effectiveMarked = bubbleMarked || solidMarked`

读取结果仍以 `MiniProgramBubbleReadResult.copy(isMarked = effectiveMarked)` 传递，避免大范围改模型；debug 增加统计：`solidFusion=union`、`solidOnlyMarks`、`bubbleOnlyMarks`、`bothMarks`。

准考证号也采用同样策略：solid 可以补强重涂数字，但不能清掉浅涂数字。

### 5. 接线 edgeCleanDirections

新增小工具函数按映射位置推导边线方向：

- 答案区：
  - 每个连续选项组首列传 `LEFT`。
  - 每个连续选项组末列传 `RIGHT`。
  - 答案区第一行传 `UP`，最后一行传 `DOWN`。
- 准考证号：
  - 数字 0 传 `LEFT`，数字 9 传 `RIGHT`。
  - 每个准考证号行的数字格传 `UP` 和 `DOWN`，因为数字格高度贴近横向框线。

方向推导应基于 `AndroidPaperQuestionMapping` / `AndroidPaperAdmissionNumberMapping` 和 layout area，不依赖像素坐标，便于单测。

debug 中不再固定 `edgeCleanDirections=none`，改为统计实际使用方向，例如 `edgeCleanDirections=active` 和各方向计数。

### 6. 3x3 实心度判涂

`MiniProgramBubbleReader` 当前把 `containCount` 填为 `centralBlackCount`。本轮改为真正的实心度计数：

- 在清理后的 binary 上扫描中央区域。
- 某个中心像素及其 3x3 邻域至少 8 个像素为黑时计入 `containCount`。
- 贴近采样边缘的 3x3 不参与，避免越界和框线污染。

`isMarked` 使用 `containCount` 作为主判据，`centralBlackCount` 作为辅助与 debug：

- 实心涂块：`containCount >= max(4, centralArea * 0.05)` 且实心证据的包围盒宽高都不小于采样格宽高的 25% 时判涂。
- 浅涂实心块：只要块内相对阈值能二值化为稳定 3x3 黑域，也应判涂。
- 细线、斜划痕、孤立边线：即使中央黑占比超过旧 8% 门槛，只要没有足够 3x3 实心证据或二维包围盒过窄，就不判涂。

这些阈值作为本轮初始实现常量。只有合成污染测试或现有样张回归证明它们过严/过宽时，才在同一提交中连同测试证据一起调整。

### 7. 局部读格失败降级

`AndroidAnswerAreaReader` 当前任何一个格失败都会返回整个答题区失败。本轮改为：

- cell resolve 结构性失败，如映射整体缺失或 question mapping 越界，仍可失败。
- 单个 bubble read 失败时，为该选项生成未涂结果并记录 option-level warning/debug。
- 如果某题所有选项都失败，该题输出 blank，并在 debug 中记录 `questionReadFailures`。

`AndroidOmrEngine` 不再因为少量答案格失败直接丢弃整帧；评分层会按 blank/missing 产生可见分数或 warning。

准考证号读取可在本轮保持现有结构性失败策略，但 bubble 读格失败应同样尽量降级为该候选未涂，避免一个数字候选破坏整张卡。

## 测试计划

### Unit tests

- `AndroidAnswerAreaReaderTest`
  - 多选题 A+C 同时涂选后 `selectedOptions=[0,2]`，`isMultiMarked=true`。
  - 单选题 A+C 同时涂选后只选最强项，但 `isMultiMarked=true`。
  - `solidMarks` 与 bubble 融合：重涂 solid-only 和浅涂 bubble-only 都保留。
  - 单个 bubble failure 不导致整个 answer area failure。

- `MiniProgramBubbleReaderTest`
  - 贴左/右边线传入 edge clean 后不判涂。
  - 未清理边线可作为对照保持风险可见。
  - 斜划痕或细线不满足 3x3 实心度，不判涂。
  - 浅色实心块仍判涂。

- `AndroidAdmissionNumberReaderTest`
  - solid 与 bubble 融合，不清掉浅涂数字。
  - 数字 0/9 和上下边方向传入 `MiniProgramBubbleReader` 可通过 debug/结果观测。

- `TemplateState` / `TemplateJson` / UI state tests
  - 添加、编辑、批量编辑能保留 `QuestionType.MULTIPLE`。
  - 多选答案 `AC` 序列化/反序列化后不丢失。

### Integration tests

- `AndroidOmrEngineTest` 或桌面渲染测试：
  - 模板包含多选题，正确涂 A+C，得满分。
  - 同一张卡中同时存在 solid 重涂与浅涂，答案都被保留。

### Regression commands

- `sh gradlew :omr-core:test`
- 针对 app 层改动再跑 `sh gradlew test`；若遇到项目记录中的存量 Robolectric 图形失败，只记录失败集合，不借本轮改动处理。

## 风险与取舍

- 3x3 实心度可能让极浅、极细但真实的涂痕更容易判空；这是刻意取舍，优先减少框线和划痕假阳性。
- 多选 UI 开放会改变此前“多选禁用”的产品行为；但评分器和 JSON 已有多选字段，属于补齐已有能力。
- solid union 可能让少量误检 solid blob 增加假阳性；通过最近中心匹配容差和 debug 统计监控，不再用它覆盖 bubble。
- 局部失败降级会让某些原本整帧失败的情况变成低分或警告；这是为了避免单格波动清空共识。

## 验收标准

- 多选题正确多涂可以满分，不再被强制降为 partial score。
- 实心重涂存在时，浅涂答案不会消失。
- 贴边框线和细划痕不再轻易触发 `isMarked`。
- 少量单格读取失败不再导致答题区整体 failure。
- 现有 solid marker、perspective mapping、consensus、blank admission number 行为不被回退。
