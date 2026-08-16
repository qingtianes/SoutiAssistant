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
- 整理 Android 包结构（model/repository/import/ocr/overlay/ui），先搬文件、保编译和测试通过。

## Current
- [ ] 整理 Android 包结构
- [ ] 继续拆分 Importer：格式识别与各解析器独立
- [ ] 实现浮窗/读屏互斥状态机
- [ ] 拆解 FloatWindowService
- [ ] 拆解 MainActivity

## Verification
- 37 项单测通过；Lint 0 error；assembleDebug 成功（JDK 21）。
- 工作区待提交：QuestionMatcher、QuestionRepository 及测试。

## Blockers
- 无。

## Done
- [x] 建立接管分支与基线标签
- [x] 抽出 OcrQuestionProcessor 并补测试
- [x] 抽出 QuestionMatcher 并补测试
- [x] 抽出 QuestionRepository（题库存取）
- [x] 拆出 BankChunker（Importer 切块核心）
- [x] 安装 find-skills、project-memory、self-improving-agent
- [x] ponytail 实体迁移到 E 盘
