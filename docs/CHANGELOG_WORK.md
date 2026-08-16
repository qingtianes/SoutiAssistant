---
title: Work Changelog
description: Dated notes on changed files, deliverables, tooling, checks, and verification.
doc_type: work_log
status: active
created: 2026-08-16
updated: 2026-08-16
tags:
  - project-memory
  - changelog
  - work-log
  - verification
audience:
  - agent
  - maintainer
related:
  - PROJECT_CONTEXT.md
  - DECISIONS.md
  - TASKS.md
---

# Work Changelog

## 2026-08-16
- 建立安卓接管分支与基线标签。
- 抽出 OcrQuestionProcessor（OCR 文本处理）并新增测试。
- 抽出 QuestionMatcher（题目匹配评分）并新增 6 项测试，全量 37 项通过。
- 抽出 QuestionRepository（题库存取）。
- 拆出 BankChunker、FileFormatDetector 与 txt/docx/pdf/xls 四个解析器。
- 包结构归位：import / ocr / overlay / ui / model / repository。
- 浮窗/读屏互斥加固。
- FloatWindowService 拆出：FrameImageUtils、OverlayResultRenderer、ServiceNotificationHelper、OverlayDragResizer、ProjectionVirtualDisplayFactory。
- MainActivity 拆出：HomeScreen、ImportScreen、BankScreens。
- 安装技能：find-skills、project-memory、self-improving-agent。
- 将 ponytail 五个技能实体从 C 盘 .cc-switch 迁到 E:\Codex\CodexHome\skills，C 盘留 Junction。
- 初始化项目记忆文件并回填真实项目信息。

## 2026-08-16 浮窗输出框高度修复（本地未推送）
- 输出框不再设置 180dp 上限，按最高相关度最佳答案的真实内容高度自适应。
- 空结果时输出框完全隐藏。
- 缩放绿框时不再把输出框重置为固定 180dp。
- 向上显示模式下调整窗口 y 锚点，避免识别框漂移导致 OCR 扫错题。
- 保留备份分支 backup/pre-bugfix-20260816。

## 2026-08-16 浮窗输出框第二次试验（本地未推送）
- 恢复渲染全部匹配结果；可视高度按最佳答案卡片高度，其余答案可滚动查看。
- 统一窗口高度与锚点计算到 applyFloatWindowHeight，向上显示时底部固定、顶部伸缩。
- 40 项测试通过，Lint 无错误。
