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
- 真机测试（放到最后）：验证扫描搜题、设置/使用说明、图片 OCR 主流程；读屏搜题真机验证 window.snapshot 可行性。

## Current
- [ ] 真机验证扫描搜题（相机预览 + OCR）
- [ ] 真机验证读屏搜题技术方案

## Verification
- 当前构建成功（未签名 HAP）。
- 本地仓库干净。

## Blockers
- 无真机，无法验证 OCR 系统能力。

## Done
- [x] 建立接管基线
- [x] 图片选择 + 官方 OCR + 本地题库搜索页面
- [x] TXT 题库导入、启用/删除管理
- [x] 题库存储改为应用私有 JSON，迁移旧 Preferences
- [x] 关闭系统备份保护题库隐私
