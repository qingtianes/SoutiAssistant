---
title: Work Changelog
description: Dated notes on changes, evidence, tooling, and durable lessons.
doc_type: work_log
status: active
created: 2026-08-16
updated: 2026-08-17
tags:
  - project-memory
  - changelog
  - parity
  - lessons
audience:
  - agent
  - maintainer
related:
  - PROJECT_CONTEXT.md
  - PARITY_MATRIX.md
  - DECISIONS.md
  - TASKS.md
---

# Work Changelog

## 2026-08-16 to 2026-08-17 — historical partial Harmony work

- Implemented partial image OCR page, TXT import, private JSON storage, local matching, minimal settings/guide, and a camera prototype.
- Verified TXT import, enable/disable/delete, restart persistence, and manual local search in the emulator.
- Discovered the emulator camera was mapped to the host webcam; camera auto-capture was a privacy/process failure and is now frozen.
- Discovered the emulator lacks the native system OCR module; code now reports a device capability limitation instead of crashing.
- Discovered normal float-window creation is denied; static overlay code is not a completed product feature.

## 2026-08-17 — H0 parity baseline recovery

- Restored the actual product objective: full Android v1.0.2 replication, not an image-OCR/TXT MVP.
- Added `docs/PARITY_MATRIX.md` as the authoritative Android→Harmony gap/evidence tracker.
- Rewrote `docs/PROJECT_CONTEXT.md`, `docs/DECISIONS.md`, and `docs/TASKS.md` to survive context/model/token changes.
- Created `E:\Codex\CodexHome\skills\souti-parity-development\SKILL.md` with mandatory baseline, memory, testing, camera-safety, and milestone rules.
- Disabled the camera scan entry and removed `ScanPage` from the registered page list while camera work is frozen.
- Removed ordinary-build `SYSTEM_FLOAT_WINDOW` declaration and unregistered the static overlay product page.
- Cleared generated debug screenshots/layout dumps and JVM crash logs from the workspace.
- Release-mode HAP build passed before and after the H0 changes.

## Durable lesson

A long conversation is not project memory. If the baseline, decisions, task state, and handoff are not updated in the repository, a model/context change can silently redefine the product. H0 exists to prevent a repeat.

## H0 checkpoint history

- Created the Harmony H0 checkpoint after memory, parity matrix, camera freeze, README, and build checks.
- The H0 history was later imported into the root repository under `HarmonyOS/`.



## 2026-08-17 — unified repository

- Imported the Harmony project and its history into E:\SoutiAssistant\HarmonyOS using Git subtree.
- Updated canonical paths, project skill, root README, root AGENTS, and root project memory.
- Android tests/Lint/debug build passed; Harmony HAP build passed with --no-daemon after correcting the Java PATH.

## 2026-08-18 — H0 pushed

- Pushed unified-repository H0 checkpoint to GitHub origin/main (2b561ca).
- Git proxy configured for this repository: http://127.0.0.1:7990.



## 2026-08-18 - H1 page skeleton

- Added `ui/HomePage.ets` with Android home information architecture (banks / shortcut-search / settings sections, circular help, theme toggle, truthful capability states).
- Added `ui/ImportPage.ets` (smart-import shell; TXT functional, DOCX/PDF/XLS marked in progress).
- Added `ui/OverviewPage.ets` (bank overview with count, enable/disable, delete, detail navigation).
- Added `ui/BankDetailPage.ets` (per-question view and single-question delete).
- Expanded `SettingsPage.ets` to the Android five-category skeleton with truthful platform states.
- Updated `UsageGuidePage.ets` to the Android guide order.
- Registered new pages in `main_pages.json` with `HomePage` as entry; kept `Index` as Harmony-extra image/text utility.
- ArkTS build passed with the documented `--no-daemon` command.
- Recorded GitHub proxy `http://127.0.0.1:7990` in AGENTS.md and Harmony project context.
