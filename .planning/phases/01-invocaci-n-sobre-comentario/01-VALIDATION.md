---
phase: 1
slug: invocaci-n-sobre-comentario
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| **Estimated runtime** | ~120 seconds |

## Sampling Rate

- **After every task commit:** Run `./gradlew.bat test`
- **After every plan wave:** Run `./gradlew.bat test buildPlugin`
- **Before `$gsd-verify-work`:** Run `./gradlew.bat test buildPlugin verifyPlugin` and the listed sandbox checks.
- **Max feedback latency:** 120 seconds

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | ACT-01 | T-01-01 | Resolver rejects malformed/stale selections without throwing or exposing selected text | light PSI | `./gradlew.bat test --tests "*SelectedCommentResolverTest"` | ❌ W0 | ⬜ pending |
| 01-01-02 | 01 | 1 | ACT-01, ACT-02 | T-01-02 | Action does not mutate the document and revalidates before handoff | action/light IDE | `./gradlew.bat test --tests "*GenerateCodexGhostTextActionTest"` | ❌ W0 | ⬜ pending |
| 01-01-03 | 01 | 1 | ACT-01, ACT-02 | — | Plugin packaging and declared platform compatibility resolve | Gradle/plugin verifier | `./gradlew.bat test buildPlugin verifyPlugin` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

## Wave 0 Requirements

- [ ] `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, Gradle wrapper — plugin build for the IntelliJ Platform baseline and test sandbox.
- [ ] IntelliJ Platform test framework dependency; add the Java PSI test framework only if Java fixtures require it.
- [ ] `SelectedCommentResolverTest` — line/block comment, outer whitespace, partial selection, empty selection, multiple comments and code-mixed selection cases.
- [ ] `GenerateCodexGhostTextActionTest` — registration, no default shortcut, presentation and invalid-execution notification/revalidation policy.

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Context popup visibility | ACT-01 | Native popup placement and PSI availability vary by running IDE/editor context. | Run sandbox IDE; select a full line and a full block comment; confirm direct action is visible. Select whitespace, partial comment, two comments, and code+comment; confirm it is absent. |
| Keymap discovery and invalid shortcut feedback | ACT-02 | Keymap UI and dispatched shortcut are platform integration behavior. | In Settings / Keymap find `Generate Codex Ghost Text`, assign a temporary shortcut, invoke it with a valid selection and then an invalid one; confirm Spanish notification and unchanged document. |
| Multiversion smoke check | ACT-01, ACT-02 | Target IDE behavior must be exercised across the support policy. | Run the sandbox/manual checks against IntelliJ 2026.1 (Java 21 baseline) and verify the packaged plugin with IntelliJ 2026.2 before use. |

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
