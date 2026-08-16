# Agent 技能分工

本项目采用“主 Agent 调度 + 子 Agent 执行”的方式。每个角色只做一类事，并使用对应技能。

## 主 Agent（技术负责人）
- 负责：定方案、拆步骤、审查结果、最终合入、跑全量回归。
- 使用技能：
  - `project-memory`：维护项目记忆。
  - `find-skills`：发现并安装新技能。
  - `self-improving-agent`：把踩坑经验固化成规则或技能。

## 开发 Agent
- 负责：在限定文件范围内实现或重构，不越界。
- 使用技能：
  - `ponytail`：只写最简可用方案，不过度设计。
  - `code-simplification`：重构时保持行为不变、让代码更清晰。
  - `test-driven-development`：有逻辑变化时先写测试。

## 审查 Agent
- 负责：只读审查，不修改代码。
- 使用技能：
  - `code-review-and-quality`：常规质量审查。
  - `ponytail-review`：专找过度设计和可删代码。
  - `receiving-code-review`：整理审查意见并分级。

## 验证 Agent
- 负责：编译、单测、Lint、打包，反馈结果。
- 使用技能：
  - `verification-before-completion`：完成前必须验证。
  - `systematic-debugging`：构建或测试失败时按根因排查。

## 硬规则
- 不同开发 Agent 的修改范围不重叠。
- 每次改动后必须跑全量回归。
- 不修改 `CODEX_HOME`，不 push（除非用户明确允许）。
