# Agent Instructions

## Mandatory startup

Before substantive work in this repository, use the `souti-parity-development` skill and read, in order:

1. `E:\SoutiAssistant\docs\PROJECT_CONTEXT.md`
2. this `AGENTS.md`
3. `docs/PROJECT_CONTEXT.md`
4. `docs/PARITY_MATRIX.md`
5. `docs/DECISIONS.md`
6. `docs/TASKS.md`
7. `docs/SESSION_HANDOFF.md`
8. `git status` and recent history in both Android and Harmony repositories

## Product rule

Android v1.0.2 is the only product baseline. HarmonyOS must fully replicate Android structure, features, UI, behavior, and states. Do not substitute an MVP, redesign, or simplified feature set.

## Safety and testing

- Camera scanning is last and frozen until the user explicitly authorizes testing. Do not request camera permission, start preview, or auto-capture before then.
- Do not use coordinate clicking plus repeated screenshots as the primary development loop. Prefer source inspection, UI-tree selectors, Hypium tests, logs, and storage assertions.
- Treat `构建通过`, `模拟器验证`, `待用户真机验收`, and `完成` as separate states.
- Do not record secrets or personal data in project memory.

## Memory and milestones

After every meaningful milestone and before context compaction/token exhaustion/handoff, update:

- `docs/PROJECT_CONTEXT.md`
- `docs/PARITY_MATRIX.md`
- `docs/DECISIONS.md`
- `docs/TASKS.md`
- `docs/CHANGELOG_WORK.md`
- `docs/SESSION_HANDOFF.md`
- `README.md` when user-visible behavior or project status changes

After build/test/documentation gates pass, commit and push `main` at important milestones. Do not create branches unless the user asks. Do not create a release until the user completes final real-device acceptance.

## Agent boundaries

- Main agent: architecture, product parity, integration, final review.
- Development agent: disjoint implementation scope only.
- Review agent: read-only compliance/quality review.
- Verification agent: build/tests/package/log evidence.

Every agent must report changed files, commands, evidence, and unresolved risks.
