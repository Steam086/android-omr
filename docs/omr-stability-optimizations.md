# 相机扫描稳定性优化：方块角标 + 透视映射 + 多帧共识 + 防抖门

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

- 新增 `ScanConsensusTracker(requiredFrames = 3)`（omr-core，纯 Kotlin）：
  - 签名 = 学号 + 逐题选项标签（比只比分数更严格）。
  - 仅成功帧参与；连续 3 帧签名一致 → 触发一次锁定回调。
  - 已锁定签名重复出现不再触发；**不同**签名连续 3 帧一致 → 自动锁定下一张（整叠连扫）。
  - 失败帧重置连续计数，不影响已锁定状态。
- `ScanScreen`：锁定时大字显示分数 → `recordStore.saveRecord` → `ScoreSpeaker` TTS 播报，
  每张卡一次；替换现有 `lastHandledKey` 首帧即采信逻辑。

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
