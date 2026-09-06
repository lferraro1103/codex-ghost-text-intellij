---
phase: 03
status: automated_complete_manual_runtime_deferred
nyquist_compliant: false
created: 2026-09-06
---

# Phase 3 validation

| Requirement / criterion | Automated evidence | Remaining evidence |
|---|---|---|
| Missing/unreachable Codex is actionable | `CodexAvailabilityServiceTest.does not invent an executable...`; fixed Spanish notification mappings | Invoke **Tools > Check Codex Connection** in a sandbox IDE with no `codex.exe`. |
| Login, quota and connection outcomes are safe | classifier and quota tests; notification-before-response and server-request rejection tests | Exercise the action with the user's normal local Codex login and an exhausted-quota account when available. |
| Existing account only; no secrets/API keys | source review: fixed `ProcessBuilder`, scrubbed credential env keys, no auth/config reads, no raw response logging; tests build against IntelliJ platform | Manual process inspection during action invocation, if desired. |
| Plugin compatibility/package | `test buildPlugin` passed and ZIP exists | Repeat `verifyPlugin` for the configured IntelliJ versions after the JetBrains verifier endpoint is reachable. |

Manual runtime verification and the remote verifier are intentionally deferred. Neither is represented as a passing result.
