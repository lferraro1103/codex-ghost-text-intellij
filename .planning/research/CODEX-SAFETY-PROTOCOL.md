# Codex App Server Text-Only Safety Protocol

**Researched:** 2026-09-06  
**Applies to:** planned Phases 3–4 local Codex integration  
**Decision:** **BLOCK all `thread/start` and `turn/start` model work.** The installed/public surface does not currently provide an enforceable zero-tool allowlist.

## Non-Negotiable Result

`SAFE-01` requires that a request cannot enable tools, commands, autonomous file changes, or plugin-managed credentials. The current App Server surfaces establish no supported way to tell a turn “expose no tools” or to pass a strict empty tool allowlist. The official config schema has no `toolChoice` property [VERIFIED: official OpenAI config schema, queried 2026-09-06] and `ToolsToml` contains only `"experimental_request_user_input"`, `"update_plan"`, and `"web_search"` [VERIFIED: official OpenAI config schema, `ToolsToml`; CITED: https://developers.openai.com/codex/config-schema.json]. It is therefore not a general built-in/MCP/app/plugin tool allowlist.

**Conclusion:** No Phase 4 model turn may be implemented or tested against the user’s logged-in account until OpenAI documents and the installed schema expose an enforceable, per-turn zero-tool control (or an equivalent isolated authenticated profile whose effective tool catalog can be verified empty before any model inference). Prompt text, `sandbox_mode = "read-only"`, `approval_policy = "never"`, and rejecting approval requests are defense in depth only; none proves that no non-shell tool, MCP tool, app, plugin, web feature, or instruction source reached the model.

This is an intentional fail-closed outcome, not a request to weaken `SAFE-01`.

## Evidence

| Finding | Evidence | Consequence |
|---------|----------|-------------|
| Current explicit launcher transport is stdio JSONL. | Official App Server docs identify `--listen stdio://` as the default JSONL transport and mark WebSocket experimental/unsupported [CITED: https://learn.chatgpt.com/docs/app-server]. The exact local native launcher accepts `--listen <URL>` and reports `stdio://` as its default [VERIFIED: `C:\Users\leandro\AppData\Local\OpenAI\Codex\bin\8e5b6932251c2c1c\codex.exe app-server --help`]. | Use a direct child process with `app-server --listen stdio://`; do not open a TCP/WebSocket listener. |
| The native executable now reports a different version from the task’s initial inventory. | The exact supplied executable reported `codex-cli 0.153.4` on 2026-09-06 [VERIFIED: native `codex.exe --version`]. | Treat generated schemas as version-bound fixtures and re-run the protocol audit on every CLI upgrade. Do not assume the earlier `0.128.0` assertion remains current. |
| Empty environments are a real safety restriction, but narrow. | `ThreadStartParams` says verbatim: `"Empty disables environment access for turns that do not provide a turn override."` [VERIFIED: build/codex-schema/v2/ThreadStartParams.json:61-69]. `TurnStartParams` likewise says: `"Empty disables environment access for this turn."` [VERIFIED: build/codex-schema/v2/TurnStartParams.json:61-69]. | Send `environments: []` on both thread and turn if a future model turn is permitted. This disables environment access, not the model’s entire tool catalog. |
| The server can load external instructions. | `thread/start`, `thread/resume`, and `thread/fork` return loaded `instructionSources` paths [CITED: https://learn.chatgpt.com/docs/app-server]. Codex MCP configuration is shared across local Codex clients, and MCP server instructions are used as server-wide guidance [CITED: https://learn.chatgpt.com/docs/extend/mcp]. | A process inheriting user/project config cannot be presumed text-only or instruction-isolated. |
| App Server config is not a clean config-file selector. | The installed native help describes `-c/--config` as overriding a key “that would otherwise be loaded from `~/.codex/config.toml`” [VERIFIED: native `codex.exe app-server --help`]. | Per-key overrides cannot be represented as proof that inherited tables, project instructions, skills, MCP servers, plugins, or hooks are absent. |
| The schema intentionally exposes many independently configured tool sources. | Root config contains `apps`, `mcp_servers`, `plugins`, `skills`, `instructions`, `model_instructions_file`, `web_search`, and feature flags [VERIFIED: official OpenAI config schema; CITED: https://developers.openai.com/codex/config-schema.json]. | Disabling several feature flags is not equivalent to an allow-none policy. |

## Exact Supported Parameters (Current Schema)

### Connection handshake — allowed before the safety gate

1. Spawn the verified absolute executable without a shell:

   ```text
   C:\Users\leandro\AppData\Local\OpenAI\Codex\bin\8e5b6932251c2c1c\codex.exe app-server --listen stdio://
   ```

2. Send one JSONL-RPC `initialize` request with only required client metadata, then the `initialized` notification. `InitializeParams` requires `"clientInfo"`; the literal required fields inside it are `"name"` and `"version"` [VERIFIED: build/codex-schema/v1/InitializeParams.json:4-22]. Omit `capabilities` rather than opting into experimental APIs. The documented lifecycle requires this handshake before all other requests [CITED: https://learn.chatgpt.com/docs/app-server].

3. Optional, user-initiated **authentication diagnostic only**: send `account/read` with `{"refreshToken": false}`. `refreshToken` defaults to `false`, while `true` requests proactive managed-auth refresh [VERIFIED: build/codex-schema/v2/GetAccountParams.json:4-13]. Do not issue login, logout, refresh, config, model-list, thread, or turn requests during the diagnostic.

4. Parse only these display-safe booleans/enums in memory: whether an account exists, `requiresOpenaiAuth`, and account `type`. Do not log or persist the full RPC response because documented ChatGPT account responses can include an email address and plan type [CITED: https://learn.chatgpt.com/docs/app-server].

### Future thread and turn hardening — necessary, not sufficient

When and only when a real zero-tool capability is verified, use these current schema fields as secondary guardrails:

```json
{
  "ephemeral": true,
  "approvalPolicy": "never",
  "sandbox": "read-only",
  "environments": []
}
```

These exact `ThreadStartParams` property names are present: `"approvalPolicy"`, `"environments"`, `"ephemeral"`, `"model"`, and `"sandbox"` [VERIFIED: build/codex-schema/v2/ThreadStartParams.json:4-136]. For a turn, the exact required keys are `"input"` and `"threadId"`, and it also supports `"approvalPolicy"`, `"environments"`, `"sandboxPolicy"`, and `"outputSchema"` [VERIFIED: build/codex-schema/v2/TurnStartParams.json:4-174].

Do **not** describe these fields as the text-only guarantee. `read-only` limits sandbox writes/commands; it does not remove non-shell tools. `approvalPolicy: "never"` auto-refuses approval rather than preventing the model from attempting tool invocation. `ephemeral: true` helps avoid persisted rollout history, not tool exposure. `environments: []` only disables environment access.

## Unsupported / Unsafe Proposals

| Proposal | Verdict | Why |
|----------|---------|-----|
| “Tell the model to answer only with code / do not use tools.” | Rejected | A prompt is not an enforcement boundary. |
| `sandbox = "read-only"` alone | Rejected | It governs sandboxed execution, not the complete model-visible tool catalog. |
| `approvalPolicy = "never"` alone | Rejected | It makes approval-required operations fail later; it does not stop attempts or unrelated tools. |
| `environments = []` alone | Rejected | It disables environment access only, per the generated schema. |
| `mcp_servers = {}` / `plugins = {}` passed as inline override | Unproven; do not use as a guarantee | The launcher documents per-key override over a loaded config, but this research found no authoritative deep-replacement/isolation contract for inherited config tables. |
| Disable feature flags such as shell/app/plugin/web flags | Defense in depth only | Feature flags are individually named and do not form a documented universal zero-tool policy. |
| Read `config/read` or `model/list` to certify safety | Rejected for this plugin | Those surfaces can reveal installed/user configuration or model/account information; they do not establish an empty effective tool catalog and must not be logged or persisted. |
| Start a thread and inspect `instructionSources` as a safety probe | Rejected | Thread startup can load inherited configuration/MCP state before the zero-tool guarantee exists. |

## Conditional Defensive Launch Overrides

The following are syntactically supported config/feature names in the official schema, but are **not** a `SAFE-01` substitute. If a later verified zero-tool API becomes available, they may be supplied as additional `-c` / `--disable` hardening:

```text
approval_policy="never"
sandbox_mode="read-only"
web_search="disabled"
features.shell_tool=false
features.unified_exec=false
features.apply_patch_freeform=false
features.apps=false
features.plugins=false
features.multi_agent_v2=false
features.code_mode=false
skills.include_instructions=false
skills.bundled.enabled=false
include_apps_instructions=false
include_environment_context=false
include_permissions_instructions=false
```

The config schema explicitly includes the relevant `features` entries, `skills.include_instructions`, `skills.bundled.enabled`, and instruction/context inclusion controls [VERIFIED: official OpenAI config schema; CITED: https://developers.openai.com/codex/config-schema.json]. None has documentation stating it clears all inherited MCP entries, disables every built-in tool, or prevents project instruction discovery. Do not add an empty `instructions` value or a replacement `model_instructions_file` merely to claim isolation: neither is documented as a complete instruction-source reset.

## Safe Phase 3 Architecture (No Model Turn)

```text
IntelliJ action
  -> CodexAvailabilityService
       -> verify absolute executable exists and is executable
       -> launch child with stdin/stdout pipes and `--listen stdio://`
       -> initialize / initialized
       -> optional account/read(refreshToken=false), sanitize to enum/boolean status
       -> terminate process
  -> UI maps only safe categories:
       available+ChatGPT auth | login required | unavailable | protocol failure
```

- Use `ProcessBuilder` argument vectors, never a shell command string. Do not inject `CODEX_API_KEY`, `OPENAI_API_KEY`, `CODEX_ACCESS_TOKEN`, or any credential into the child environment. [VERIFIED: official OpenAI environment-variable reference describes those variables as credential-bearing; CITED: https://learn.chatgpt.com/docs/config-file/environment-variables]
- Do not call `account/login/start`, `account/logout`, `account/chatgptAuthTokens/refresh`, `config/read`, `config/value/write`, `config/batchWrite`, `model/list`, `thread/start`, `thread/resume`, or `turn/start` in Phase 3.
- A timeout, malformed JSONL, early child exit, or JSON-RPC error yields a generic local-connection failure. Capture only an error category and exit code; redact raw stderr/response bodies from notifications, logs, tests, and telemetry.
- `account/read` must remain optional and user-triggered. If `account.type != "chatgpt"`, `requiresOpenaiAuth` is false, or no account is present, display an actionable “Open Codex and sign in with ChatGPT” state. Do not start login from the plugin.

## Future Phase 4 Gate

Before writing any client path that invokes `turn/start`, all conditions must pass in a test against the exact native CLI version:

1. **Authoritative control:** Official docs and the generated app-server schema contain a non-experimental, per-turn parameter that explicitly restricts the model-visible tool set to zero, or a documented isolated-profile mechanism with that effect.
2. **Isolation proof:** Startup reveals no inherited instruction sources, skills, MCP servers, plugins, apps, hooks, dynamic tools, or remote environments. The proof must not rely on raw `config/read` output or secret-bearing configuration.
3. **Authentication proof:** A sanitized `account/read` result reports `type: "chatgpt"`; the plugin itself passes no API key/access token and does not implement login. This distinguishes managed ChatGPT authentication from API-key mode in the official account API [CITED: https://learn.chatgpt.com/docs/app-server]. It does not promise a particular subscription entitlement or price.
4. **Transport proof:** Child process uses only stdio, no TCP listener, no WebSocket, and no inherited credential environment variables.
5. **Runtime invariant:** If any server event is a `commandExecution`, `fileChange`, `mcpToolCall`, `dynamicToolCall`, `collabToolCall`, or `webSearch`, discard all proposal output, interrupt/terminate the session, and surface a safety failure. This is a detection backstop, not primary enforcement; the documented event stream includes these item types [CITED: https://learn.chatgpt.com/docs/app-server].
6. **Error mapping:** Only after the above gate may Phase 4 map `UsageLimitExceeded`, `Unauthorized`, `SandboxError`, and connection failures to user-facing bounded categories. These documented error kinds are available on failed turns [CITED: https://learn.chatgpt.com/docs/app-server].

If any condition is unknown, **do not start the model turn**. Keep the deterministic Phase 2 fixture behavior and report the feature as unavailable pending an App Server API upgrade.

## Account / Quota Privacy Policy

- The plugin never reads auth files, keychain entries, raw `config.toml`, or `config/read` output.
- It never calls `model/list`; model discovery is not needed for a text-only fixed integration and may reveal account/model details.
- It never presents or stores email addresses, token values, access-token presence, plan IDs, workspace identifiers, raw server error bodies, or raw configuration.
- It may hold a one-session, non-persisted status enum derived from `account/read`: `CHATGPT_READY`, `LOGIN_REQUIRED`, `UNSUPPORTED_AUTH`, or `DIAGNOSTIC_FAILED`.
- It may map a later turn’s documented error category to `QUOTA_EXHAUSTED`, `UNAUTHORIZED`, `CONNECTION_FAILED`, or `SAFE_MODE_VIOLATION`; unknown errors remain `CONNECTION_FAILED` without raw detail.

## Sources

- [OpenAI Codex App Server documentation](https://learn.chatgpt.com/docs/app-server) — stdio transport, initialization, account endpoints, instruction sources, turn event/error semantics.
- [OpenAI Codex configuration schema](https://developers.openai.com/codex/config-schema.json) — supported config shape, feature flags, tools configuration scope, MCP/skills/apps/plugins/instruction keys.
- [OpenAI Codex MCP documentation](https://learn.chatgpt.com/docs/extend/mcp) — shared local MCP configuration and server instructions.
- [OpenAI Codex environment variables](https://learn.chatgpt.com/docs/config-file/environment-variables) — credential-bearing variables that the plugin must not inject or disclose.
- Local generated App Server schema in `build/codex-schema/` — exact version-bound request fields used above.
- Exact native binary help/version — transport and local CLI version checked without reading user auth/config.

## Revalidation Trigger

Re-run this protocol before enabling Phase 4 whenever the native Codex executable changes version, the generated App Server schema is regenerated, or OpenAI publishes a documented zero-tool/tool-allowlist control. Until then, this document is a blocking safety contract.
