# 模板可选项：保留/去除“姓名·学号”表头区

## 需求

生成答题卡模板时可选择是否保留左上角“班级/姓名”手写栏与“准考证号”涂卡区（合称表头区）。
默认保留（与旧版行为一致）；不保留时整体布局垂直收缩、四角定位标随之内收，便于打印。
去除表头后识别算法必须仍能正确识别答案并计分，准考证号读取结果为空。

## 数据模型

- `TemplateState.showHeader: Boolean = true`，新增 `withShowHeader(show)`。
- `TemplateJson` 序列化 `showHeader` 字段；旧 JSON 缺省按 `true` 读取，保证兼容既有模板。

## 布局（omr-core / TemplateGeometry）

- `CardLayout` 增加 `showHeader` 字段。
- `buildLayout`：表头偏移 `headerOffset = showHeader ? HEADER_HEIGHT + HEADER_DIVIDER_GAP : 0`。
  题目区 y 坐标与总高度都基于 `headerOffset`，去表头后卡片高度减少 104（15 题卡从 216 → 112）。
  `examIdRows` 在无表头时为空列表。卡片宽度不变（题目三栏布局仍需要整宽）。
- 四角定位标始终相对渲染尺寸绘制，高度收缩即“四角收缩”。

## 识别链路（omr-core）

- `AndroidPaperTemplateBuilder.build` 允许 `admissionNumberDigits = 0`：
  无准考证行，网格行数只含答案区，`answerArea` 从第 0 行开始，`admissionNumberMappings` 为空。
- `AndroidOmrEngine` 依据 `template.showHeader` 传 0 位准考证号；
  `AndroidAdmissionNumberReader` 对空映射天然返回成功且 `digits=""`（无需改动）。
- `AndroidSolidMarkDetector.matchAdmissionNumberCell` 在 `examIdRows` 为空时直接跳过，
  避免 `examIdDigitBox` 对空列表取索引。
- 角标定位的期望宽高比 `expectedRenderedAspectRatio` 由同一几何推导，自动适配紧凑卡片。

## 渲染（app / TemplateRenderer）

- `layout.showHeader == false` 时跳过表头分隔线、班级/姓名栏、准考证涂卡区的绘制。

## UI

- `TemplateEditSheet` 顶部新增“姓名/学号区”开关；关闭时隐藏“准考证号：N位 修改”入口。
  创建考试后立即进入该编辑页，即可在生成时选择。
- `ShareTemplateScreen` 徽章：无表头时显示“无考号”。
- `ScanScreen`：无表头模板允许 `examId` 为空时保存扫描记录（考号存空串），语音播报已天然跳过空考号。

## CLI

- `omr-cli` 新增 `--no-header`，便于桌面端对无表头样张回归。

## 验证策略

- 本机无法信任 app 层 Robolectric 原生图形测试（未改动前 13 个中已有 7 个因本机字体/Skia 差异失败，
  例如准考证 "1234" 被读成 "0123"），因此识别验证以纯 JVM 的 omr-core 测试为准：
  在 omr-core 测试源中用 `java.awt` 实现与 `TemplateRenderer` 几何一致的桌面渲染器，
  渲染有/无表头两种卡、按模板几何填涂，再经 `AndroidOmrEngine.scan` 断言答案、得分与空考号。
- app 层同步补充 Robolectric 端到端用例与纯 JVM 的几何/JSON 用例；后者在本机可作判据。

## 已知限制

- 四角定位标 180° 中心对称，引擎本就不具备旋转 180° 的方向消歧（实测旋转样张照片识别失败并拒识）。
  有表头时倒置卡会因“准考证号全空”被拒；无表头卡倒置时缺少该兜底，需正立扫描。
