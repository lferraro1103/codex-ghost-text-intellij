---
status: partial
phase: 01-invocaci-n-sobre-comentario
source: [01-PLAN.md]
started: 2026-09-06T13:50:20Z
updated: 2026-09-07T00:00:00Z
---

## Current Test

number: 1
name: IntelliJ 2026.1 popup and assigned shortcut
expected: |
  Complete comments show the direct action; invalid selections hide it.
  The user-assigned shortcut dispatches the same action, with a fixed Spanish notification for invalid input and no document changes.
awaiting: user response

## Tests

### 1. IntelliJ 2026.1 popup and assigned shortcut
expected: Install build/distributions/codex-ghost-text-0.1.0.zip in IntelliJ 2026.1. Select full line/block comments and invoke the direct Generate Codex Ghost Text action; source stays unchanged. Invalid selections hide the popup item. Assign a temporary shortcut in Settings / Keymap and invoke with valid and invalid selections. Invalid input displays exactly Seleccioná exactamente un comentario para generar código. without a modal dialog. Verify focus, scaling and native theme.
result: [pending]

### 2. IntelliJ 2026.2 popup and assigned shortcut
expected: Install the same ZIP in IntelliJ 2026.2 with its required runtime and repeat the full valid/invalid popup and assigned-shortcut matrix from test 1. With a valid local Codex login, a valid selection must show a ghost proposal below the comment without changing the document; Tab inserts it as one Undo action and Esc discards it. Invalid selections must not start a request. No exception, lost focus or autonomous source change is allowed.
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps

No user-reported UI failures yet. These tests were absent from prior audit input, not passed. Automated action events and Plugin Verifier are not equivalent to interactive Keymap approval. Test 2 was updated after Phase 4 replaced the original local fixture with real generation.
