---
phase: 03
plan: 01
status: complete_with_external_verification_deferred
completed: 2026-09-06
key_files:
  created:
    - src/main/kotlin/com/leandro/codexghosttext/codex/CodexAvailabilityService.kt
    - src/main/kotlin/com/leandro/codexghosttext/actions/CheckCodexConnectionAction.kt
    - src/test/kotlin/com/leandro/codexghosttext/codex/CodexAvailabilityServiceTest.kt
  modified:
    - src/main/resources/META-INF/plugin.xml
---

# Phase 3 summary

Implemented a bounded, local-only Codex availability check available from **Tools > Check Codex Connection**. It finds a native local Codex executable, launches `app-server --listen stdio://` with fixed arguments, and performs only `initialize`, `initialized`, `account/read` with `refreshToken: false`, and `account/rateLimits/read`.

The check never starts a thread or turn, never runs a model, never invokes login/logout/config operations, and neither reads nor stores authentication data. Credential-shaped environment variables are removed from the child process. Responses are categorized without logging or retaining raw server data; malformed data, timeout, a server request, or a process error yields a safe generic failure.

The planned serialization dependency was intentionally not packaged: it produced a Kotlin runtime metadata conflict while IntelliJ's test platform loaded the plugin. The diagnostic therefore uses a deliberately narrow parser for only fixed JSON-RPC envelope, account type and quota fields, with bounded line size and tests for the expected protocol shapes.

## Validation

- `./gradlew.bat test buildPlugin --no-daemon` — passing after the final hardening pass; 25 tests total, including 8 deterministic Codex protocol/diagnostic tests.
- `build/distributions/codex-ghost-text-0.1.0.zip` — generated successfully.
- `./gradlew.bat verifyPlugin --no-daemon` — could not complete because the JetBrains verifier could not obtain remote update metadata due to an SSL/TLS handshake termination. This is an external verification dependency, not a plugin test failure; repeat when the verifier endpoint is reachable.

## Safety boundary for Phase 4

The current Codex App Server protocol/schema does not expose a documented, enforceable per-turn zero-tool allowlist. The phase-4 requirement that generation have no shell, file, command or autonomous capabilities therefore remains blocked rather than implemented unsafely. See `.planning/research/CODEX-SAFETY-PROTOCOL.md`.
