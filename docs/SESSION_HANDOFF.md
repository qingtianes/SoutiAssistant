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
- Harmony workspace: `E:\SoutiAssistant_Harmony`
- Parity matrix: `E:\SoutiAssistant_Harmony\docs\PARITY_MATRIX.md`
- Project skill: `E:\Codex\CodexHome\skills\souti-parity-development\SKILL.md`

## Completed in this checkpoint

- Repaired Harmony memory files and recorded the camera privacy lesson.
- Created parity matrix and project skill.
- Disabled camera entry/registration; no camera test is authorized.
- Removed ordinary float-window permission and static page registration.

## H0 local checkpoint\n\n- Local commit: `402a4e8 docs: restore Android-to-Harmony parity baseline`.\n- Remote: unset; do not guess the URL.\n\n## Next exact actions

1. Audit inventory returned; integrate its findings into `docs/PARITY_MATRIX.md`.
2. Re-read the matrix and memory files, then review `git diff --check`.
3. README and usage guide now describe the full parity target and frozen camera.
4. H0 build already passed with constrained JVM; rerun only if code changes.
5. Review `git diff`, confirm no secrets and no debug artifacts.
6. Configure the correct Harmony Git remote only after verifying the intended URL; do not guess.
7. Commit H0 locally; push only after the intended remote URL is confirmed.

## Prohibited

- Do not start or test camera scanning.
- Do not use coordinate-click/screenshot loops.
- Do not mark any parity item complete from build success alone.
- Do not create a branch or release.



