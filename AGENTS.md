# Agent Instructions

## Project Memory Requirement

Keep these project memory files accurate and concise when work changes durable context in project folders or repositories:

- `docs/PROJECT_CONTEXT.md` for stable project facts, structure, workflows, resources, and constraints.
- `docs/DECISIONS.md` for dated project, product, technical, process, or content decisions and rationale.
- `docs/TASKS.md` for current tasks, blockers, and next actions.
- `docs/CHANGELOG_WORK.md` for dated notes on changed files, docs, assets, behavior, deliverables, process, tooling, checks, and verification.

Do not store secrets, credentials, API keys, private tokens, database dumps, or sensitive personal data in project memory.

docs/*.md memory files use Project Memory Metadata v1 frontmatter; preserve it when editing. AGENTS.md stays plain Markdown.

## Operating Rules
- 重构只改结构，不改变浮窗、读屏、扫描、题库导入等现有功能行为。
- 未经用户明确允许，不 push。
- 不修改 CODEX_HOME；文件尽量放 E 盘。
- 构建前设置 JAVA_HOME 为 E:\Huawei\DevEco Studio\jbr。
- 每次改动后运行 testDebugUnitTest、lintDebug、assembleDebug。
