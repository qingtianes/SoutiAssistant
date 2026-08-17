---
title: Project Context
description: Stable project facts, structure, workflows, resources, and constraints.
doc_type: context
status: stable
created: 2026-08-16
updated: 2026-08-17
tags:
  - project-memory
  - context
  - durable-knowledge
audience:
  - agent
  - maintainer
  - workbuddy
related:
  - DECISIONS.md
  - TASKS.md
  - CHANGELOG_WORK.md
  - HANDOFF_TO_WORKBUDDY.md
---

# Project Context

## Overview
- 项目：SoutiAssistant（搜题助手）Android 客户端。
- 用途：浮窗搜题、读屏搜题、摄像头扫描搜题，实时 OCR 题目并在已勾选本地题库中匹配答案输出。
- Android 当前版本：v1.1.1（已发布 GitHub Release）。
- 当前状态：Android 主线已完成并发布；HarmonyOS 已作为本仓库 `HarmonyOS/` 子目录进入完整复刻阶段。

## Tech Stack
- Kotlin + Jetpack Compose（Material3，主页）+ 传统 View（悬浮窗）。
- Gradle 8.13，Android SDK 35，构建需 JDK 21。
- 本机 JDK 21 路径：E:\Huawei\DevEco Studio\jbr。
- 相机：CameraX 1.4.1；OCR：Google ML Kit 中文离线。
- 数据：SharedPreferences（JSON 题库 + souti_settings 设置）。

## Project Structure（v1.0.2 实际）
- `com.dingding.souti.model`：Bank、Question、SearchResult。
- `com.dingding.souti.repository`：QuestionBank、QuestionRepository、QuestionMatcher、SettingsStore、SettingsLogic。
- `com.dingding.souti.import`：Importer、FileFormatDetector、BankChunker、Txt/Docx/Pdf/Xls 解析器。
- `com.dingding.souti.ocr`：OcrBridge、OcrHelper、OcrQuestionProcessor。
- `com.dingding.souti.overlay`：FloatWindowService 及拆出的组件。
- `com.dingding.souti.ui`：MainActivity、HomeScreen、ImportScreen、BankScreens、ScanScreen、SettingsScreen、UsageGuideScreen。
- `HarmonyOS/`：鸿蒙独立 Stage/ArkTS 工程；其 `docs/PARITY_MATRIX.md` 是 Android→Harmony 复刻权威矩阵。

## FloatWindowService 已拆组件
- FrameImageUtils、OverlayResultRenderer、ServiceNotificationHelper、OverlayDragResizer、ProjectionVirtualDisplayFactory、SearchUiBuilder、ScreenReadWindowBuilder、StandbyUiBuilder、OutputWindowBuilder。

## Key Workflows
- 浮窗搜题：悬浮窗绿框实时 OCR 框内区域，单题答案输出到独立输出窗。
- 读屏搜题：全屏实时 OCR，多题答案按顺序输出到独立小窗。
- 扫描搜题：CameraX 实时取景 + 横向取景框 + 双指/按钮缩放，只识别框内，支持暂停/继续。
- 题库导入：支持 .txt/.docx/.pdf/.xls，不支持 .xlsx。
- 设置中心：权限管理 / 识别与匹配 / 浮窗显示 / 扫描搜题 / 通用关于。

## Verification
- 构建前设置 JAVA_HOME 为 E:\Huawei\DevEco Studio\jbr。
- 回归命令：gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon。
- 发布打包：gradlew.bat assembleRelease；输出 app-release.apk（后续规范命名为 SoutiAssistant-vX.Y.Z-release.apk）。
- 当前单测通过，Lint 0 error。

## Constraints
- 浮窗与读屏互斥，启动一个前必须先停另一个。
- OCR、截屏、摄像头帧仅本机处理，不上传；allowBackup=false。
- 未经用户明确允许，不 push。
- 鸿蒙重要里程碑在构建/验证/文档审查通过后提交并 push 到本仓库 main。
- WorkBuddy 重做 UI 时只改 `com.dingding.souti.ui` 的排版/样式，不改业务逻辑与 overlay 行为。
