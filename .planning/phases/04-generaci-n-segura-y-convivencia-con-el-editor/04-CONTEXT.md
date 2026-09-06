# Phase 4: Generación segura y convivencia con el editor - Context

**Gathered:** 2026-09-06
**Status:** Ready for planning
**Mode:** Autonomous; the user delegated routine implementation decisions and clarified the required access boundary.

<domain>
## Phase Boundary

Replace the local development fixture with a real Codex App Server turn. The model may inspect the current project when that context is needed to generate a proposal, but it must never write project files autonomously. The plugin remains the sole writer, and only inserts an accepted proposal through `Tab`.

</domain>

<decisions>
## Implementation Decisions

### Project-aware generation
- **D-01:** Launch the local App Server with the active IntelliJ project's base path and a read-only sandbox. The request itself contains the selected comment and a bounded snapshot of its editor context; Codex may inspect relevant project classes or references on demand instead of receiving the entire project wholesale.
- **D-02:** Read-only commands used to search or inspect project source are allowed when Codex needs them. Autonomous writes, patches and file-change approvals are not allowed; any such request must cancel the turn and leave the document unchanged.
- **D-03:** Built-in web access and plugins remain disabled for the MVP. They are not needed to understand the local codebase and would expand the data/capability boundary. A future explicit setting may enable them.

### Response and editor lifecycle
- **D-04:** Stream one model response into a bounded code-only proposal. Strip presentation-only Markdown fences, reject blank/oversized/non-code responses, and show it through the existing block-inlay preview only after the turn completes.
- **D-05:** Preserve the existing preview freshness contract: a new request, edit, selection/editor change, project disposal, cancellation or late/failed turn removes/withholds the proposal. `Tab` remains one undoable document insertion and `Esc` remains cancellation.
- **D-06:** Use the existing ChatGPT Codex login and quota. Do not add API keys, OAuth, custom billing, credential reads, login UI or automatic retries.

### Requirement correction
- **D-07:** The original SAFE-01 wording that prohibited all commands/tools was too strict for project-aware generation. The intended guarantee is no autonomous mutation, not no project reading. Planning must revise the requirement and its threat model to reflect read-only inspection, disabled web/plugins, bounded context and explicit `Tab` acceptance.

### the agent's Discretion
- Choose the smallest public App Server request/notification surface that supports one cancellable streamed turn, and test it with a fake transport. Keep diagnostics from Phase 3 reusable; do not add a tool window or continuous completion.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project contract
- `.planning/PROJECT.md` — core value, manual interaction and explicit acceptance boundary.
- `.planning/REQUIREMENTS.md` — CODEX-01, SAFE-01 and QUAL-01; SAFE-01 needs the user-approved read-only correction recorded during this phase.
- `.planning/ROADMAP.md` §Phase 4 — delivery goal and success criteria.
- `.planning/STATE.md` — deferred UAT and previous strict safety blocker superseded by D-07.

### Existing implementation
- `.planning/phases/02-ghost-text-y-control-de-propuesta/02-CONTEXT.md` — immutable preview, Tab/Esc and freshness decisions.
- `.planning/phases/03-acceso-local-a-codex/03-CONTEXT.md` — local executable/account/quota behavior to reuse.
- `src/main/kotlin/com/leandro/codexghosttext/actions/GenerateCodexGhostTextAction.kt` — native trigger to replace the fixture source.
- `src/main/kotlin/com/leandro/codexghosttext/preview/GhostPreviewService.kt` — one preview and guarded insertion contract.
- `src/main/kotlin/com/leandro/codexghosttext/codex/CodexAvailabilityService.kt` — owned process and bounded JSONL diagnostic patterns.

### App Server protocol
- `build/codex-schema/v2/ThreadStartParams.json` — installed Codex schema for project cwd, read-only sandbox and per-thread configuration.
- `build/codex-schema/v2/TurnStartParams.json` — installed Codex schema for cancellable turn input and sandbox policy.
- `.planning/research/CODEX-SAFETY-PROTOCOL.md` — prior research; its zero-tool restriction is superseded only by D-07, not its credential/no-autonomous-write constraints.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GenerateCodexGhostTextAction`: validates exactly one selected comment and owns the explicit native trigger.
- `GhostPreviewService`: renders a non-document block inlay, cancels stale state and inserts only through one write command.
- `CodexAvailabilityService` and `CodexExecutableLocator`: own a short-lived local App Server child process, scrub credential-shaped environment values and classify account/quota outcomes.

### Established Patterns
- Project-level services own lifecycle-sensitive state; application service only wraps global Tab/Esc handlers.
- Protocol data is bounded and unlogged; notifications are handled separately from correlated responses.
- Tests use deterministic fixtures before any real account smoke test.

### Integration Points
- Replace `GhostPreviewService.showFixture` in `GenerateCodexGhostTextAction` with a non-blocking generation request.
- Reuse the existing notification group for failures and preserve preview/key handler contracts unchanged.

</code_context>

<specifics>
## Specific Ideas

The desired experience remains a manually triggered Copilot-like transparent code block below the comment. Codex may read related project source so a request such as “haceme tal método” can match the user's classes and conventions; it must not edit the project itself.

</specifics>

<deferred>
## Deferred Ideas

- Optional web/plugins and broader privacy/context controls — v2 settings work, not MVP defaults.
- Continuous completion and multiple candidates — v2.

</deferred>

---

*Phase: 4-Generación segura y convivencia con el editor*
*Context gathered: 2026-09-06*
