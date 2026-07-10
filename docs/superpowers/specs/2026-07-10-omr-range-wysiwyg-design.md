# OMR 识别距离与所见即所得设计

日期：2026-07-10

## 背景

当前相机扫描存在明显的距离甜点区：太远时卡片单元格低于固定像素门槛，太近时角标或必读区域离开分析帧。`Preview` 与 `ImageAnalysis` 又没有通过统一 `ViewPort` 绑定，用户看到的取景内容不一定与分析器收到的 `ImageProxy.cropRect` 对应。扫描页底部状态面板还覆盖了部分预览，使用户无法凭画面判断真正的安全取景范围。

本设计分 P0、P1、P2 实施：P0 先保证取景真实且符合直觉，P1 扩展远距离信息量，P2 在失败闭合前提下扩展近距离并改善对焦。

## 目标

- 扫描页保持竖屏，展示完整 3:4 相机视野，不用铺满屏幕后隐藏裁切。
- `Preview` 与 `ImageAnalysis` 使用同一个传感器区域；预览中可见的有效区域必须全部进入分析 crop。
- 扫描中的状态面板不覆盖有效取景区域。
- 默认带考号模板不再要求整卡宽度至少占 1280 分析帧的约 68% 才能进入判格。
- 设备支持时优先使用 1920×1440 分析流；不支持时安全回退并报告实际分辨率。
- 允许纸张空白边缘或最多一个编码角标区域轻微离开画面，但所有需要评分的答案格和考号格必须完整位于分析帧内。
- 保持失败闭合：任何不满足信息量、几何、清晰度或必读区域完整性的帧都必须 `score=null`。
- 不删除 legacy 扫描路径，不给 `omr-core` 引入 Android 或 AndroidX 依赖。
- 所有 Android 构建和测试只在 Fedora 的现有 JDK 21/Android SDK 环境运行；本机不下载 SDK。

## 非目标

- 不改变编码角标的 6×6 码字或现有打印模板几何。
- 不通过数字变焦伪造更远识别距离。
- 不允许两个及以上编码角标缺失时继续评分。
- 不以降低错误拒绝率为代价接受错误分数。
- 不在本轮重写连通域扫描器或引入 OpenCV 扫描实现。

## 方案比较

### 方案 A：只提高分析分辨率

优点是改动小，同距离下角标和答题格获得更多像素。缺点是 14×14 原始采样门槛仍然存在，60 题模板的有效距离窗口仍很窄；更大的整帧连通域扫描还会增加延迟和内存压力。该方案只能作为 P1 的一部分。

### 方案 B：只降低 14 px 门槛

优点是合成小卡片更容易进入读取器。缺点是现有读取器按原始像素数量采样，边缘清理和连通域阈值也依赖该尺寸；直接把 14 改成 8 会在没有归一化和回归证据的情况下扩大错误成功风险。该方案不采用。

### 方案 C：统一取景几何、提高输入分辨率并归一化单元格采样

P0 用共享 `ViewPort` 消除所见与分析不一致；P1 请求更高分辨率，同时把每个投影格透视采样到固定网格，并以原始信息量而不是输出网格大小做安全门；P2 用“所有必读格完整”代替“所有角标中心必须在边界内”，再增加卡片区域对焦。该方案同时处理近端、远端和用户体验，是本设计采用的方案。

## P0：所见即所得取景

### 相机区域

扫描页继续由 Manifest 固定为竖屏。相机有效视图使用宽高比 3:4，宽度优先铺满可用屏幕，剩余空间显示状态和结果。3:4 对应 4:3 分析帧旋转后的显示比例，可以保留默认后摄的完整横向视场，避免把横向答题卡裁进细长的全屏竖向 ViewPort。

`PreviewView` 使用 `COMPATIBLE` 与 `FILL_CENTER`。因为 `PreviewView` 本身已经是 3:4，4:3 相机流旋转后不会发生有意义的隐藏裁切；边缘设备差异由统一 `ViewPort` 同步到分析流。

`ScanStatusPanel` 从相机 `Box` 的覆盖层移到相机区域下方。扫描中只显示一行状态、声音状态和操作提示；答题结果模板只在锁定后显示，且仍不覆盖相机有效区域。

### UseCase 绑定

`CameraPreview` 等待 `PreviewView` 附着且宽高非零后再构建会话。`Preview` 与 `ImageAnalysis` 使用同一个 `targetRotation`，加入同一个 `UseCaseGroup`，并设置 `previewView.viewPort`：

```kotlin
val rotation = previewView.display.rotation
val preview = Preview.Builder()
    .setTargetRotation(rotation)
    .build()
val analysis = ImageAnalysis.Builder()
    .setTargetRotation(rotation)
    .setResolutionSelector(resolutionSelector)
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()
val viewPort = requireNotNull(previewView.viewPort)
val group = UseCaseGroup.Builder()
    .setViewPort(viewPort)
    .addUseCase(preview)
    .addUseCase(analysis)
    .build()
cameraProvider.bindToLifecycle(lifecycleOwner, selector, group)
```

`CameraImageProxyFrameAdapter` 继续以 `ImageProxy.cropRect` 为唯一分析裁切来源，不增加第二个隐藏 ROI。方向模式只改变 `MiniProgramFrame` 的内部旋转，不改变 crop 内容。

### 取景引导

相机区域上绘制与当前模板宽高比一致的细线框。引导框表示推荐卡片位置，不是隐藏识别裁切：算法仍扫描共享 ViewPort 的完整 crop。引导框四角保留安全内边距，并随模板题量变化。

提示文案按结构化拒绝原因区分：

- `RETAKE_CELL_SIZE`：`请靠近一些，让答题卡更清晰`
- `RETAKE_CARD_CLIPPED`：`请稍微远离，确保答题区域完整`
- `RETAKE_CODED_MARKERS`：`请将至少三个清晰角标放入画面`
- `RETAKE_BLUR`：`画面模糊，请持稳或轻触卡片对焦`
- `RETAKE_EXPOSURE`：保留调整光线提示

### 可观测性

每个被处理帧继续记录 `ImageProxy`、cropRect 和旋转，并新增：

- `actualCropResolution`
- `cropAspectRatio`
- `cardWidthRatio`、`cardHeightRatio`、`cardAreaRatio`
- `minAnswerSourceCell`、`minAdmissionSourceCell`
- `requiredCellsInsideFrame`
- `requestedAnalysisResolution`

调试构建可显示这些字段；正式 UI 只显示可操作提示。

## P1：远距离识别

### 分辨率策略

分析流首选 1920×1440，使用 CameraX 的 closest-higher-then-lower 回退。最终分辨率必须从实际 `ImageProxy` 记录，不能把请求标签当作实际值。

低于 1280×960 的实际 crop 不直接崩溃，但返回新的 `RETAKE_LOW_RESOLUTION`，提示设备分辨率不足或需要靠近。1280×960 保持兼容，1920×1440 提供更宽距离。

保持每 250ms 处理最新帧、`KEEP_ONLY_LATEST` 和单线程 analyzer，避免高分辨率下堆积帧。

### 固定网格判格

`MiniProgramBubbleReader` 不再把投影格的原始像素宽高直接作为采样网格，也不再要求原始宽高都达到 14。每个投影格使用双线性插值归一化为 16×16 灰度网格，现有二值化、边缘清理、连通域和中心区域逻辑在该固定网格上运行。

原始信息量仍有独立硬门：

- 投影宽度至少 10 px；
- 投影高度至少 8 px；
- 投影面积至少 80 px²；
- 四个投影点全部位于帧内，并额外保留 1 px 插值边距。

答案格和考号格使用相同的原始信息量底线，避免考号格 10 单位高度继续强制全卡缩放到 1.4。固定网格不会创造新信息；原始门槛负责拒绝低信息帧，16×16 只让读取逻辑不依赖相机距离。

`AndroidOmrEngine` 的 `ProjectedCellSizeValidation` 改为输出宽、高、面积和完整性，不再复用 `MiniProgramBubbleReader` 的输出采样尺寸。

### 质量门

角标定位前的整帧 PRE_OMR 检查继续硬拒绝严重过曝和欠曝，但整帧 Laplacian 过低只作为诊断，不再提前返回。角标定位后的卡片 ROI 清晰度检查继续作为最终模糊硬门。这样远处的小卡片不会因为背景稀释整帧清晰度而被错误拦截。

### 角标诊断

编码角标的 12 px 最小边长保持不变。诊断增加尺寸过滤前候选数、尺寸过滤后候选数、解码候选数、选中角标边长和每个 ID 的拒绝阶段，用于确认真机远端失败到底来自角标还是答题格。

## P2：近距离与对焦

### 必读区域完整性

当前 `anchors touch frame border` 规则改为两层：

1. 角标定位仍要求至少三个唯一编码角标，解码、方向、重投影和宽高比全部通过；两个及以上角标缺失时失败。
2. 透视投影后，所有当前模板实际需要读取的答案格和考号格都必须完整位于帧内并满足 1 px 插值边距。

如果只有纸张空白边缘或一个已由三角标可靠推算的角标区域离开画面，但全部必读格完整，则允许继续。任何必读格裁切都返回 `RETAKE_CARD_CLIPPED` 且 `score=null`。

卡片最小宽、高、面积、宽高比、内部亮度/对比度和卡片 ROI 质量门保持。legacy 模式继续使用原有严格角标边界规则；放宽只适用于 `CODED_ONLY` 且存在可靠三角标映射的路径。

### 对焦与测光

绑定相机后，在预览中心创建覆盖引导框主要区域的 AF/AE MeteringPoint，触发一次对焦测光。用户轻触预览时，在触点重新发起 AF/AE，并在约 3 秒后回到连续自动对焦。AF/AE 状态继续只作诊断，最终是否接受由实际像素质量决定。

预览上显示短暂的对焦圆环和 `轻触卡片可对焦` 提示；不增加持久设置项。

## 数据流

```text
3:4 PreviewView
  -> PreviewView.viewPort
  -> UseCaseGroup(Preview + ImageAnalysis)
  -> ImageProxy.cropRect（与预览同一传感器区域）
  -> Y 平面裁切/内部方向旋转
  -> 曝光预检查；整帧模糊仅诊断
  -> 至少三个编码角标 + 透视映射
  -> 卡片 ROI 质量与几何检查
  -> 所有必读格完整性/原始信息量检查
  -> 每格双线性归一化到 16×16
  -> 判格、评分、3/4 多帧共识
  -> 状态区展示/存档/播报
```

## 错误处理

- `PreviewView.viewPort` 尚不可用时不绑定不完整会话；等待下一次 layout 回调。
- CameraX 无法绑定首选分辨率时由 ResolutionStrategy 回退；最终实际 crop 低于最低规格时返回结构化拒绝。
- ViewPort 或相机重建时清空捕获元数据与共识，避免跨几何配置合并结果。
- 帧转换、投影或读取异常继续通过 `onError` 上报，并在 `finally` 关闭 `ImageProxy`。
- 所有新拒绝结果必须满足 `success=false`、`score=null`。

## 测试策略

### 纯 JVM / Robolectric

- ViewPort 配置辅助逻辑：竖屏有效区域为 3:4，状态面板不占取景区域。
- Y 平面非全尺寸 crop + 0/90/180/270 度旋转保持准确。
- 固定 16×16 双线性采样在 10×8、14×10、18×13 和透视四边形上保持正确标记结果。
- 低于 10×8 或 80 px² 的格子失败闭合。
- 必读格完整而一个推算角标越界时编码路径可继续；任一必读格越界时返回 `RETAKE_CARD_CLIPPED`。
- 1、15、16、30、45、60 题，带考号/无考号，按多个缩放级别覆盖远近边界。
- 整帧低 Laplacian 不再早退，但卡片 ROI 模糊仍返回 `RETAKE_BLUR`。
- 所有新提示映射到明确中文操作。

### Fedora 构建

在 `/data/Sources/android-omr` 使用：

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME=/data/local/android-sdk
export ANDROID_SDK_ROOT=/data/local/android-sdk
sh gradlew :omr-core:test
sh gradlew :app:testDebugUnitTest
sh gradlew test
sh gradlew assembleDebug
```

### 真机验收

- 至少两台后摄能力不同的 Android 设备。
- 保存同一时刻的预览截图和分析 crop，确认四角内容一致。
- 将分析角标映射到 PreviewView，覆盖误差不超过 3 dp。
- 默认 15 题带考号卡在预览宽度约 40%～90% 区间可稳定得到正确结果。
- 60 题带考号卡在预览高度约 50%～90% 区间可稳定得到正确结果。
- 近距离只裁纸张空白边缘时允许识别；裁到任一答案格或考号格时必须提示远离且不输出分数。
- 清晰、正确曝光帧从完整进入引导框到锁定的 P95 不超过 1.5 秒。
- 连续扫描 10 分钟无持续积压、预览冻结或明显热降频导致的错误成功。

## 实施顺序与提交边界

1. P0：共享 ViewPort、3:4 无遮挡布局、引导框、实际 crop 诊断。
2. P1a：1920×1440 首选和低分辨率结构化拒绝。
3. P1b：固定网格双线性判格、原始信息量门、远距离缩放回归。
4. P1c：整帧模糊软化、卡片 ROI 硬门和角标诊断。
5. P2a：编码路径必读区域完整性替代角标边界规则。
6. P2b：中心/点击 AF-AE、近距离提示和最终 UI 回归。
7. Fedora 全量测试、APK 构建和真机验收记录。

每一步独立测试和提交；如果高分辨率导致 P95 超限，优先降低分析节奏或优化扫描分配，不回退所见即所得和失败闭合要求。
