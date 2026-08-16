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
- [ ] 继续拆 FloatWindowService 扫描循环

## Verification
- 40 项单测通过；Lint 0 error；assembleDebug 成功（JDK 21）。
- 当前工作区干净。

## Blockers
- 无。

## Verification Note (2026-08-16)
- 模拟器烟测：浮窗搜题 OCR 循环正常，识别 19 字符，搜索到 2 条匹配；结论：功能未受影响。
- 截图方式：PowerShell 直接重定向会破坏 PNG，后续用 WriteAllBytes 或 Git Bash。

## Future Improvements
- [ ] OCR 识别框默认高度偏窄（约 115px），后续调宽以容纳题干+选项
- [ ] 考虑浮窗在 souti 自身页面时的让位/收起策略
- [ ] 复核浮窗顶栏答案标识与结果卡的一致性
- [ ] 复核 2 条匹配的渲染数量

## Done
- [x] 建立接管分支与基线标签
- [x] 抽出 OcrQuestionProcessor、QuestionMatcher、QuestionRepository
- [x] 拆出 BankChunker、FileFormatDetector 与各解析器
- [x] 包结构归位：import / ocr / overlay / ui / model / repository
- [x] 浮窗/读屏互斥加固
- [x] FloatWindowService 拆出：FrameImageUtils、OverlayResultRenderer、ServiceNotificationHelper、OverlayDragResizer、ProjectionVirtualDisplayFactory、SearchUiBuilder、ScreenReadWindowBuilder、StandbyUiBuilder
- [x] MainActivity 拆出：HomeScreen、ImportScreen、BankScreens
- [x] 安装 find-skills、project-memory、self-improving-agent
- [x] ponytail 实体迁移到 E 盘
