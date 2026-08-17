---
title: Decisions
description: Important product, technical, process, and safety decisions for the Harmony parity project.
doc_type: decision_log
status: active
created: 2026-08-16
updated: 2026-08-17
tags:
  - project-memory
  - decisions
  - parity
audience:
  - agent
  - maintainer
related:
  - PROJECT_CONTEXT.md
  - PARITY_MATRIX.md
  - TASKS.md
  - CHANGELOG_WORK.md
---

# Decisions

## Historical decisions retained

- 2026-08-16: Store题库 in application-private JSON with temporary-file replacement; disable system backup for local privacy.
- 2026-08-16: Prefer user-selected PhotoViewPicker URI over broad gallery permission.

## 2026-08-17 H0 parity reset

- Android v1.0.2 at `E:\SoutiAssistant` is the only product baseline for HarmonyOS.
- The Harmony target is complete replication of Android structure, features, behavior, states, and UI. A smaller MVP is not an acceptable substitute.
- Existing Harmony code and memory that describe the product as only image OCR + TXT import are historical partial work, not the product specification.
- Android features may not be removed because a Harmony API is difficult. Keep the UI entry and expose `待验证`, `平台限制`, or `开发中` honestly.
- Smart import, bank overview, complete settings, float search, and screen read must be represented in the Harmony product structure before final acceptance.
- Camera scanning is the last implementation phase. Until the user explicitly authorizes a camera test, do not request camera permission, start preview, or auto-capture.
- The prior emulator camera test was a process failure: the emulator used the host webcam and auto-capture exposed the user without sufficient warning. Record this as a privacy lesson and never repeat it.
- Do not use coordinate clicking plus repeated screenshots as the primary development loop. Prefer source inspection, deterministic build/tests, UI-tree selectors, logs, and storage assertions.
- `ponytail` may reduce code complexity only after feature scope is fixed; it must never reduce required product features.
- Project memory must be updated at every meaningful milestone and before context exhaustion/handoff.
- At each important milestone after build/test and documentation review, commit and push `main`. Do not create a release until the user completes final real-device acceptance.
- Final acceptance is performed by the user on a real HarmonyOS device; the agent prepares the build, evidence, and checklist.
