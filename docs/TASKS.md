---
title: Current Tasks
description: Current tasks, blockers, verification state, and recommended next actions.
doc_type: task_state
status: active
created: 2026-08-16
updated: 2026-08-22
tags:
  - project-memory
  - tasks
  - current-state
audience:
  - agent
  - maintainer
  - workbuddy
related:
  - PROJECT_CONTEXT.md
  - DECISIONS.md
  - CHANGELOG_WORK.md
  - HANDOFF_TO_WORKBUDDY.md
---

# Tasks

## Current State
- Android v1.1.3 题库导入与三种搜题匹配回归已完成代码修复。
- 真实样例验证：TXT 432 题、XLS 435 题、文字版 PDF 723 题；PDF 判断题答案 163 条已从题干标记正确提取。
- 自动测试、Lint、Debug/Release 构建完成后发布；真机验收仍由用户安装 Release APK 后进行。

## Recommended Next Action
1. 用户在真机分别导入 TXT/XLS/PDF 样例，抽查简答题、乱序选项、判断题和公式符号。
2. 记录扫描版 PDF、图片题和复杂公式的真实样例，为后续可选 AI 导入方案提供依据。

## Verification
- 单元测试覆盖 TXT/XLS/PDF/DOCX、旧数据兼容、数学符号和三种 OCR 共用查询流程。
- 发布包命名：`SoutiAssistant-v1.1.3-release.apk`。

## Blockers
- 扫描版 PDF、图片题和复杂 Office 公式无法由纯文本解析保证完整性；本版本明确提示并保留导入后抽查。

## Done
- [x] 浮窗搜题 / 读屏搜题 / 扫描搜题
- [x] 题库导入与智能匹配
- [x] 设置中心 5 大类
- [x] 扫描搜题暂停/继续
- [x] 新图标替换
- [x] 主页 B 极简卡片流 + 题库区置顶 + 右上角 ⓘ 使用说明
- [x] App 内使用说明与 README 使用说明
- [x] PRD / DESIGN / ACCEPTANCE / SECURITY / FINAL_AUDIT 文档

## 2026-08-22 题库导入 BUG 修复
- [x] TXT：空行分隔且带答案标记的题块优先，避免答案中的 `1、2、3、` 被误切成伪题。
- [x] XLS：移除前 40 字错误去重，保留真实 435 条题目；补充换行选项分隔。
- [x] PDF：初始化 `PDFBoxResourceLoader`，使用资源安全关闭，解析异常不再静默卡在“正在解析”。
- [x] 导入结果：统一将题干、选项、答案拆入 `Question` 字段，而不是把整块文本全部放进题干。
- [x] 回归：真实 TXT=432 题、真实 XLS=435 题；单测、Lint、Debug 构建通过。
