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
- 当前状态：功能主体已存在，正在进行只拆结构、不改功能的重构。

## Tech Stack
- Kotlin + Jetpack Compose（Material3）。
- Gradle 8.13，Android SDK 35，构建需 JDK 21。
- 本机 JDK 21 路径：E:\Huawei\DevEco Studio\jbr（Android Studio 自带 jbr 是 JDK 25，Kotlin 编译器无法解析，勿用）。

## Project Structure
- app/src/main/java/com/dingding/souti/ 下主要文件：
  - MainActivity.kt：主页、题库总览、导入页、题库详情、导航（约 701 行）。
  - FloatWindowService.kt：悬浮窗、读屏、录屏、OCR、结果渲染（约 2804 行，重构重点）。
  - Importer.kt：txt/docx/pdf/xls 解析。
  - QuestionBank.kt：题库存取、搜索、TXT 解析。
  - OcrBridge.kt / OcrHelper.kt：录屏授权会话与 OCR 触发。
  - OcrQuestionProcessor.kt / QuestionMatcher.kt：已抽出的纯逻辑组件。

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
