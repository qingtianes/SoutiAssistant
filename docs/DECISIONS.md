---
title: Decisions
description: Important project, product, technical, process, or content decisions with rationale and consequences.
doc_type: decision_log
status: active
created: 2026-08-16
updated: 2026-08-16
tags:
  - project-memory
  - decisions
  - rationale
audience:
  - agent
  - maintainer
related:
  - PROJECT_CONTEXT.md
  - TASKS.md
  - CHANGELOG_WORK.md
---

# Decisions

## 2026-08-16
- HarmonyOS 第一版主路径：用户主动选择图片 → 官方 OCR → 显示可修改文字 → 搜索本地题库。
- 暂不增加广泛相册读取权限，优先依靠 PhotoViewPicker 返回的用户授权 URI。
- 题库存储从 Preferences 大字符串迁移到应用私有 JSON 文件，写入时用临时文件替换保证一致性。
- 关闭系统备份，避免题库和答案随系统备份离开设备。
- 悬浮窗保留为实验性功能，不作为第一版主路径。
