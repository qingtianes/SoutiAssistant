---
title: Current Tasks
description: Current tasks, blockers, verification state, and recommended next actions.
doc_type: task_state
status: active
created: 2026-08-16
updated: 2026-08-16
tags:
  - project-memory
  - tasks
  - current-state
audience:
  - agent
  - maintainer
related:
  - PROJECT_CONTEXT.md
  - DECISIONS.md
  - CHANGELOG_WORK.md
---

# Tasks

## Recommended Next Action
- 继续拆 FloatWindowService：扫描循环、视图构建、读屏小窗构建。

## Current
- [ ] 继续拆 FloatWindowService 扫描循环与 buildStandbyUi（高耦合，需专项谨慎处理）

## Verification
- 40 项单测通过；Lint 0 error；assembleDebug 成功（JDK 21）。
- 当前工作区干净。

## Blockers
- 无。

## Done
- [x] 建立接管分支与基线标签
- [x] 抽出 OcrQuestionProcessor、QuestionMatcher、QuestionRepository
- [x] 拆出 BankChunker、FileFormatDetector 与各解析器
- [x] 包结构归位：import / ocr / overlay / ui / model / repository
- [x] 浮窗/读屏互斥加固
- [x] FloatWindowService 拆出：FrameImageUtils、OverlayResultRenderer、ServiceNotificationHelper、OverlayDragResizer、ProjectionVirtualDisplayFactory、SearchUiBuilder、ScreenReadWindowBuilder
- [x] MainActivity 拆出：HomeScreen、ImportScreen、BankScreens
- [x] 安装 find-skills、project-memory、self-improving-agent
- [x] ponytail 实体迁移到 E 盘
