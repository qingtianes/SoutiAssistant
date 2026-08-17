---
title: Android to HarmonyOS Parity Matrix
description: Authoritative Android v1.0.2 to HarmonyOS replication inventory and evidence tracker.
doc_type: parity_matrix
status: active
created: 2026-08-17
updated: 2026-08-17
tags:
  - parity
  - android
  - harmonyos
  - migration
audience:
  - agent
  - maintainer
  - workbuddy
related:
  - PROJECT_CONTEXT.md
  - DECISIONS.md
  - TASKS.md
  - CHANGELOG_WORK.md
---

# Android → HarmonyOS 完整复刻矩阵

## 规则

- Android v1.0.2 当前代码是唯一产品标准；Android 的入口、功能、交互、数据和 UI 不得被鸿蒙版擅自删减。
- “已完成”必须同时有：源码实现、构建通过、自动/确定性验证证据；模拟器不能替代真机能力验证。
- 平台暂不支持时保留入口和状态说明，记录为“平台限制/待探针”，不得伪装为完成。
- 摄像头扫描最后开发；用户未明确允许前不得启动电脑或模拟器摄像头。

## 页面与产品结构

| Android 基准 | Harmony 目标 | 当前状态 | 证据/下一步 |
|---|---|---|---|
| `ui/HomeScreen.kt` | 完整首页、玻璃拟态、主题、入口和状态 | 未开始（当前是简化页） | 读取 Android UI 状态与尺寸，建立页面契约 |
| `ui/BankScreens.kt` | 题库总览、题库详情、启停/删除/数量 | 未开始 | 当前仅首页题库卡片；需独立页面 |
| `ui/ImportScreen.kt` | 智能导入入口和格式反馈 | 部分完成 | 当前仅 TXT；需对齐 Importer |
| `ui/ScanScreen.kt` | 摄像头扫描完整 UI | 冻结 | 用户要求最后开发，当前入口禁用 |
| `ui/SettingsScreen.kt` | 完整设置分类与生效状态 | 部分完成 | 当前仅版本/指南/隐私/清空 |
| `ui/UsageGuideScreen.kt` | 与 Android 流程一致的使用说明 | 部分完成 | 需更新为完整菜单和能力状态 |
| Android MainActivity 导航 | Harmony 导航与页面栈 | 部分完成 | 先建立所有入口，再接平台能力 |

## 题库与导入

| Android 基准 | Harmony 目标 | 当前状态 | 证据/下一步 |
|---|---|---|---|
| `import/Importer.kt` | 按格式分发的智能导入 | 未完成 | 建立统一 ImportResult 契约 |
| `import/FileFormatDetector.kt` | TXT/DOCX/PDF/XLS 检测 | 未完成 | Android 支持 `.txt/.docx/.pdf/.xls`；`.xlsx` 明确不支持 |
| `import/TxtBankParser.kt` | TXT 解析 | 已有部分实现 | 已验证导入/解析/搜索；需纳入统一导入架构 |
| `import/DocxBankParser.kt` | DOCX 解析 | 未开始 | 评估 ArkTS/原生可行实现 |
| `import/PdfBankParser.kt` | PDF 解析 | 未开始 | 评估本地文本提取能力 |
| `import/XlsBankParser.kt` | XLS 解析 | 未开始 | 评估依赖和兼容边界 |
| `import/BankChunker.kt` | 统一题目切块 | 部分完成 | 对齐 Android 边界、错误反馈和测试 |
| `repository/QuestionBank.kt` | 题库管理、启用、搜索 | 部分完成 | 已验证 TXT 场景；需扩展完整导入来源 |
| `repository/QuestionRepository.kt` | 题库/题目持久化 | 部分完成 | JSON 持久化已验证；需题库总览 UI |
| `repository/QuestionMatcher.kt` | 相关度匹配 | 部分完成 | 已验证手动搜索；需对齐 Android 评分语义 |

## OCR、浮窗与读屏

| Android 基准 | Harmony 目标 | 当前状态 | 证据/下一步 |
|---|---|---|---|
| `ocr/OcrHelper.kt` | 图片/屏幕 OCR 生命周期 | 部分完成 | 图片 OCR API 已接入；模拟器缺原生 OCR |
| `ocr/OcrQuestionProcessor.kt` | 多题切分、题干提取、去重 | 未完成 | 先对照 Android 纯逻辑实现 |
| `overlay/FloatWindowService.kt` | 浮窗搜题独立服务 | 未实现 | 保留入口；先做平台权限探针，普通构建不伪装可用 |
| `overlay/OutputWindowBuilder.kt` | 独立输出窗 | 未实现 | 需按 Android 组件拆分 |
| `overlay/OverlayResultRenderer.kt` | 结果渲染、最佳答案、滚动 | 未实现 | 复刻 Android 行为契约 |
| `overlay/ScreenReadWindowBuilder.kt` | 读屏输出窗 | 未实现 | 需先证明鸿蒙连续帧能力 |
| `overlay/ProjectionVirtualDisplayFactory.kt` | 屏幕采集帧 | 未实现 | 真机探针最后进行 |
| Android 浮窗/读屏互斥 | Harmony 模式互斥 | 未开始 | 必须共享状态锁，禁止并行采集 |

## 设置与通用能力

| Android 基准 | Harmony 目标 | 当前状态 | 证据/下一步 |
|---|---|---|---|
| 权限管理 | 相机/屏幕/悬浮窗状态 | 部分完成 | 重新按能力状态设计，不伪装系统权限 |
| 识别与匹配 | 最低分、分数/来源、OCR 节流等 | 未完成 | 读取 SettingsScreen.kt 和 SettingsStore |
| 浮窗显示 | 尺寸、扫描框、输出位置等 | 未完成 | 浮窗能力探针后实现 |
| 扫描搜题设置 | 比例、缩放、暂停等 | 冻结 | 摄像头最后开发 |
| 主题 | 深色/浅色 | 未完成 | 对照 Android Theme.kt 与 HomeScreen |
| 隐私 | 本机处理、备份策略 | 部分完成 | 继续保持本机处理与无敏感信息入库 |
| 版本/关于 | 版本历史、设备兼容性 | 部分完成 | 对齐 Android SettingsScreen |

## 证据等级

- `未开始`：没有实现。
- `部分完成`：存在实现，但尚未达到 Android 行为/UI 契约。
- `构建通过`：编译和打包通过，不代表功能完成。
- `模拟器验证`：在模拟器上通过，真机能力仍未证明。
- `待用户真机验收`：开发与自动验证完成，等待用户最终验收。
- `完成`：只有用户真机验收通过后才能使用。

## Android v1.0.2 页面契约

Android `ui/HomeScreen.kt` 的 `App()` 注册页面状态：`home / import / overview / bank / scan / settings / help`。Harmony 正式注册目前只有 `Index / SettingsPage / UsageGuidePage`；H1 必须先恢复完整页面边界，不能继续让 `Index.ets` 同时承担首页、题库、图片 OCR、搜索和结果显示。

### 首页契约

- 顶部：标题、副标题“浮窗 · 读屏 · 摄像头 · 本地题库”、右上角圆形使用说明按钮。
- 题库区：智能导入、题库总览。
- 快捷搜题：浮窗、读屏、扫描、AI 搜题占位卡。
- 浮窗卡片：权限、未勾选题库、运行/失败状态、启动/停止。
- 读屏卡片：全屏识别、多题顺序输出、运行状态和启动/停止。
- 设置区：独立设置卡片和五类能力摘要。
- 视觉：`#F7F7F7` 背景、白色圆角卡片、`#1D9E75` 主色、状态圆点、灰/绿/红状态色。

### 智能导入契约

Android 来源：`Importer.kt`, `FileFormatDetector.kt`, `Txt/Docx/Pdf/XlsBankParser.kt`, `BankChunker.kt`, `ImportScreen.kt`。

必须复刻：

- TXT、DOCX、文字版 PDF、XLS；XLSX 显示“不支持并建议另存为 XLS”。
- MIME + 扩展名联合检测和统一格式分发。
- 有序号题、无序号空行题、选项对齐题、章节过滤。
- 后台解析、进度、源文字数、解析文字数、覆盖率和低覆盖率拦截。
- 解析结果预览、题目数量、自定义题库名称、确认/取消、分类错误提示。

### 题库总览/详情契约

Android 来源：`BankScreens.kt`。

必须复刻：

- 独立总览页，手动导入/AI 导入标签。
- 右上角确认和已选数量；勾选参与搜索的题库。
- 题库名称、题目数量、导入时间、空状态。
- 点击进入详情；分享为 TXT；删除题库。
- 详情逐题显示题干、选项、答案；支持删除单题。

### 设置契约

Android 来源：`SettingsScreen.kt`, `SettingsStore.kt`, `SettingsLogic.kt`。

必须复刻：

- 权限：悬浮窗、摄像头、通知、录屏说明。
- 识别与匹配：最大结果 1/3/5/10、最低匹配分 0–100、识别速度快/标准/省电。
- 浮窗显示：结果字号小/中/大、来源与相关度开关、绿框默认大小小/中/大。
- 扫描：默认缩放 1.0x/1.5x/2.0x、取景框单行/双行。
- 通用/关于：版本、更新日志、隐私、GitHub 反馈、清空题库与设置、恢复默认、持久化。

### 浮窗搜题契约

Android 来源：`FloatWindowService.kt` 及 overlay 拆分组件。

必须复刻或以平台证据标记限制：

- 后台/前台生命周期、屏幕授权、绿框区域拖动缩放、坐标映射、持续帧 OCR。
- OCR 状态栏、独立答案输出窗、独立标题栏、拖动、内容滚动、最佳答案高度、自适应空状态。
- 启动/停止、最小化、错误状态、首页运行状态、设置联动。
- 与读屏模式互斥。

### 读屏搜题契约

Android 来源：`FloatWindowService.kt`, `ScreenReadWindowBuilder.kt`, `OcrQuestionProcessor.kt`, `ProjectionVirtualDisplayFactory.kt`。

必须复刻或以平台证据标记限制：

- 全屏持续帧、中文 OCR、文本标准化、多题切分、题干提取、去重和滚动防抖。
- 逐题搜索、按页面顺序输出、独立小窗、拖动、缩放、暂停、最小化、关闭。
- 首页状态和与浮窗模式互斥。

### 扫描搜题契约（H7，当前冻结）

Android 来源：`ScanScreen.kt`。

最终必须复刻：

- 上半预览/下半结果、FILL_CENTER、横向绿色取景框、只识别框内。
- 实时内存帧分析、OCR 节流、双指和 +/- 缩放、倍数显示。
- 暂停/继续和结果锁定、设置联动、页面销毁解绑相机。
- 用户明确操作后才申请权限和开始采集；不得进入页面即自动拍照。

## H0 审计结论

- 当前 Harmony 不是 Android 复刻版，而是图片 OCR + TXT 题库的单页原型。
- 题库基础存储和 TXT 场景是可复用资产，但不能代表智能导入或题库总览完成。
- 图片 OCR 是额外历史路径，不能替代 Android 首页和三种搜题模式。
- 浮窗只剩未注册静态实验源码；读屏没有实现；设置和使用说明严重不完整；扫描入口已按安全决定冻结。
- H1 从页面结构和 UI 组件基线开始，不从真机或摄像头开始。
