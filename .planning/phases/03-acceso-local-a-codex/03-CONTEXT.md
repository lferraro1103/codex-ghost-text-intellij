# Phase 3: Acceso local a Codex — Context

Gathered: 2026-09-06
Status: Ready for research/planning after Phase 2 implementation
Mode: autonomous — user delegated optimized defaults and asked to finish all phases.

## Phase Boundary

Diagnostic connection only: executable discovery, owned stdio process, initialize/initialized, account and rate-limit reads, sanitized actionable state. No model turn, login implementation, token-file access or generation in this phase. Phase 2 preview remains local until Phase 4 replaces its source.

## Decisions

- D-01: Native `codex` executable discovery from OS PATH and supported local npm installation layouts, with a clear missing-executable diagnostic. Never interpolate editor text into a command, execute a shell script, or spawn an interactive window. A narrowly scoped executable override may be added only if discovery needs it.
- D-02: One owned process/connection at a time per project; all I/O off EDT, bounded JSONL frames, deadlines, cancellation and shutdown. No persistent local socket listener or separate daemon install.
- D-03: Reuse the user's ChatGPT/Codex account via App Server. Never request/read/store API keys, passwords, cookies or tokens; do not implement OAuth or change global Codex configuration. Reject API-key account mode instead of silently billing the API.
- D-04: Add a native `Check Codex Connection` action in Tools/search, not a tool window. Fixed Spanish notifications for missing CLI, login required (`codex login` outside plugin), exhausted quota, timeout/disconnect and unsupported protocol. The ready message says the local account is available, not that a model generation has succeeded.
- D-05: Stop on exhausted quota; no automatic generation retry loops, model upgrade, usage reset or billing change. Missing quota metadata is unknown, not zero remaining.
- D-06: Fake transport and subprocess fixture tests must cover malformed/oversized frames, interleaved notifications, unknown server requests (reject), disconnect, cancellation and resource disposal. A real initialize/account-only smoke test may be run with sanitized booleans/enums; never log account email or raw envelopes.

## API Coverage

Integrate only initialize/initialized, account/read, account/rateLimits/read for diagnostics. Thread/turn methods are Phase 4. All login/logout, config writes, MCP OAuth/tool calls, shell execution, file mutations, plugin installs, external apps and remote control APIs are explicitly OPT-OUT: they violate the personal text-proposal scope. This phase is transport/diagnostics, not an AI decision system; AI-SPEC is reserved for Phase 4 generation.

## Reuse

Keep the existing notification group, native actions, Kotlin/JVM21 baseline 2026.1 and verifier matrix 2026.1/2026.2. Small explicit JSON dependency; no second Kotlin standard library or coroutine runtime. Follow upcoming CODEX-SAFETY-PROTOCOL research and installed CLI schema, not stale flag examples.

## Deferred

Manual appearance/Keymap UAT remains deferred by the user's direction, never marked passed. No continuous autocompletion, telemetry, cloud history or Marketplace publication.
