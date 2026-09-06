---
phase: 1
slug: invocaci-n-sobre-comentario
status: human_needed
nyquist_compliant: false
wave_0_complete: true
created: 2026-09-02
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | IntelliJ Platform test framework with light PSI/action tests |
| **Config file** | `build.gradle.kts` — created in Wave 0 |
| **Quick run command** | `./gradlew.bat test --tests "*SelectedCommentResolverTest"` |
| **Full suite command** | `./gradlew.bat test buildPlugin verifyPlugin` |
| **Observed runtime (2026-09-06)** | Forced test rebuild 98s; incremental corrected test + buildPlugin 38s. Verifier timing is separate; the original 120s full-gate estimate is not established. |

## Sampling Rate

- **After every task commit:** Run `./gradlew.bat test`
- **After every plan wave:** Run `./gradlew.bat test buildPlugin`
- **Before `$gsd-verify-work`:** Run `./gradlew.bat test buildPlugin verifyPlugin` and the listed sandbox checks.
- **Max feedback latency:** ≤120 seconds

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | ACT-01 | T-01-01 | Resolver rejects malformed/stale selections without throwing or exposing selected text | light PSI | `./gradlew.bat test --tests "*SelectedCommentResolverTest"` | Yes | PASS: 7 tests, 2026-09-06 |
| 01-01-02 | 01 | 1 | ACT-01, ACT-02 | T-01-02 | Action does not mutate the document and revalidates before handoff | action/light IDE | `./gradlew.bat test --tests "*GenerateCodexGhostTextActionTest"` | Yes | PASS: 7 tests including actual notifications, 2026-09-06 |
| 01-01-03 | 01 | 1 | ACT-01, ACT-02 | — | Plugin packaging and declared platform compatibility resolve | Gradle/plugin verifier | `./gradlew.bat test buildPlugin`; offline verifier fallback documented in 01-VERIFICATION.md | Yes | PASS: build and both verifier targets, 2026-09-06 |
| 01-01-04 | 01 | 1 | ACT-01, ACT-02 | — | Popup and user-assigned Keymap behavior are confirmed interactively on 2026.1/Java 21 and 2026.2/Java 25 before compatibility is claimed | blocking human checkpoint | `N/A — runs only after 01-01-03 passes` | ✅ plan | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

## Wave 0 Requirements

- [x] `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, Gradle wrapper — plugin build for the IntelliJ Platform baseline and test sandbox.
- [x] IntelliJ Platform test framework dependency and Java PSI fixtures.
- [x] `SelectedCommentResolverTest` — exact range, EOF, UTF-16, stale PSI, malformed offsets, missing PSI and selection matrix.
- [x] `GenerateCodexGhostTextActionTest` — direct registration, no default shortcut, keyboard-place presentation, actual notification delivery and document immutability.

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Context popup visibility | ACT-01 | Native popup placement and PSI availability vary by running IDE/editor context. | Run sandbox IDE; select a full line and a full block comment; confirm direct action is visible. Select whitespace, partial comment, two comments, and code+comment; confirm it is absent. |
| Keymap discovery and invalid shortcut feedback | ACT-02 | Keymap UI and dispatched shortcut are platform integration behavior. | In Settings / Keymap find `Generate Codex Ghost Text`, assign a temporary shortcut, invoke it with a valid selection and then an invalid one; confirm Spanish notification and unchanged document. |
| Multiversion smoke check | ACT-01, ACT-02 | Target IDE behavior must be exercised across the support policy; Plugin Verifier cannot establish popup/Keymap interaction. | After the automated full gate passes, stop at Task 4. Run the complete popup and assigned-shortcut matrix in IntelliJ 2026.1 on Java 21 and IntelliJ 2026.2 on provisioned Java 25; approve before the phase summary claims compatibility. |

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency ≤ 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
