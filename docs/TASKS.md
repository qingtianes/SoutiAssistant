---
title: Current Tasks
description: Current tasks, blockers, verification state, and recommended next actions.
doc_type: task_state
status: active
created: 2026-08-16
updated: 2026-08-17
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
- Android v1.0.2 已发布 GitHub Release，主分支与标签已推送。
- 用户验收通过。

## Recommended Next Action
1. WorkBuddy 接手重做 UI（只改 `com.dingding.souti.ui`，读 HANDOFF_TO_WORKBUDDY.md）。
2. UI 完成后由 Codex 集成、构建、测试、发布。
3. 启动鸿蒙版本（`E:\SoutiAssistant_Harmony`，先只同步改动、不真机验证）。

## Verification
- 单元测试通过；Lint 0 error；assembleDebug/assembleRelease 成功（JDK 21）。
- 发布包命名规范：`SoutiAssistant-vX.Y.Z-release.apk`。

## Blockers
- 无。

## Done
- [x] 浮窗搜题 / 读屏搜题 / 扫描搜题
- [x] 题库导入与智能匹配
- [x] 设置中心 5 大类
- [x] 扫描搜题暂停/继续
- [x] 新图标替换
- [x] 主页 B 极简卡片流 + 题库区置顶 + 右上角 ⓘ 使用说明
- [x] App 内使用说明与 README 使用说明
- [x] PRD / DESIGN / ACCEPTANCE / SECURITY / FINAL_AUDIT 文档