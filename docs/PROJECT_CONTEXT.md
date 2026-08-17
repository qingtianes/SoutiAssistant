---
title: Project Context
description: Stable project facts, structure, workflows, resources, and constraints.
doc_type: context
status: stable
created: 2026-08-16
updated: 2026-08-17
tags:
  - project-memory
  - context
  - durable-knowledge
audience:
  - agent
  - maintainer
  - workbuddy
related:
  - PARITY_MATRIX.md
  - DECISIONS.md
  - TASKS.md
  - CHANGELOG_WORK.md
  - SESSION_HANDOFF.md
---

# Project Context

## Product objective

- Project: SoutiAssistant HarmonyOS native client.
- Android baseline: `E:\SoutiAssistant`, Android v1.0.2 current `main` code and released behavior.
- Harmony workspace: `E:\SoutiAssistant_Harmony`.
- Non-negotiable objective: fully replicate Android structure, features, navigation, interaction states, data behavior, and UI. HarmonyOS is not an MVP, redesign, or feature subset.
- Platform differences may change implementation technology, not product scope. Unsupported capability must keep its product entry and show a truthful state until evidence proves availability.
- Final real-device acceptance belongs to the user.

## Android baseline

- UI: `HomeScreen.kt`, `BankScreens.kt`, `ImportScreen.kt`, `ScanScreen.kt`, `SettingsScreen.kt`, `UsageGuideScreen.kt`, `Theme.kt`.
- Smart import: TXT, DOCX, PDF, XLS through `Importer`, `FileFormatDetector`, format parsers, and `BankChunker`; `.xlsx` is explicitly unsupported.
- Search: local selected banks, normalized matching, answer/source/score rendering.
- Float search: bounded screen OCR area, independent output window, drag/resize, pause/standby and answer rendering.
- Screen read: full-screen multi-question OCR, question splitting, ordered answer output.
- Camera scan: live camera frame, landscape question region, zoom, pause/continue; this feature is deliberately last in the Harmony plan.
- Settings: permission management, recognition/matching, float display, scan settings, general/about, theme and clearing data.
- Float search and screen read are mutually exclusive.

## Harmony current evidence

- Existing ArkTS skeleton has simplified Index, TXT import, private JSON storage, local search, image OCR adapter, camera prototype, minimal settings/guide, and abandoned overlay experiment.
- TXT picker/import/enable/disable/delete/restart persistence/manual search were verified in the emulator.
- Current emulator lacks the system `textRecognition` native module; code shows a clear device-capability message instead of crashing.
- Camera prototype previously auto-started and the emulator mapped to the host webcam. Camera development/testing is now frozen and the ScanPage route is not registered.
- `SYSTEM_FLOAT_WINDOW` was denied in the emulator and removed from the normal manifest. Static overlay code is not registered as a product page.
- Read-screen work is only a technical evaluation; no product implementation exists.
- These facts are partial evidence, not parity completion. See `PARITY_MATRIX.md`.

## Toolchain

- DevEco Studio: `E:\Huawei\DevEco Studio`.
- SDK: `E:\Huawei\DevEco Studio\sdk\default`.
- HDC: `E:\Huawei\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe`.
- Java: `E:\Huawei\DevEco Studio\jbr`.
- Build with constrained JVM memory to avoid Windows page-file failure:

```powershell
$env:JAVA_TOOL_OPTIONS='-Xms128m -Xmx1536m'
$env:JAVA_HOME='E:\Huawei\DevEco Studio\jbr'
$env:PATH='E:\Huawei\DevEco Studio\tools\node;' + $env:PATH
& 'E:\Huawei\DevEco Studio\tools\hvigor\bin\hvigorw.bat' assembleHap --mode module -p product=default
```

## Required workflow

1. Read Android baseline and project memory before Harmony coding.
2. Update `PARITY_MATRIX.md` before implementation.
3. Use deterministic tests/logs/UI tree; screenshots only for important visual checkpoints.
4. Camera requires explicit user authorization before any test.
5. Update project memory at every durable milestone and before handoff/context exhaustion.
6. At important milestone gates: build/test, update Markdown/README/changelog, commit, and push `main`.
7. Do not create branches unless the user asks. Do not create a release until final acceptance.

## Skills and agent model

- Required project skill: `E:\Codex\CodexHome\skills\souti-parity-development\SKILL.md`.
- Memory: `project-memory`; lessons: `self-improving-agent`; discovery/creation: `find-skills` and `skill-creator`.
- `ponytail` may simplify implementation only after scope is fixed; it may never remove Android features.
- Main agent owns architecture/integration; development agents use disjoint write scopes; review and verification are independent.
