# Phase 2: Ghost text y control de propuesta - Context

**Gathered:** 2026-09-06
**Status:** Ready for planning
**Mode:** Autonomous; user delegated implementation choices and authorized continuing with manual Phase 1 UAT deferred.

<domain>
## Phase Boundary
One local, deterministic proposal below a selected comment, accepted with Tab or discarded with Esc. No Codex process/network until phases 3–4.
</domain>

<decisions>
## Implementation Decisions
### Preview
- **D-01:** Use one project-owned disposable editor block inlay below the comment's final line; inherit editor font, line height and theme-aware muted foreground. Never alter the document to show a preview.
- **D-02:** Preserve comment and existing source. Insert after the comment line using one write command; handle EOF explicitly. Reject inline block comments followed by non-whitespace on the same line to avoid splitting live code.
### Lifecycle and keys
- **D-03:** Tab accepts only a fresh proposal in its owning editor; Esc discards. Without a proposal delegate to original editor handlers. Native lookup/template states take precedence.
- **D-04:** Editing, caret/selection change, switching editor, editor release, project disposal or a new request cancels the previous preview. Stamp and selection snapshot must match before insertion.
### Limits
- **D-05:** Single caret, writable normal editor only. Reject empty or excessive output; show at most 80 lines/16000 characters and reject rather than invisibly truncate accepted text. Local preview is explicitly identified as a development fixture; replace it with real generation in Phase 4.
### the agent's Discretion
User requested optimized defaults and autonomous phase-by-phase completion. Choose public baseline APIs and focused tests; manual visual approval stays deferred, not passed.
</decisions>

<code_context>
## Existing Code Insights
SelectedCommentResolver validates committed same-document PSI with UTF-16 offsets. GenerateCodexGhostTextAction is the single native trigger; light Java fixtures and Gradle wrapper already pass. Plugin descriptor has one generation action and a native notification group. Add preview service, renderer, editor handlers and fixtures without adding external dependencies.
</code_context>

<specifics>
Copilot-like translucent code BELOW the selected comment. Mouse selection + popup or user-assigned shortcut, not continuous completion.
</specifics>

<deferred>
Codex diagnostics (Phase 3), real generation and bounded tool-free requests (Phase 4), visual smoke tests in both versions at final handoff.
</deferred>
