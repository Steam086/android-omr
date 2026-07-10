# 相机扫描稳定性优化：方块角标 + 透视映射 + 多帧共识 + 防抖门

> 2026-07-09 后续：新生成模板已进一步升级为四个唯一编码角标；本文件保留实心方块阶段的背景、基线和兼容策略。当前方案见 `docs/superpowers/plans/2026-07-09-coded-fiducial-template.md`。

## 背景与问题定位

手机实拍答题卡时分数在帧间波动。桌面扰动实验（`DesktopCameraInstabilityProbeTest`）复现并定位了四个叠加原因：

1. App 首个成功帧即记录/播报，无多帧一致性确认（`ScanScreen`）。
2. 实心标记→选项格匹配余量为零：L 形角标存在 outer/innerRight 两个相差 26 单位的参考候选，
   真实锚点落在两者之间；`RECT_TOLERANCE=4` 大于选项框间隙 3.5，`firstOrNull` 按 A→D 顺序
   使边界点永远判给靠左选项（Q11 D→C、Q6 C→B）。
3. 双线性插值无法表达透视畸变，残余误差在离锚点最远的右边缘最大。
4. 检测到 ≥1 个实心答案标记时整体覆盖 BubbleReader，模糊帧组件增减导致每帧判定路径不同。

## 目标

同一张卡在正常手持条件下连续扫描，输出分数稳定一致；误读优先表现为"空白/未识别"（可见失败）
而不是"判成别的选项"（沉默失败）。

## 方案（6 个单元）

### 1. 模板角标改为实心方块（TemplateGeometry + 双端渲染器）

- 四角在原 L 形括号相同的位置改画 `CORNER_MARKER_SIZE = 26f` 的实心方块
  （左上角仍在 `CORNER_BRACKET_MARGIN` 处，卡片外形与宽高比不变）。
- `TemplateGeometry` 新增 `cornerMarkerRects(layout)` 与 `cornerMarkerCenters(layout)`；
  方块中心是唯一无歧义参考点，从根上消除 outer/innerRight 摇摆。
- App `TemplateRenderer.drawCornerBrackets` 与测试端 `DesktopTemplateCardRenderer` 同步改画方块。
- L 形几何常量与 `cornerAnchorReference()` 保留，供旧卡回退路径使用。

### 2. 双路锚点检测（SolidCornerMarkerDetector 优先，CornerAnchorMatcher 回退）

- 新增 `SolidCornerMarkerDetector`（omr-core）：全局阈值二值化后找 fill ratio ≥ 0.85 的实心连通域，
  按象限取最靠角的候选，四点做四边形 + 期望宽高比校验；锚点取连通域**质心**（亚像素），
  `source = "solid-marker"`。
- 判别依据：方块 fill≈1.0 且尺寸 ~26 单位；L 括号 fill≈0.41；答案涂块（18×12.5）更小且在卡内部。
- `AndroidOmrEngine.scan`：先方块检测，失败回退现有 `CornerAnchorMatcher`，旧卡行为零变化。
- 下游按 `source` 选参考点：方块路径用 `cornerMarkerCenters`（单一参考）；
  L 路径保留现有 outer/innerRight 证据选择逻辑。

### 3. 透视变换替代双线性（PerspectiveMapping）

- 新增 `PerspectiveMapping`：由 4 组点对应解 3×3 homography（8×8 线性方程组，高斯消元），
  提供正向（模板→帧）与逆向（帧→模板）映射。
- 替换两处：`AndroidPaperCoordinateProjector.project`（正向）、
  `AndroidSolidMarkDetector.invert`（逆向，替代 Newton 迭代反解双线性）。
- `MiniProgramGridBuilder`/`MiniProgramGrid` 保留（几何校验与 legacy 读取路径不动）。

### 4. 最近中心匹配（AndroidSolidMarkDetector）

- `matchQuestionCell` / `matchAdmissionNumberCell` 改为：在候选格中取**中心距离最近**者；
  容差改为每轴不超过格间隙一半（选项横向 1.75、纵向 3；数字格横向 4）。
- 超出容差判不命中（空白），不再吞进相邻格。

### 5. 多帧共识（ScanConsensusTracker + ScanScreen 接入）

- `ScanConsensusTracker`（omr-core，纯 Kotlin）：
  - 签名 = 学号 + 逐题选项标签 + 分数，避免不同答题结果只因同分而被合并。
  - 最近 5 个合格帧中至少 4 帧一致才锁定；超过 2 秒的样本自动过期。
  - 一张卡锁定后，任何其他读数都不能覆盖；连续 900ms 检测不到角标才开始下一张。
  - 失败但仍检测到角标的帧不清空进度，也不会计入共识。
- `ScanScreen`：确认阶段只显示进度，不展示单帧分数；锁定后才显示答题结果、大字分数、
  保存记录并触发 TTS。检测到卡片移开后清空锁定展示。

### 6. 陀螺仪防抖门（DeviceStabilityMonitor + 分析器网关）

- 纯逻辑类 `StabilityEvaluator`（app，可单测）：输入 (时间戳, 角速度模长) 序列，
  角速度 < 0.15 rad/s 持续 300 ms 判稳定；超阈值立即判不稳。
- `DeviceStabilityMonitor`：SensorManager `TYPE_GYROSCOPE`，缺失时退化为始终稳定（不阻塞）。
- `AndroidOmrImageAnalyzer` 增加可选 `stabilityGate: () -> Boolean`：
  不稳帧计入 `droppedReason="unstable"` 直接丢弃（省 CPU）。
- ScanScreen：默认开 + "防抖"开关按钮；被门挡住时状态显示"手机晃动中…"。

## 不做（明确出范围）

- 分块自适应二值化（全局阈值保留）。
- 相机分析分辨率调整（维持 1280×960）。
- 删除 L 形识别路径或 legacy vision 路径。

## 测试

- `PerspectiveMapping`：4 角点精确映射、逆映射一致性、仿射退化情形。
- `SolidCornerMarkerDetector`：方块卡合成图（`DesktopTemplateCardRenderer` 方块模式）
  含透视扭曲 + 降采样 1280 的识别单测；与 L 卡图共存时的判别。
- 最近中心匹配：间隙中点、容差边界用例。
- `ScanConsensusTracker`：3 帧锁定、失败帧重置、换卡自动重锁、重复签名不重复触发。
- `StabilityEvaluator`：稳定窗口、抖动打断、传感器缺失。
- 回归：`DesktopWechatImageScanTest`（L 形回退路径）必须继续通过；
  复跑 `DesktopCameraInstabilityProbeTest` 对比方块卡改造前后的分数稳定性。

## 验证结果（实施后）

- L 形微信照片探针（legacy 回退路径 + 透视映射 + 最近中心匹配，`camera-instability-probe.txt`）：
  历史基线数值（ds1280 8/10、cornerShadow35 6/10、blur1+noise3 8/8/6/6/失败）来自在提交 407ad14（修复前代码）上运行同一探针的输出，探针使用固定种子、可确定性复现；
  现在 ds1280 改为 10.0/10.0，与全分辨率一致（Q11 D→C 翻转已消除）；
  cornerShadow35 由此前 6/10 提升为 10.0/10.0；blur1+noise3 五个种子中 3 个可见失败
  （`projected cell too small`，即拒绝而非误判；其余变体中另见 `invalid card geometry` 类可见拒绝），2 个成功且均为
  10.0/10.0（此前的部分错分模式已消除），blur/noise 各变体中无一例"判成别的选项"的沉默错分；
  但同一探针在 `ds1280 bright+40`（强过曝）行仍复现一例沉默错分（`true | 8.0/10.0 | 1233 | Q11=C`），
  legacy L 路径未完全消除该风险，详见下文"已知残留限制"。
- 方块卡稳定性回归（`SolidMarkerCardStabilityTest`）：1280 宽 + blur1 + noise3 × 8 种子,
  成功帧全部 10/10 且学号一致。
- 已知基线失败（与本次改动无关，改动前后均失败）：`AndroidOmrImageScanTest` 3 例、
  `CornerAnchorMatcherTest` 4 例。

## 已知残留限制

1. legacy L 路径在强过曝（bright+40 探针行）下仍可能沉默错分（参考候选歧义所致）；
   方块角标卡不受影响，旧卡逐步换印后自然消除。可选后续：outer/innerRight 两参考分歧时
   降级为可见失败。
2. 理论尾部风险（显式接受，待跟踪）：旧 L 卡上若恰有 ≥4 个大幅越框、近方形（长宽比 ≤1.35、
   fill ≥0.85）的涂块位于作答区四角极值处，可能被方块检测器抢先当作角标（表现为大量空白的
   可见异常分数，而非隐蔽错分）。按最终审查裁决记录在案。
