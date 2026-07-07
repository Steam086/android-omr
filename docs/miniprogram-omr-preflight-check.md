# 小程序识别引擎施工前检查

## Plan 自查

| 检查项 | 结论 | 修正建议 |
|---|---|---|
| 阶段是否拆得太大 | 阶段 2 原计划把基础网格和边界精修放得过近 | 阶段 2 只做基础虚拟网格，边界精修后移 |
| 是否缺少单元测试 | 每个算法阶段都有测试计划 | 阶段 11 增加 mapper 测试，阶段 12 增加 overlay 坐标映射测试 |
| 是否提前接入 CameraX | 没有 | CameraX 保持到阶段 10 |
| 是否提前修改 UI | 没有 | UI 保持到阶段 11/12 |
| 是否可能破坏旧链路 | 阶段 11 有风险 | 必须保留旧 `OmrScanner` fallback |
| 数据结构是否完整 | 主体完整，但阶段 2 需要 build result | 阶段 2 加 `MiniProgramGridBuildResult` |
| 算法迁移风险是否覆盖 | 主要风险已覆盖 | 额外关注当前角点 matcher 尚非 JS 完全等价 |
| 文件职责是否清楚 | 基本清楚 | 阶段 2 不引入 reader/engine/CameraX |
| 验收标准是否明确 | 基本明确 | 阶段 2 明确点数、四角贴合、行列单调、失败 reason |
| 回滚方式是否明确 | 阶段 2-10 清楚 | 阶段 11 保持单入口开关以便回滚 |

## JS 到 Kotlin 函数映射

| 小程序函数 / 逻辑 | 小程序关键词 | 原作用 | Kotlin 目标文件 | Kotlin 目标函数 | 当前状态 | 测试 | 阶段 | 迁移风险 |
|---|---|---|---|---|---|---|---|---|
| 中心区域均值 / 全局阈值 | `u(t)`, `thresholdOffset || -30` | 中心 20% 均值 + 偏移 | `MiniProgramGeometry.kt` | `centerMean`, `threshold` | 已实现 | 已有 | 已完成 | 不能与块内阈值混用 |
| `isQ` | `key: "isQ"` | 四角几何校验 | `MiniProgramGeometry.kt` | `isQuad` | 已实现 | 已有 | 已完成 | 容差不要随意改 |
| `findJiaoDian` | 四象限模板扫描 | 找 LU/LD/RU/RD 锚标 | `CornerAnchorMatcher.kt` | `findAnchors` | 部分实现 | 部分已有 | 后续复核 | 当前不是 JS 完全等价 |
| LU/LD/RU/RD 模板 | `binSize.width > 288 ? ... : ...` | 22 点黑白模板 | `CornerAnchorMatcher.kt` | `template` | 部分实现 | 部分已有 | 后续复核 | 大/小图分支不能简化 |
| 四角候选组合筛选 | `LU x LD x RU x RD` + `isQ` | 组合并选最佳四边形 | `CornerAnchorMatcher.kt` | `findAnchors` | 已有基础版 | 已有 | 已完成/后续增强 | 失败原因和候选统计不足 |
| 虚拟网格生成 | `getPoints`, `this.points = K` | 原图坐标点阵 | `MiniProgramGridBuilder.kt` | `build` | 未实现 | 无 | 阶段 2 | row/column 容易写反 |
| 左右边界精修 | `C(...)`, `B(...)` | 修正左右边界 | `MiniProgramGridBuilder.kt` | 后续函数 | 未实现 | 无 | 阶段 2.5/后续 | 不要塞进基础网格阶段 |
| 单格读取 `k(...)` | `function k(t,r,n,e...)` | 判断一个格子是否涂黑 | `MiniProgramBubbleReader.kt` | `read` | 未实现 | 无 | 阶段 3 | 裁剪宽高和黑白值易错 |
| 块内阈值 | `blackRatio`, `rangePercent` | 单格自适应阈值 | `MiniProgramBubbleReader.kt` | `read` | 未实现 | 无 | 阶段 3 | 排序方向是降序 |
| 边缘污染清理 | `LEFT/RIGHT/UP/DOWN` | 清除表格边线污染 | `MiniProgramBubbleReader.kt` | `clearEdges` | 未实现 | 无 | 阶段 3 | 不能统一裁边代替 |
| BFS 连通域清理 | `function y(r,n,e)` | 清理小黑噪点 | `MiniProgramConnectedComponentCleaner.kt` | `clean` | 未实现 | 无 | 阶段 4 | `0=黑`, `1=白` |
| `getSize` | `exports.getSize = h` | 计算行列布局 | `MiniProgramTemplateSize.kt` | `fromTemplate` | 未实现 | 无 | 阶段 5 | JS 的 width/height 命名反直觉 |
| 准考证号读取 | `readZkzh` | 10 行 x N 列取最黑数字 | `MiniProgramAdmissionNumberReader.kt` | `read` | 未实现 | 无 | 阶段 6 | `9-i` 上下索引易反 |
| 答题区读取 | `readBody` | 读取每题选项 | `MiniProgramAnswerAreaReader.kt` | `read` | 未实现 | 无 | 阶段 7 | 跨列映射易错 |
| 评分逻辑 | `function o(...)`, `getScore` | 单选、多选、部分分 | `MiniProgramScoreCalculator.kt` | `calculate` | 未实现 | 无 | 阶段 8 | Android 当前题型字段不足 |

## 当前代码基线

- 旧识别链路仍完整。
- 新识别链路还没有接 UI。
- 新识别链路还没有接 CameraX。
- 第一阶段只完成灰度帧、几何阈值、四角锚标检测基础能力。
- 下一阶段最多新增 `MiniProgramGrid.kt`、`MiniProgramCell.kt`、`MiniProgramGridBuilder.kt`、`MiniProgramGridBuilderTest.kt`。
- 下一阶段绝对不碰 `ui/`、`camera/`、`vision/`、`grading/`、`template/TemplateGeometry.kt`、`template/TemplateRenderer.kt`。

## 阶段 2 开工 Prompt

```text
开始阶段 2：只执行“虚拟网格生成”。

严格限制：
1. 不接 CameraX。
2. 不改 UI。
3. 不做单格读取 k(...)。
4. 不做 BFS 去噪。
5. 不做准考证号读取。
6. 不做答案区读取。
7. 不做评分。
8. 不实现 getSize。
9. 不做左右边界精修。
10. 不删除、不替换、不修改旧 vision 识别链路。

本阶段只允许新增：
- MiniProgramGrid.kt
- MiniProgramCell.kt
- MiniProgramGridBuilder.kt
- MiniProgramGridBuilderTest.kt

目标：
根据已有 MiniProgramAnchors / LU、LD、RU、RD 四角点，生成基础虚拟网格点阵和 cell。

请先写 MiniProgramGridBuilderTest，再实现最小代码，通过相关单测后再汇报。
```
