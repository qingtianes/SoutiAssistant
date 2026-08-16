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
- 项目：SoutiAssistant HarmonyOS（鸿蒙版搜题助手）。
- 用途：用户主动选择图片 → 官方 OCR → 本地题库搜索答案。
- 当前状态：本地题库闭环已完成，真机验证未做。

## Tech Stack
- ArkTS + Stage 模型。
- DevEco Studio 6.1.1.300。
- SDK API 24，项目 target/compatible 6.1.0(23)。
- 本机 SDK：E:\Huawei\DevEco Studio\sdk\default。

## Structure
- entry/src/main/ets/ui/Index.ets：主页面（图片 OCR + 题库管理）。
- entry/src/main/ets/overlay/FloatPage.ets：实验性悬浮窗 Demo。
- entry/src/main/ets/overlay/FloatWindowManager.ets。
- entry/src/main/ets/model/QuestionBankModels.ets：题库模型。
- entry/src/main/ets/repository/：QuestionRepository。
- entry/src/main/ets/service/：QuestionBank、QuestionMatcher。
- entry/src/main/ets/camera/：CameraHelper。
- entry/src/main/ets/importer/TextBankImportService.ets：TXT 文件选择读取。
- entry/src/main/ets/ocr/OcrService.ets：官方 textRecognition OCR。
- entry/src/main/ets/picker/ImagePickerService.ets：PhotoViewPicker 选图。

## Verification
- 构建前设置 NODE_HOME / JAVA_HOME / DEVECO_SDK_HOME 到 DevEco 目录。
- 构建命令：hvigorw.bat assembleHap --mode module -p product=default -p module=entry@default -p buildMode=debug --no-daemon。
- 当前构建成功，未签名 HAP 可生成。

## Constraints
- 真机 OCR 能力需验证：SystemCapability.AI.OCR.TextRecognition、中文识别、相册 URI 读取。
- 签名由 DevEco 自动签名或用户配置。
