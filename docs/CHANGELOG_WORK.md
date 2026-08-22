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

## 2026-08-16 浮窗输出框独立悬浮窗（本地未推送）
- 输出结果从主浮窗拆出为独立悬浮窗，可拖动到任意位置。
- 主浮窗只保留标题栏、绿框、OCR 状态栏，不再因输出内容变化而漂移。
- 输出窗高度按最佳答案卡片高度 + 容器 padding + 卡片 bottomMargin 自适应。
- 40 项测试通过，Lint 无错误。

## 2026-08-16 输出窗标题栏常驻与拖动（本地未推送）
- 输出窗增加常驻标题栏“搜题结果”，拖动标题栏移动，内容区只滚动。
- 服务运行时输出窗常驻，空结果显示“暂无输出”。
- 移除主浮窗“向下/向上显示”开关，输出窗初始位于主浮窗下方，用户自由拖动。
- 40 项测试通过，Lint 无错误。


## 2026-08-22 Android 题库导入 BUG 修复（本地未推送）
- 修复 `BankChunker`：对“空行分隔 + 答案标记”格式使用题块优先策略；答案步骤中的数字编号不再生成伪题。
- 修复 `XlsBankParser`：移除 `stem.take(40)` 的错误去重；真实样例从错误的 432 条恢复为 435 条；支持换行选项导出；补充资源关闭。
- 修复 `PdfBankParser`：导入前调用 `PDFBoxResourceLoader.init(context.applicationContext)`；`InputStream` 与 `PDDocument` 使用 `use`；捕获 `Throwable` 并返回可见错误。
- 新增 `QuestionChunkParser`：把整题块正确拆为题干、选项、答案，导入后搜索字段不再混入选项与答案文本。
- `ImportScreen`：解析异常不再让页面永久停留在加载状态；题目结构化失败会给出明确提示。
- 新增回归测试：答案编号不误切题、题块结构化、XLS 选项分隔。
- 样例验证：TXT 432 题，XLS 435 题；PDF 已完成代码级修复，待设备实测中文字体显示。

## 2026-08-22 — Android v1.1.3 题库导入与匹配回归
- 修复 PDFBox 初始化、PDF 排版空行误切、判断题答案遗漏、同行选项未拆分。
- 修复 TXT 简答题编号步骤误切；XLS 多 Sheet、表头别名、乱序选项和同格选项解析。
- DOCX 保留普通/自闭合空段落边界；新增导入质量提示。
- 三种搜题统一使用结构化搜索，恢复简答题、乱序选项、答案片段与旧题库兼容。
- 修复 OCR 处理前压平换行导致读屏多题无法按题号拆分。
- 回归样例：TXT 432、XLS 435、PDF 723；新增格式、符号、旧数据与 OCR 单测。
