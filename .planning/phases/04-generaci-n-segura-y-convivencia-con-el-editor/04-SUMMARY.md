---
phase: 04
status: implemented_manual_smoke_test_pending
completed: 2026-09-07
---

# Phase 4 summary

The generation action now replaces the development fixture with a short-lived local Codex App Server thread and turn. It sends the selected comment plus a bounded current-file window, grants the active project as a read-only working directory, disables web/plugins in thread config, streams only the agent text, and forwards a completed bounded proposal to the existing immutable ghost preview.

The plugin removes credential-shaped environment variables, rejects file-change/patch signals, terminates the child on completion/failure/disposal, and leaves document mutation exclusively to the existing Tab acceptance command.

## Validation

- `./gradlew.bat test buildPlugin --no-daemon` passed.
- Protocol tests cover extraction of the nested thread ID, streamed JSON text decoding, and missing-thread rejection.
- A real IntelliJ + authenticated Codex smoke test remains required before release.
