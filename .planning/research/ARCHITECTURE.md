# Architecture Patterns

**Domain:** Personal IntelliJ Platform plugin that turns a selected comment into a local Codex-backed ghost-code proposal.
**Researched:** 2026-08-31
**Confidence:** MEDIUM — the core protocols and platform threading rules are documented; both Codex App Server and parts of IntelliJ inline completion evolve quickly.

## Recommended Architecture

Build a native Kotlin plugin with a **project-scoped, cancellable suggestion session**. The editor layer never calls Codex directly and never changes a `Document` while a proposal is merely visible. A local process bridge owns one `codex app-server --listen stdio://` subprocess per IntelliJ project and speaks newline-delimited JSON-RPC on its standard input/output.

Use a **block inlay** as the MVP presentation, anchored immediately after the selected comment. It visually places a dim code block below the comment without inserting text. It is a better fit than normal completion because the requested result is a multi-line block below an existing comment, not a replacement at the caret. Keep that renderer behind an interface: the platform exposes `InlineCompletionProvider`, but several adjacent inline-completion rendering, insertion, and partial-accept extension points are currently marked internal or experimental. This lets a later version adopt native inline completion only after compatibility testing on the chosen IDE baseline.

```text
Context menu / shortcut
        |
GenerateFromCommentAction
        |
SuggestionCoordinator (one current session per editor)
   +----+------------------------------+
   |                                   |
ContextSnapshotter                CodexAppServerClient
(short read action)              (local process + JSON-RPC)
   |                                   |
PromptBuilder                 thread/start -> turn/start
   |                                   |
   +---------- SuggestionSession <-----+ streamed deltas
                         |
                 GhostSuggestionPresenter
                    (block inlay only)
                         |
           Tab: AcceptSuggestionAction / Esc: Dismiss
                         |
              short command + write action on Document
```

### Component Boundaries

| Component | Responsibility | Communicates With |
|---|---|---|
| `GenerateFromCommentAction` | Appears in Editor Popup Menu and Keymap; validates there is one selected comment and starts generation. Its `update()` remains cheap. | `SuggestionCoordinator`, active `Editor`/`Project` |
| `ContextSnapshotter` | Takes an immutable snapshot: selected comment, language ID, file path, surrounding bounded text, target insertion offset, indent, modification stamp, and a `RangeMarker`. | IntelliJ editor/document APIs, coordinator |
| `PromptBuilder` | Converts only the snapshot into a bounded request. Delimits user-selected code as data; requests code only, no Markdown and no file/tool changes. | coordinator |
| `CodexAppServerClient` | Launches and initializes the local `codex app-server`; serializes JSONL writes, parses JSON-RPC responses/notifications, correlates ids, exposes `startEphemeralTurn()` and `interrupt()`. | local Codex process, coordinator |
| `SuggestionCoordinator` | State machine and owner of a `SuggestionSession`; translates streamed output to a candidate and is the only component allowed to ask the presenter to show or clear it. | all components below |
| `GhostSuggestionPresenter` | Adds, updates, and disposes a block inlay. It contains no network/process logic and never calls `Document.insertString`. | `Editor`, coordinator |
| `AcceptSuggestionAction` / `DismissSuggestionAction` | Consume `Tab`/`Esc` only for a live candidate in that editor. Acceptance makes a single undoable document edit; dismissal only clears UI and cancels work. | coordinator, `Document` |
| `CodexPluginSettings` | Holds non-secret preferences: executable path, output/context caps, manual shortcut, and whether diagnostic logs are visible. No OpenAI password, cookie, API key, or OAuth token is stored. | UI/settings and process bridge |

The plugin should depend on the platform and language modules, not `com.intellij.java`, so it can operate on any language whose editor exposes a comment selection. The first release may validate syntactically that the selection is exactly one PSI comment; a plain-text fallback is useful for languages without a loaded PSI.

## Data Flow

1. The user selects one comment and invokes **Generate code with Codex** through the editor context menu or configurable shortcut.
2. The action obtains the active `Editor` and `Document`; it rejects an empty/multi-caret selection, a non-comment selection, injected/readonly files, or an already-invalid project. The comment stays untouched.
3. `ContextSnapshotter` performs a short cancellable read action and records a bounded window around the selection (for example, 100 lines or 12 KB), language/file metadata, a document modification stamp, insertion point just after the comment line, and a `RangeMarker`. It releases the read lock before any prompt, I/O, or model work.
4. `SuggestionCoordinator` supersedes any session already attached to that editor, creates a monotonic `sessionId`, and starts an ephemeral Codex thread. Use a fresh ephemeral thread for each suggestion so code-completion context is not persisted as a normal Codex conversation.
5. The bridge sends `turn/start` with the text request. It must use the most restrictive read-only sandbox supported by the locally generated App Server schema and `approvalPolicy: "never"`; do not use `workspaceWrite` merely for convenience. The App Server documentation notes that `cwd` plus workspace-write/full access can mark the project trusted in Codex configuration.
6. The bridge reads stdout continuously, routes JSON-RPC responses by request id, and routes streamed notifications by `(threadId, turnId)`. It accumulates only agent-message text, limits output size, and ignores any tool/diff event. Deltas may update the inlay progressively, but only the current `sessionId` may mutate the UI.
7. On a completed successful turn, the coordinator normalizes the candidate (remove an optional enclosing code fence, preserve line endings as `\n`, reject empty/non-code-only output) and shows it below the preserved comment. A failure, login requirement, approval request, overload exhaustion, invalid context, or cancellation clears the inlay and surfaces a compact notification.
8. `Tab` accepts only the candidate attached to the focused editor. Inside one undoable command and a minimal write action, it rechecks the session id, `RangeMarker`, document modification stamp, and writability; then it inserts the generated text at the recorded post-comment offset. `Esc`, a new generation, or a conflicting document edit simply disposes the inlay and leaves the source unchanged.

## Codex App Server Contract

The process is local, but it is still an asynchronous protocol client:

```text
GeneralCommandLine([codex path, "app-server", "--listen", "stdio://"])
  stdout: JSONL reader only
  stdin: single serialized JSONL writer
  stderr: bounded diagnostic log, never parsed as protocol

initialize -> initialized
thread/start { cwd, ephemeral: true, approvalPolicy: "never", read-only sandbox }
turn/start { threadId, input: [text] }
item/agentMessage/delta* -> candidate buffer
turn/completed -> finalize or clear
turn/interrupt { threadId, turnId } -> wait for terminal notification
```

Codex App Server uses JSON-RPC-shaped messages over JSONL on stdio; its documentation names `thread/start`, `turn/start`, streamed item deltas, `turn/completed`, and `turn/interrupt` as the relevant lifecycle. Generate TypeScript or JSON Schema from the exact installed CLI during development and use that schema as the source of truth for request fields. The host inspected during research has `codex-cli 0.128.0` and exposes `app-server`, `generate-ts`, and `generate-json-schema`; startup must still check the executable and protocol at runtime rather than assume that version.

### Session State Machine

```text
IDLE
  -> SNAPSHOTTING
  -> STARTING_THREAD
  -> GENERATING
  -> PREVIEWING
  -> ACCEPTED -> IDLE
  -> DISMISSED -> CANCELLING -> IDLE
  -> FAILED -> IDLE
```

`PREVIEWING` owns exactly one inlay and exactly one immutable candidate. A session becomes stale immediately when its editor changes, its range marker becomes invalid, the user invokes another generation, the project closes, or focus/input moves to an incompatible editor state. Stale events are discarded by `sessionId` before touching the editor.

## Concurrency and Cancellation Contracts

| Concern | Contract |
|---|---|
| IntelliJ UI | Never block EDT. Action presentation checks only cheap editor/selection state. Create/remove/update inlays on the UI dispatcher. |
| IntelliJ model access | Snapshot text in a short `readAction`; do no process I/O, PSI traversal, index query, or model generation under a read lock. Revalidate document/range after background work because read objects may become invalid. |
| Document mutation | The preview has no mutation rights. Only acceptance uses a brief write action plus one command-processor command so undo is one operation. A failed freshness check discards rather than attempts a fuzzy insertion. |
| Process I/O | One coroutine reads stdout for the process lifetime; a `Mutex` serializes stdin JSONL writes. Correlate replies by JSON-RPC id and notifications by thread/turn ids. Stderr has a size cap and is diagnostics only. |
| Per-editor sessions | At most one active session per editor. Starting a new one first invalidates the old `sessionId`, clears its inlay, then requests `turn/interrupt` if a turn id exists. |
| Cancellation | `Esc`, document change, selection/range invalidation, editor disposal, project disposal, and superseding invocation all cancel the coroutine and best-effort call `turn/interrupt`. Do not wait on EDT; await terminal `turn/completed` in the background. |
| Late responses | Never revive a canceled suggestion. Deltas and completions must pass `(sessionId, threadId, turnId, editor identity)` checks. |
| Backpressure/retry | App Server may return `-32001` when overloaded. Retry only the still-current, not-yet-visible request at most twice with exponential backoff plus jitter; do not retry auth, invalid-request, or approval failures. |
| Shutdown | On project disposal, cancel all sessions, close stdin, stop the reader, and terminate the child process after a short grace period. Never leave a background turn able to update a disposed editor. |

## Patterns to Follow

### Immutable snapshot, mutable UI session

**What:** Copy all context required by Codex into a value object before launching background work. Keep only editor-local UI handles (`RangeMarker`, `Inlay`, session id) mutable.

**When:** Every generate invocation.

**Why:** It prevents holding IntelliJ read locks during a network/model wait and gives acceptance an exact stale-result guard.

```kotlin
data class SuggestionSnapshot(
  val documentStamp: Long,
  val insertionMarker: RangeMarker,
  val selectedComment: String,
  val contextBefore: String,
  val contextAfter: String,
  val languageId: String,
  val indent: String
)
```

### Transport adapter, not App Server calls in actions

**What:** Define a small `CodexTransport` interface with a production JSON-RPC implementation and a deterministic fake.

**When:** All process communication.

**Why:** Unit and editor-fixture tests can verify ordering, stale events, and cancellation without a logged-in account or live quota.

```kotlin
interface CodexTransport : AutoCloseable {
  suspend fun startSuggestion(request: CodexRequest): RunningTurn
  fun events(): Flow<CodexEvent>
  suspend fun interrupt(turn: RunningTurn)
}
```

### Presentation adapter

**What:** Keep `SuggestionPresenter` independent from the result source and acceptance action.

**When:** Rendering ghost text.

**Why:** A block inlay is reliable for the requested below-comment preview. A later `InlineCompletionProvider` adapter can be trialed without changing the prompt, transport, state machine, or safety checks.

## Anti-Patterns to Avoid

### Holding a read lock while Codex runs

**Why bad:** It blocks IntelliJ write work, producing a frozen editor. The platform explicitly advises moving costly operations out of EDT/read actions and using cancellation-aware background work.

**Instead:** Capture a bounded snapshot, release the lock, then revalidate before rendering/accepting.

### Treating the preview as a document edit

**Why bad:** It violates the core safety promise: a canceled or stale completion may alter the file and pollute undo history.

**Instead:** Use a disposable inlay; only `Tab` runs the explicit insertion command.

### Reusing one long-lived Codex thread for unrelated comments

**Why bad:** Context and old instructions leak into the next suggestion, state can compact, and history accumulates unexpectedly.

**Instead:** Use an ephemeral, one-turn thread per suggestion; retain no code context beyond that request.

### Globally hijacking Tab or parsing stderr as JSON

**Why bad:** A global Tab handler breaks indentation, lookups, live templates, and other completion providers. Stderr is log output, not protocol framing.

**Instead:** Consume Tab only when the focused editor owns a fresh suggestion and delegate immediately otherwise; read protocol only from stdout JSONL.

### Depending directly on internal inline-completion helpers

**Why bad:** JetBrains marks several renderer, insertion, and partial-accept extension points internal/experimental; a platform update can break the plugin.

**Instead:** Ship the inlay-based MVP and isolate any future native inline-completion integration behind an adapter and explicit IDE-version compatibility tests.

## Build Order

1. **Plugin shell and editor command** — Gradle IntelliJ Platform project, plugin metadata, `GenerateFromCommentAction` in the editor popup/keymap, selection validation, and fixture tests. No Codex process yet.
2. **Safe static preview** — Implement `SuggestionSession`, block-inlay presenter, `Tab` accept / `Esc` dismiss, range-marker freshness check, one-command undo, and a hard-coded candidate. This validates the essential experience without external dependencies.
3. **Local App Server bridge** — Add executable discovery, process lifecycle, `initialize`, JSONL framing, request correlation, fake transport tests, login/version/overload diagnostics, and strict sandbox/approval settings.
4. **Generation orchestration** — Add snapshot/prompt construction, ephemeral thread/turn lifecycle, bounded streamed output, stale-event filtering, interruption, and manual end-to-end smoke testing against the user's logged-in Codex.
5. **Hardening and compatibility** — Test multiple IDE versions, active lookups/templates/multicaret, unsupported language/readonly files, project disposal, App Server overload/error paths, and verify that no preview can ever change a file before `Tab`.

## Scalability Considerations

| Concern | MVP / one user | Future wider usage |
|---|---|---|
| Concurrent suggestions | One per editor and one app-server process per project; manual trigger keeps demand low. | Add a bounded project queue and explicit per-project concurrency cap. |
| Context size | Fixed line/character cap; single selected comment. | Token-aware truncation and optional semantic context, still snapshot based. |
| App Server updates | Check executable at startup; generate schema for tested versions. | Version compatibility matrix and CI against supported IDE/CLI pairs. |
| Rendering | One custom block inlay. | Adapter may choose native inline completion only where APIs are stable. |

## Sources

- [Codex App Server README — protocol, lifecycle, stdio, backpressure, cancellation, schema generation](https://github.com/openai/codex/blob/main/codex-rs/app-server/README.md) — MEDIUM confidence (official source; protocol is actively evolving).
- [JetBrains Threading Model — read/write actions, cancellation, UI responsiveness](https://plugins.jetbrains.com/docs/intellij/threading-model.html) — HIGH confidence.
- [JetBrains Documents — obtaining and lifecycle of editor documents](https://plugins.jetbrains.com/docs/intellij/documents.html) — HIGH confidence.
- [JetBrains Inlay Hints — inline and block editor presentation model](https://plugins.jetbrains.com/docs/intellij/inlay-hints.html) — HIGH confidence.
- [JetBrains Action System](https://plugins.jetbrains.com/docs/intellij/action-system.html) and [platform module compatibility](https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html) — HIGH confidence.
- [JetBrains extension point list — inline completion surface and internal/experimental markers](https://plugins.jetbrains.com/docs/intellij/intellij-platform-extension-point-list.html) — MEDIUM confidence; verify at the implementation's target IDE version.
