# 低延迟手持相机识别设计

## 背景

当前相机链路将设备稳定性、AF/AE 收敛、整帧质量、450ms 候选帧窗口和 4/5 多帧共识串联。即使每一层都按设计工作，正常手持的理想锁定下限也约为 2.1 秒；当单次 OMR 接近 217ms 时，四个结果还可能无法在 2 秒共识窗口内同时存在。

参考小程序采用不同策略：陀螺仪只更新运动状态，不阻止正常识别；每 400ms 处理一次最新帧；同一考号和分数读到两次后确认。Android 需要保留更严格的完整答案签名与像素安全门，但不应让传感器状态反复清空识别进度。

## 目标

- 正常手持、答题卡完整进入画面后，锁定延迟 P95 不超过 1.5 秒。
- 完整签名仍包含考号、逐题选项和分数，误锁安全性不低于当前实现。
- 运动、连续对焦和曝光搜索不再冻结识别；真正模糊、过曝、欠曝或几何无效的帧继续可见失败。
- 限制 Y 平面转换频率和中间分配，避免持续扫描引发 GC 抖动。
- 保持 `omr-core` 无 Android/AndroidX 依赖，保留 legacy 扫描路径。

## 非目标

- 不调整答题卡模板、角标编码或 OMR 判格算法。
- 不删除 AF/AE 元数据采集、设备稳定性监视器或相机质量提示。
- 不改变相机分析分辨率 1280x960、CameraX `KEEP_ONLY_LATEST` 或 30fps 曝光策略。
- 不引入新的第三方依赖。

## 设计

### 1. 传感器与相机状态改为软信号

`DeviceStabilityMonitor` 继续驱动“请持稳手机”覆盖提示。`CaptureStateEvaluator` 继续解释 Camera2 AF/AE 状态。`AndroidOmrImageAnalyzer` 将两者写入调试信息，但不因 `deviceStable=false`、AF 扫描或 AE 搜索提前返回，也不清空候选进度。

安全判定由实际帧承担：整帧 `FrameQualityEvaluator(PRE_OMR)` 继续拒绝明显模糊和曝光异常；定位角标后，`FrameQualityEvaluator(CARD_ROI)` 与 OMR 几何校验继续负责最终硬拒绝。

### 2. 固定节奏处理最新帧

`ScanScreen` 使用：

- `minAnalyzeIntervalMs = 250L`
- `candidateWindowMs = 0L`

CameraX 仍使用 `STRATEGY_KEEP_ONLY_LATEST`。因此分析器最多约每秒处理四个最新帧，不为择优帧额外等待 450ms，也不会在 30fps 下对每个相机帧创建完整灰度图。

不增加固定启动预热。相机尚未稳定时，真实像素质量门会拒绝坏帧；首个合格帧可以立即进入 OMR，避免把一次性等待变成新的固定延迟。

### 3. 与处理节奏匹配的多帧共识

默认共识参数改为：

- `requiredFrames = 3`
- `windowSize = 4`
- `maxWindowDurationMs = 3_000L`
- `cardAbsentResetMs = 900L`（保持不变）

签名仍为考号、逐题选项和分数。失败但仍能看到卡片的帧不清空进度；卡片移开 900ms 后才允许下一张卡。3 秒窗口覆盖慢设备上 250ms 调度间隔加 OMR 耗时，避免结果在锁定前过期。

### 4. 单遍 Y 平面转换

`YPlaneFrameInput` 使用 JVM `ByteBuffer` 读取 Y 平面。转换器直接从带 stride/crop 的源缓冲区写入最终旋转后的 `IntArray`，不再依次创建：

1. 完整 Y `ByteArray`；
2. 裁剪 `IntArray`；
3. 旋转 `IntArray`。

无论旋转角度如何，每个被分析帧只分配最终 `MiniProgramFrame.pixels`。转换保持同步完成，`ImageProxy.close()` 前不会保留相机缓冲区引用。

### 5. 可观测性

分析结果调试信息增加：

- `deviceStable`
- `captureGateAccepted`
- `captureGateRejection`
- `frameAdapterElapsedMs`
- `frameQualityElapsedMs`
- `omrElapsedMs`
- `analyzerElapsedMs`

这些字段用于真机区分传感器波动、相机状态、帧转换、质量检查和 OMR 计算耗时，不参与业务判定。

## 数据流

CameraX 最新帧 → 250ms 节流 → 记录运动/AF/AE 软信号 → 单遍 Y 平面转换 → 整帧像素质量硬门 → OMR/卡片 ROI 质量与几何硬门 → 3/4 完整签名共识 → 展示、存档和播报。

## 错误处理

- 图像转换或 OMR 抛出的异常继续通过 `onError` 上报，并在 `finally` 关闭 `ImageProxy`。
- 模糊/曝光异常继续返回结构化 `RETAKE_BLUR`/`RETAKE_EXPOSURE`。
- 运动状态继续通过现有 UI 提示并写入调试信息；AF 扫描和 AE 搜索只写入调试信息。三者均不生成拒绝结果。
- 缺失运动传感器或捕获元数据时按现有降级规则继续识别。

## 验证

- 分析器测试证明设备不稳、AF 扫描和 AE 搜索仍会进入帧适配与 OMR，同时调试字段正确。
- 分析器测试证明模糊和曝光异常仍在 OMR 前被拒绝。
- 共识测试覆盖 3/4、失败帧容忍、3 秒慢节奏以及卡片移开重置。
- Y 平面测试覆盖 stride、crop、0/90/180/270 度旋转、无符号灰度和不改变输入缓冲区 position。
- 运行 `:omr-core:test`、相机相关 app 单测、`test` 和 `assembleDebug`。
