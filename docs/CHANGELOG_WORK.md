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
- 建立 HarmonyOS 接管基线与标签。
- 实现图片选择 + 官方 OCR + 本地题库搜索页面。
- 实现 TXT 题库导入、启用/删除管理。
- 题库存储迁移到应用私有 JSON 文件。
- 关闭系统备份保护题库隐私。
- 验证 Hvigor 构建成功（未签名 HAP）。
- 建立 HarmonyOS 项目记忆。

## 2026-08-17
- 新增 SettingsPage.ets：设置页（当前版本、使用说明入口、隐私说明、清空本地题库）。
- 新增 UsageGuidePage.ets：使用说明页。
- Index 顶栏新增“使用说明 / 设置”入口，通过 router 跳转。
- main_pages.json 注册新页面。
- 本地构建 hvigorw assembleHap debug 通过（未签名 HAP）。
