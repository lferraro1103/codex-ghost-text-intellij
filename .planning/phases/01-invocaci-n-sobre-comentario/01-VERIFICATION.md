---
phase: 01-invocaci-n-sobre-comentario
verified: 2026-09-06T13:58:00Z
status: human_needed
score: automated selection and dispatch verified; interactive checkpoint outstanding
---

# Phase 1 verification — audit correction

## Automated evidence

- Commit `8242b04`: reject uncommitted PSI and PSI belonging to another document before looking up selected offsets. BGT update never forces a PSI commit.
- Red run: `gradlew.bat test --rerun-tasks --no-daemon`, 12 tests, 2 failures. One reproduced the stale-PSI bug (old comment accepted after replacing `//` with `xx`); the other exposed an invalid test assumption (IntelliJ derives PSI automatically from an editor). The latter fixture was corrected to use a document with no associated PSI.
- Green run: `gradlew.bat test buildPlugin --no-daemon`, 14 tests, 0 failures, 0 errors, 38 seconds. XML timestamps: selection 2026-09-06T13:54:30.215Z; actions 2026-09-06T13:54:39.394Z. These are fresh executions, not September 2 results.
- Action tests exercise keyboard-place visibility/enablement, exact single native notification delivery through the project message bus, no notification on valid execution, immutable source, absent editor, direct popup membership, registration text and no default shortcut.
- Resolver tests cover exact UTF-16/end-exclusive ranges, EOF, line/block/doc comments, whitespace-only/empty/partial/code-mixed/two-comment/string-literal rejection, absent PSI, malformed offsets, stale PSI and foreign documents.
- ZIP: `build/distributions/codex-ghost-text-0.1.0.zip`.

## Compatibility evidence

The normal `gradlew.bat verifyPlugin --no-daemon` run on September 6 failed before analysis with `SSLHandshakeException` while accessing JetBrains Marketplace metadata. The task cleared its previous generated report directory, so September 5 verdicts must not be cited as fresh evidence for this build.

Offline verification passed with cached Plugin Verifier 1.410 and the locally downloaded IDEs. The first offline process was stopped to avoid repeatedly expanding the unrelated shared plugin cache; the completed run used a project-local verifier cache via `-Dplugin.verifier.home.dir=<workspace>/build/audit-verifier-cache` and `-offline -verification-reports-dir build/reports/auditVerifier`. No TLS checks or incompatibilities are suppressed. The IDE layouts emitted warnings about missing optional classpath components; the verifier still resolved this platform-only plugin and reported Compatible for both targets.

| Target | Verdict | Timestamp (local UTC-03) |
|---|---|---|
| IU-261.22158.277 (2026.1) | Compatible | 2026-09-06 11:00:52 |
| IU-262.8665.258 (2026.2) | Compatible | 2026-09-06 11:00:47 |

Completed verifier exit code: 0; duration 53 seconds; network downloads: 0 B. Reports live under `build/reports/auditVerifier/<target>/`. ZIP SHA-256: `E8AEDE85D6AD38931CFDAC77B7343A08C12C635F24A00E80CAB54EFF14CF5E39`.

Mode reference: https://github.com/JetBrains/intellij-plugin-verifier#common-options

## Requirements

ACT-01 and ACT-02 have automated implementation evidence. Neither the physical popup/Keymap dispatch nor the full multiversion UI contract is declared human-approved. Phase 1 valid execution intentionally performs no generation; ghost preview and Codex integration remain future phases.

## Human Verification Required

The two executable matrices are tracked in `01-UAT.md` (both pending). This report refers to that single authoritative list to avoid counting the same manual checks twice. Original plan Task 4 remains a blocking checkpoint until the user approves both matrices or explicitly authorizes deferral for Phase 2. No Phase 1 SUMMARY is created because the plan prohibits it before that approval.

## Audit integrity

Before repair, `audit-uat` scanned zero files; that meant missing evidence, not a clean UAT. After creating `01-UAT.md`, the CLI correctly reports two outstanding tests. No unresolved test is silently converted to pass.
