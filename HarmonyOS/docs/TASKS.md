---
title: Current Tasks
description: Current parity work, blockers, verification state, and next actions.
doc_type: task_state
status: active
created: 2026-08-16
updated: 2026-08-17
tags:
  - project-memory
  - tasks
  - parity
audience:
  - agent
  - maintainer
related:
  - PROJECT_CONTEXT.md
  - PARITY_MATRIX.md
  - DECISIONS.md
  - CHANGELOG_WORK.md
  - SESSION_HANDOFF.md
---

# Tasks

## Current milestone: H0 — parity baseline recovery

- [x] Confirm Android v1.0.2 is the product baseline.
- [x] Establish initial `PARITY_MATRIX.md`.
- [x] Record camera privacy incident and freeze camera testing.
- [x] Create `souti-parity-development` project skill.
- [x] Rewrite Harmony project context and decisions around full parity.
- [x] Finish initial cross-check of the parity matrix against Android source and UI states.
- [x] Update README, usage guide, and changelog to match H0.
- [x] Run H0 build and deterministic checks.
- [ ] Configure/confirm Harmony Git remote; commit and push H0 to `main`.

## Next milestones

1. H1: complete Harmony navigation and Android-shaped UI skeleton.
2. H2: smart import and independent bank overview.
3. H3: complete settings and usage guide parity.
4. H4: image OCR parity.
5. H5: float search capability probe and implementation.
6. H6: screen read capability probe and implementation.
7. H7: camera scanning last, only after explicit user authorization.
8. H8: user real-device acceptance and final release decision.

## Prohibited until H7

- Do not start `ScanPage`.
- Do not request camera permission.
- Do not invoke host/emulator webcam.
- Do not run auto-capture.

## Verification states

- `构建通过` is not `功能完成`.
- `模拟器验证` is not `真机验证`.
- `待用户真机验收` is not `用户验收通过`.
- A parity item is complete only when the matrix contains Android source, Harmony source, deterministic evidence, and documentation.

## Blockers

- Real-device OCR, camera, screen capture, and signing are intentionally deferred to the final user-acceptance phase.
- Harmony now uses the root Android repository remote: `origin` (`qingtianes/SoutiAssistant`).
