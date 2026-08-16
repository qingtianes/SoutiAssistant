---
title: Project Context
description: Stable project facts, structure, workflows, resources, and constraints.
doc_type: context
status: stable
created: 2026-08-16
updated: 2026-08-16
tags:
  - project-memory
  - context
  - durable-knowledge
audience:
  - agent
  - maintainer
related:
  - DECISIONS.md
  - TASKS.md
  - CHANGELOG_WORK.md
---

# Project Context

## Overview
- 项目：SoutiAssistant（搜题助手）Android 客户端。
- 用途：通过悬浮窗识别框、读屏全屏识别两种方式，实时 OCR 屏幕题目，在已勾选的自定义题库中匹配答案输出；摄像头扫描搜题待开发。
- 使用场景：考试练习，单题作答与滑动多题作答。
- 当前状态：功能主体已存在，正在做只拆结构、不改功能的重构。

## Tech Stack
- Kotlin + Jetpack Compose（Material3）。
- Gradle 8.13，Android SDK 35，构建需 JDK 21。
- 本机 JDK 21 路径：E:\Huawei\DevEco Studio\jbr（Android Studio 自带 jbr 是 JDK 25，Kotlin 编译器无法解析，勿用）。

## Project Structure
- `com.dingding.souti.model`：Bank、Question、SearchResult。
- `com.dingding.souti.repository`：QuestionBank、QuestionRepository、QuestionMatcher。
- `com.dingding.souti.import`：Importer、FileFormatDetector、BankChunker、Txt/Docx/Pdf/Xls 解析器。
- `com.dingding.souti.ocr`：OcrBridge、OcrHelper、OcrQuestionProcessor。
- `com.dingding.souti.overlay`：FloatWindowService 及已拆出的组件。
- `com.dingding.souti.ui`：MainActivity、HomeScreen、ImportScreen、BankScreens。

## FloatWindowService 已拆组件
- FrameImageUtils：帧差异、亮度、反色。
- OverlayResultRenderer：结果卡片渲染。
- ServiceNotificationHelper：前台通知。
- OverlayDragResizer：读屏小窗拖拽/缩放。
- ProjectionVirtualDisplayFactory：VirtualDisplay + ImageReader 创建。
- SearchUiBuilder：搜题界面构建。
- ScreenReadWindowBuilder：读屏小窗构建。

## Key Workflows
- 浮窗搜题：悬浮窗绿框实时 OCR 框内区域，单题答案输出。
- 读屏搜题：全屏实时 OCR，多题答案按顺序输出到独立小窗。
- 扫描搜题：摄像头扫描，未完成。
- 题库导入：支持 .txt/.docx/.pdf/.xls，不支持 .xlsx。

## Verification
- 构建前设置 JAVA_HOME 为 E:\Huawei\DevEco Studio\jbr。
- 回归命令：gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon。
- 当前单测 37 项通过，Lint 0 error，APK 可生成。

## Constraints
- 重构只拆结构，不改变现有功能行为。
- 未经用户明确允许，不 push。
- 不修改 CODEX_HOME；文件尽量放 E 盘。
