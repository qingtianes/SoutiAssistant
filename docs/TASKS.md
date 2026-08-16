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
- 继续把 ocr / overlay / ui / model-repository 相关文件移入对应包，每搬一组就回归一次。

## Current
- [ ] 把 OcrBridge / OcrHelper / OcrQuestionProcessor 移入 ocr 包
- [ ] 把 FloatWindowService 移入 overlay 包
- [ ] 把 MainActivity 移入 ui 包
- [ ] 把模型、题库与匹配移入 model / repository 包
- [ ] 实现浮窗/读屏互斥状态机
- [ ] 拆解 FloatWindowService
- [ ] 拆解 MainActivity

## Verification
- 37 项单测通过；Lint 0 error；assembleDebug 成功（JDK 21）。
- 当前工作区干净，所有已完成的切片均已提交。

## Blockers
- 无。

## Done
- [x] 建立接管分支与基线标签
- [x] 抽出 OcrQuestionProcessor 并补测试
- [x] 抽出 QuestionMatcher 并补测试
- [x] 抽出 QuestionRepository（题库存取）
- [x] 拆出 BankChunker（Importer 切块核心）
- [x] 拆出 FileFormatDetector（格式识别）
- [x] 把 txt/docx/pdf/xls 解析器拆到独立文件
- [x] 把 import 相关文件移入 import 包
- [x] 安装 find-skills、project-memory、self-improving-agent
- [x] ponytail 实体迁移到 E 盘
