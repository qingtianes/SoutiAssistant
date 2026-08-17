---
title: Session Handoff
description: Compact checkpoint for resuming the H0 parity reset after context loss or token exhaustion.
doc_type: handoff
status: active
created: 2026-08-17
updated: 2026-08-17
tags:
  - project-memory
  - handoff
  - checkpoint
audience:
  - agent
  - maintainer
related:
  - PROJECT_CONTEXT.md
  - PARITY_MATRIX.md
  - DECISIONS.md
  - TASKS.md
  - CHANGELOG_WORK.md
---

# Current checkpoint

## Objective

Recover H0 and then fully replicate Android v1.0.2 into HarmonyOS. Do not reduce scope to the previous image-OCR/TXT prototype.

## Current files

- Android baseline: `E:\SoutiAssistant`
- Harmony workspace: `E:\SoutiAssistant\HarmonyOS`
- Parity matrix: `E:\SoutiAssistant\HarmonyOS\docs\PARITY_MATRIX.md`
- Project skill: `E:\Codex\CodexHome\skills\souti-parity-development\SKILL.md`

## Completed in this checkpoint

- Repaired Harmony memory files and recorded the camera privacy lesson.
- Created parity matrix and project skill.
- Disabled camera entry/registration; no camera test is authorized.
- Removed ordinary float-window permission and static page registration.

## Unified repository checkpoint

- Harmony H0 history is imported into the root repository under `HarmonyOS/`.
- Canonical remote: root repository `origin` (`qingtianes/SoutiAssistant`).

## Next exact actions

1. Review the unified-repository diff and memory path updates.
2. Android full regression already passed: unit tests, Lint, and debug assemble.
3. Harmony HAP build already passed with `--no-daemon`, explicit SDK, Java, and PATH.
4. Commit the canonical-path and root-memory updates on root `main`.
5. Push root `main` to `origin`.
6. Begin H1 from `HarmonyOS/docs/PARITY_MATRIX.md`: page structure and UI skeleton first.

## Prohibited

- Do not start or test camera scanning.
- Do not use coordinate-click/screenshot loops.
- Do not mark any parity item complete from build success alone.
- Do not create a branch or release.
