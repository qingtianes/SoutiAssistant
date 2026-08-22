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
- Android v1.1.2 正在发布题库导入修复；源码已 push，APK Release 正在生成。
- 本次修复已通过单元测试、Lint 和 assembleDebug；等待用户在模拟器/真机导入三个样例文件验收。

## Recommended Next Action
1. 安装本地 debug APK，分别导入 PDF、XLS、TXT 三个样例并核对题数与答案结构。
2. 若用户验收通过，再决定是否提交/推送本次导入修复；本轮未 push。
3. 导入修复确认后再继续鸿蒙版本。

## Verification
- 单元测试通过；Lint 0 error；assembleDebug/assembleRelease 成功（JDK 21）。
- 发布包命名规范：`SoutiAssistant-vX.Y.Z-release.apk`。

## Blockers
- PDF 中文字体映射是否在用户设备上正常显示，仍需 Android 模拟器或真机导入验证。

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
