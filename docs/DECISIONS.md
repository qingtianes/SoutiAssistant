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
- 采用只切蛋糕、不改味道的重构原则：每次只拆一个模块，拆前补测试、拆后全量回归。
- 目标包结构：model / repository / import / ocr / overlay / ui。
- 浮窗与读屏必须互斥：切换前完整停掉旧模式，再启动新模式，避免共用 MediaProjection 冲突。
- HarmonyOS 暂缓，先完成 Android；HarmonyOS 可经卓易通兼容安卓程序。
- 已抽出 OcrQuestionProcessor、QuestionMatcher 两个纯逻辑组件并补测试。
- 代码精简三件套：ponytail 防过度设计、code-simplification 拆后清晰化、ponytail-review 清点可删项。

## 2026-08-17
- 输出窗默认位置：删除该设置，保持“输出窗初始跟随主浮窗下方、可自由拖动”。
- 隐私：关闭系统备份 allowBackup=false，确保题库/设置仅本机。
- 扫描搜题：FILL_CENTER 居中裁剪填满预览窗口；新增取景框、双指/按钮缩放、暂停/继续。
- 主页：采用 B 极简卡片流风格；题库区置于快捷搜题上方；右上角 ⓘ 进入使用说明。
- 图标：替换为 WorkBuddy 交付的位图图标，删除 adaptive icon。
- 发布命名：APK 统一为 SoutiAssistant-vX.Y.Z-release.apk。
- 使用说明：APP 与 README 均以“题库导入”为第一步。


## 2026-08-22 题库导入解析策略
- 对带可靠空行边界且大多数题块含 `答案:` 的 TXT，空行优先于数字题号；原因是简答答案内部经常使用 `1、2、3、`。
- XLS 默认保留每一条有效数据行，不使用题干前缀做去重；相似题（如“原因”和“防范措施”）不能被静默丢弃。
- 导入确认前统一把整题文本解析为 `Question(stem, options, answer)`，避免答案和选项进入题干导致搜索与展示失真。
- PDF 导入必须在 Android 侧初始化 PDFBox 资源，并保证失败可见、资源可释放；本轮不把乱码 PDF 静默当作成功。

## 2026-08-22：导入精确性优先于宽松猜测
- 对有“题号 + 资源编号”的 PDF 仅以强题号切题，排版空行不再拆题。
- 搜索统一 0～100 相关度：题干优先；多个选项支持乱序；正确答案正文可反查；单个干扰项只作弱证据。
- 本地解析无法可靠读取扫描图片、图片公式或复杂 OMML 时明确提示，不静默伪造内容；AI 导入保留为后续可选能力。
