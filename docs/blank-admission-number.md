# 空学号答题卡：正常判分，不再拒识

## 需求

带表头模板下，学生未涂（或漏涂某位）准考证号时，此前整卡被拒识
（`admission number failed: admission number contains blank digit`），分数无法读出。
现改为：空位不再构成整卡失败，答案照常识别并计分；空位在结果中以 `?` 占位
（全空为 `????`，漏涂一位如 `?878`）。

## 行为变化（omr-core）

- `AndroidAdmissionNumberReader`：`digitResults.any { isBlank }` 不再产生结果级
  `failureReason`；`success=true`，`digits` 保留 `?` 占位。
  结构性失败不变：候选不足仍返回 `admission number has incomplete digit candidates`，
  映射越界等仍拒识。
- `AndroidOmrEngine`：扫描整体 `success` 不再受空学号影响；若存在空位，
  在 `warnings` 中追加 `admission number contains blank digit` 以保留可观测性。

## 行为变化（app）

- `ScanScreen` 无需改动：`"????".isNotBlank()` 本就满足保存门槛，记录以占位串存档
  （记录里 `????` 表示学生未填，与无表头模板的空串语义区分）。
- `ScoreSpeechText`：考号含 `?` 时不播报考号，只播报得分。

## 取舍与已知限制

- 此前“准考证号全空即拒识”兼职承担了带表头卡的倒置（180°）兜底：四角定位标 180°
  中心对称，引擎无方向消歧。放开后，倒置的有表头卡会被读成“空学号 + 错误分数”而不被拒。
  桌面渲染实验（JVM 渲染卡翻转 180° 后扫描）确认：`success=true, digits=????, score=0`。
  所有卡片（无论有无表头）均需正立扫描。
- 同分且同为空学号的多张卡片会命中 `ScanScreen` 的去重键（`????:score`），
  连续扫描时第二张不会重复保存；此前该场景直接拒识，无此问题。补录考号可避免。

## 验证

- `omr-core`：`DesktopHeaderlessTemplateScanTest.scansHeaderCardWithBlankAdmissionNumber`
  （JVM 渲染有表头卡、只涂答案不涂学号，断言成功、得分与 `????`）。
- `app`：`AndroidAdmissionNumberReaderTest`（部分空/全空均成功且带占位）、
  `AndroidOmrEngineTest.scansCardWithBlankAdmissionNumberAsSuccess`、
  `ScoreSpeechTextTest.omitsExamIdWithBlankPlaceholderDigits`。
- 本机 Robolectric 原生图形测试仍有 8 个存量失败（字体/Skia 差异，含 180° 倒置样张用例），
  与本改动无关：stash 前后失败集合一致。
