# Phase 2: Ghost text y control de propuesta - Research

**Researched:** 2026-09-06  
**Domain:** IntelliJ Platform 2026.1 editor block-inlay presentation, proposal lifecycle, and editor-key interception  
**Confidence:** HIGH for the 2026.1 API surface; MEDIUM for behavior across later IDE lines.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
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

### Deferred Ideas (OUT OF SCOPE)
Codex diagnostics (Phase 3), real generation and bounded tool-free requests (Phase 4), visual smoke tests in both versions at final handoff.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PREV-01 | User can see one generated code block below the selected comment without the document being modified. | Public block inlay and renderer contract; fixture tests assert document equality. |
| PREV-02 | Preview is discarded when the user edits, changes selection/editor, or starts another generation. | Per-preview disposable plus document/caret/selection/editor-release listeners. |
| ACPT-01 | User can insert a fresh preview with Tab as one undoable document change. | Original-handler wrapper, freshness gate, `WriteCommandAction` builder, and one `Document.insertString`. |
| ACPT-02 | User can discard a preview with Esc, while Tab and Esc retain normal IDE behavior when no preview is active. | Global singleton handler wrappers delegate to captured original handlers on every non-consumed path. |
</phase_requirements>

## Summary

Implement Phase 2 as a project light service that owns at most one `ProposalPreview`: the target `Editor`, its `Document`, immutable selection/comment snapshot, creation `modificationStamp`, generated fixture text, and one `Inlay`. The preview service installs the visual as a block inlay at the comment line's end offset. It never writes the document while creating, painting, or cancelling a preview. The SDK's public 2026.1 contract exposes `InlayModel.addBlockElement(int, boolean, boolean, int, EditorCustomElementRenderer)`, `Inlay.dispose()`, `Editor.getColorsScheme()`, and `Editor.getLineHeight()` [VERIFIED: local IntelliJ 2026.1 SDK `javap`; CITED: https://plugins.jetbrains.com/docs/intellij/editor-basics.html].

Key interception cannot safely be project-scoped: `EditorActionManager` is application-wide. Install exactly one application light service that wraps the public handlers identified by `IdeActions.ACTION_EDITOR_TAB` and `IdeActions.ACTION_EDITOR_ESCAPE`; it routes only to the project service attached to the supplied editor. Every route not consumed, including an active native completion lookup or live template, calls the captured original handler with the identical `(editor, caret, dataContext)`. The exact public constants are `"EditorTab"` and `"EditorEscape"` [VERIFIED: local IntelliJ 2026.1 SDK `javap -constants`]. This preserves native behavior and avoids restoring a handler from one closing project over another project's active wrapper.

Acceptance is deliberately a small transaction: revalidate the owner, one caret, normal writable editor, snapshot selection, document identity/stamp, and no native lookup/template; calculate the post-comment insertion point with the EOF case; then run exactly one named `WriteCommandAction` that performs one `Document.insertString`. JetBrains documents that document mutation needs both a command (the outer command is one undo entry) and a write action, `\\n` is the only valid document line separator, and read-only files must be checked before mutation [CITED: https://plugins.jetbrains.com/docs/intellij/documents.html].

**Primary recommendation:** Use public block inlays plus a project `GhostPreviewService`, and one application-installed `EditorActionHandler` wrapper pair that always delegates the original Tab/Esc handler unless a still-fresh owned preview can safely consume the key.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Fixture proposal creation and validation | API / Backend (local plugin service) | — | The service owns deterministic proposal state; Phase 2 has no network/process work. |
| Ghost block rendering | Browser / Client (IDE editor UI) | API / Backend | The renderer paints only an inlay; it never changes the document. |
| Tab/Esc arbitration | Browser / Client (IDE editor action system) | API / Backend | The key wrapper sees the focused editor and asks its project service whether consumption is safe. |
| Final code insertion / Undo | Browser / Client (IDE document command) | — | IntelliJ's document/command model owns mutation and Undo history. |

## Project Constraints (from AGENTS.md)

- Keep this a native IntelliJ Platform plugin.
- Use the already-authenticated local Codex App Server later; do not add API-key or OAuth behavior.
- Keep invocation manual from the selected comment through the existing action/shortcut.
- Do not modify a document before explicit acceptance with Tab.
- Prefer a simple, robust local personal-plugin solution; do not expand to public distribution concerns.
- Preserve the existing GSD workflow and do not make direct production edits during research.

## Standard Stack

### Core

| Library / API | Version | Purpose | Why Standard |
|---------------|---------|---------|--------------|
| IntelliJ Platform editor API | 2026.1 baseline | `InlayModel`, `EditorCustomElementRenderer`, listener APIs, action handlers, document command | It is already the target platform: `platformVersion=2026.1`, `sinceBuild="261"`, and Java 21 are verbatim project settings [VERIFIED: gradle.properties:4-7 — `platformVersion=2026.1`, `javaVersion=21`; build.gradle.kts:35-37 — `sinceBuild = "261"`]. |
| IntelliJ Platform test framework | Platform-provided | Light fixture tests for preview state and document mutation | Existing build includes `testFramework(TestFrameworkType.Platform)` [VERIFIED: build.gradle.kts:22-26 — `testFramework(TestFrameworkType.Platform)`]. |

### Supporting

| Library / API | Version | Purpose | When to Use |
|---------------|---------|---------|-------------|
| Kotlin standard library supplied by IDE | Project Kotlin 2.4.0 | Implementation language | Use existing toolchain; do not add dependencies for this phase [VERIFIED: gradle.properties:2,7-8 — `kotlinVersion=2.4.0`, `javaVersion=21`, `kotlin.stdlib.default.dependency=false`]. |
| `WriteCommandAction` | 2026.1 SDK | One named document edit/Undo unit | Only in the successful Tab acceptance path. |
| `LookupManager` and `TemplateManager` | 2026.1 SDK | Native completion/template precedence guard | Check immediately before consuming Tab/Esc; do not synthesize or dismiss native state. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Block inlay | Write generated code then undo on reject | Violates D-01 and pollutes document/Undo before user consent. |
| Application handler wrapper + project routing | Per-project `setActionHandler` install/uninstall | Incorrect because `EditorActionManager` is application-wide; project disposal can restore a stale predecessor and break other open projects. [VERIFIED: local IntelliJ 2026.1 SDK `javap`: `EditorActionManager.getInstance()` obtains an application service.] |
| Public `EditorActionHandler` | `InlineCompletionProvider` | The latter is deferred; current phase needs manually initiated multiline block behavior and stable explicit ownership. |

**Installation:** No external package is installed in this phase. Use the already configured IntelliJ Platform dependency.

## Architecture Patterns

### System Architecture Diagram

```text
GenerateCodexGhostTextAction
  -> SelectedCommentResolver validates the current selection
  -> GhostPreviewService.showFixture(editor, commentSnapshot)
       -> reject multi-caret/viewer/read-only/inline-block-tail/oversize
       -> dispose previous preview
       -> add block inlay below comment line; register preview lifetime listeners

Editor Tab or Esc
  -> application GhostKeyHandler wrapper
       -> active lookup or live template? ---- yes --> original IDE handler
       -> owning project service has matching fresh preview?
            -> no ------------------------------> original IDE handler
            -> Esc -----------------------------> dispose preview; consume
            -> Tab -----------------------------> revalidate -> one write command -> insert -> dispose

Document/caret/selection/editor release/project disposal/new request
  -> GhostPreviewService.cancel() -> dispose inlay and per-preview listeners
```

### Recommended Project Structure

```text
src/main/kotlin/com/leandro/codexghosttext/
├── actions/GenerateCodexGhostTextAction.kt     # existing trigger delegates to preview service
├── preview/GhostPreviewService.kt              # project-scoped state, validation, show/cancel/accept
├── preview/GhostBlockRenderer.kt               # pure sizing and painting; no document writes
├── preview/ProposalPreview.kt                  # immutable snapshot + disposable inlay lifetime
└── editor/GhostKeyHandlerInstaller.kt          # one app-scoped Tab/Esc wrappers and original fallback
src/test/kotlin/com/leandro/codexghosttext/
├── preview/GhostPreviewServiceTest.kt          # lifecycle, limits, insert/Undo
└── editor/GhostKeyHandlerTest.kt                # consume/delegate precedence and owner routing
```

### Pattern 1: Disposable preview ownership

**What:** A project light service owns only a nullable preview and a child disposable. The child owns its inlay and all listener registrations. Replacing or cancelling a preview disposes the child first, then clears the reference.

**When to use:** Every new generation request and every cancellation trigger from D-04.

**Implementation details:** Register per-preview listeners with overloads that take `Disposable`: `Document.addDocumentListener(listener, disposable)`, `CaretModel.addCaretListener(listener, disposable)`, `SelectionModel.addSelectionListener(listener, disposable)`, and `EditorFactory.addEditorFactoryListener(listener, disposable)` [VERIFIED: local IntelliJ 2026.1 SDK `javap`; CITED: https://plugins.jetbrains.com/docs/intellij/documents.html]. Editor listeners must be cheap; JetBrains specifically advises listeners to only clear caches/state [CITED: https://plugins.jetbrains.com/docs/intellij/threading-model.html].

### Pattern 2: One global key wrapper, original fallback

**What:** At application-service startup, capture each current handler then set a wrapper for Tab and Esc. The wrapper calls `original.execute(editor, caret, dataContext)` untouched whenever the preview service says it did not consume the key.

**When to use:** Only when installing the two wrapper handlers once. It is not a per-preview or per-project operation.

**Exact 2026.1 API surface:** `EditorActionManager.getActionHandler(String)`, `setActionHandler(String, EditorActionHandler)`, `EditorActionHandler.doExecute(Editor, Caret, DataContext)`, and `EditorActionHandler.execute(Editor, Caret, DataContext)` are public in the local target SDK [VERIFIED: local IntelliJ 2026.1 SDK `javap`]. JetBrains' multicaret documentation says a delegating editor handler should pass the same caret to its delegate [CITED: https://plugins.jetbrains.com/docs/intellij/multiple-carets.html].

**Lifecycle:** Keep installer ownership at application scope, not the project service. On application/plugin disposal, restore only if `manager.getActionHandler(actionId) === wrapper`; otherwise leave a later-installed wrapper intact. Run setup/restore on the UI thread because handler-chain mutation is global. The last threading point is an implementation guard based on the action-system's global mutable state [ASSUMED].

### Pattern 3: Freshness gate before the only write

**What:** Store the original `Document` reference, `Document.modificationStamp`, selection start/end, comment end line, and insertion offset context. Just before Tab insertion, require identity and exact snapshot match again.

**When to use:** In `acceptIfFresh`, not only when the preview is shown.

**Required guards, in order:** project/editor active and undisposed; same owning editor/document; `caretModel.caretCount == 1`; `!editor.isViewer`; `document.isWritable`; selection snapshot unchanged; stamp unchanged; `LookupManager.getActiveLookup(editor) == null`; `TemplateManager.getInstance(project).getActiveTemplate(editor) == null`; output remains non-empty and within D-05 bounds; target's post-comment segment is safe. `Document` exposes `isWritable()` and `getModificationStamp()` in the 2026.1 SDK [VERIFIED: local IntelliJ 2026.1 SDK `javap`].

### Pattern 4: Pure renderer

**What:** `GhostBlockRenderer` measures text with the owner editor's font/line height and paints a theme-aware muted foreground. It uses no write action, no PSI, and no document mutation.

**When to use:** `InlayModel.addBlockElement` needs a renderer. Cache only derived measurement per paint/update if necessary.

**API:** Use `editor.colorsScheme.getFont(EditorFontType.PLAIN)` and `editor.lineHeight`; the renderer overrides `calcWidthInPixels`, `calcHeightInPixels`, and `paint` [VERIFIED: local IntelliJ 2026.1 SDK `javap`].

### Anti-Patterns to Avoid

- **Per-project action-handler replacement:** Never restore a captured handler from each project service; handler chains are application-global.
- **Mutating while previewing or painting:** An inlay renderer is UI code. Do not use it to insert, reformat, commit PSI, or update state beyond rendering.
- **Treating selection/caret listeners as a reason to re-show:** They only cancel. Re-showing creates loops and violates manual activation.
- **Invisible truncation:** Reject over-limit fixture/generated output; do not render a shortened preview but insert its unseen full text.
- **A stale acceptance based solely on the inlay offset:** Use document identity, stamp, selection, editor, and comment-line snapshot before mutation.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Multiline editor overlay | Swing component positioned over the editor | `InlayModel.addBlockElement` + `EditorCustomElementRenderer` | The editor owns scrolling, folding, repainting, logical/visual coordinate translation, and disposal. |
| Undo grouping | Custom undo stack or nested arbitrary commands | One `WriteCommandAction.writeCommandAction(project).withName(...).run { document.insertString(...) }` | IntelliJ records the outer command as the undo unit [CITED: https://plugins.jetbrains.com/docs/intellij/documents.html]. |
| Key binding replacement | Raw AWT key listener | `EditorActionManager` / `EditorActionHandler` wrapper | Preserves IDE action dispatch, injected contexts, lookup/template precedence, and original fallback. |
| Preview cleanup registry | Strong global map of `Document`/`Editor` | Project service plus child `Disposable` registrations | Documents can be garbage-collected and retaining them long-term leaks memory [CITED: https://plugins.jetbrains.com/docs/intellij/documents.html]. |

**Key insight:** IntelliJ already owns editor coordinates, document history, key dispatch, and resource disposal. The plugin should own only the proposal snapshot and decision to consume a key.

## Common Pitfalls

### Pitfall 1: Incorrect block-inlay anchor

**What goes wrong:** The preview appears before code on the same line, floats at the wrong logical line, or shifts into existing source.

**Why it happens:** An offset inside the comment, `showAbove=true`, or an insertion offset confused with an inlay anchor was used.

**How to avoid:** Anchor the inlay at `document.getLineEndOffset(commentEndLine)` with `showAbove=false`; calculate the separate insertion string/offset only on acceptance. Test EOF, line comments, standalone block/doc comments, and reject block comments with non-whitespace suffix.

**Warning signs:** A preview renders on the comment's line or acceptance separates code that shared the closing block-comment line.

### Pitfall 2: Tab steals native IDE behavior

**What goes wrong:** Completion selection, live-template expansion, indentation, or normal Tab behavior stops working.

**Why it happens:** The wrapper consumes a key from any editor or fails to invoke its original handler on a non-owned/stale preview.

**How to avoid:** Make completion/template checks first; then require exact preview owner/freshness. Delegate the same caret/data context to the captured handler otherwise. `LookupManager.getActiveLookup(Editor)` and `TemplateManager.getActiveTemplate(Editor)` are public in the target SDK [VERIFIED: local IntelliJ 2026.1 SDK `javap`].

**Warning signs:** A fixture test where an unrelated editor has a preview consumes Tab, or a simulated original handler is not called when there is no preview.

### Pitfall 3: Preview survives stale state

**What goes wrong:** Tab inserts text after a user changed the document, selection, caret, editor, or file.

**Why it happens:** Validation only happened when showing the inlay.

**How to avoid:** Register cancel listeners with the preview disposable and repeat all snapshot guards in the Tab acceptance path. Never store PSI as long-lived preview state.

**Warning signs:** Mutation stamp differs, listeners stay registered after replacement, or the inlay remains after editor release.

### Pitfall 4: Read-only acceptance failure

**What goes wrong:** Tab throws or silently fails in a viewer/read-only file.

**Why it happens:** `Document.insertString` is attempted despite the editor/document being non-writable.

**How to avoid:** Reject preview creation and acceptance for `editor.isViewer` or `!document.isWritable`; when a file-backed document can change writability, call `ReadonlyStatusHandler.ensureDocumentWritable(project, document)` immediately before the command and fall back without consuming Tab if it returns false [VERIFIED: local IntelliJ 2026.1 SDK `javap`; CITED: https://plugins.jetbrains.com/docs/intellij/documents.html].

### Pitfall 5: More than one undo operation

**What goes wrong:** One acceptance needs multiple Undo presses.

**Why it happens:** Formatting/selection movement/document changes run in separate commands or outside the named command.

**How to avoid:** One command contains only the single insertion and no formatter in Phase 2. Selection/caret adjustment after insertion must be proven not to alter the document; otherwise defer it.

## Code Examples

Verified 2026.1 public patterns. `IdeActions.ACTION_EDITOR_TAB` is exactly `"EditorTab"`; `IdeActions.ACTION_EDITOR_ESCAPE` is exactly `"EditorEscape"` [VERIFIED: local IntelliJ 2026.1 SDK `javap -constants`].

### Add a non-mutating block inlay

```kotlin
val lineEnd = editor.document.getLineEndOffset(commentEndLine)
val inlay = editor.inlayModel.addBlockElement(
    lineEnd,
    /* relatesToPrecedingText = */ false,
    /* showAbove = */ false,
    /* priority = */ 0,
    GhostBlockRenderer(editor, proposalText),
)
```

The exact target signature is `addBlockElement(int, boolean, boolean, int, EditorCustomElementRenderer)` [VERIFIED: local IntelliJ 2026.1 SDK `javap`]. `0` is only a display-priority default, not persisted state [ASSUMED].

### Wrap an editor handler and preserve the fallback

```kotlin
private class TabHandler(
    private val original: EditorActionHandler,
    private val router: (Editor, Caret, DataContext) -> Boolean,
) : EditorActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        if (caret != null && router(editor, caret, dataContext)) return
        original.execute(editor, caret, dataContext)
    }
}
```

The original handler's three-argument `execute(Editor, Caret, DataContext)` is public and final; delegation must preserve the same caret [VERIFIED: local IntelliJ 2026.1 SDK `javap`; CITED: https://plugins.jetbrains.com/docs/intellij/multiple-carets.html]. `caret != null` is a gate because the project has a one-caret contract, not because the SDK forbids null [ASSUMED].

### Make a single undoable insertion

```kotlin
WriteCommandAction.writeCommandAction(project)
    .withName("Accept Codex Ghost Text")
    .run<RuntimeException> {
        document.insertString(insertionOffset, insertionText)
    }
```

The builder chain `writeCommandAction(Project)`, `withName(String)`, and `run(ThrowableRunnable)` is present in the local 2026.1 SDK [VERIFIED: local IntelliJ 2026.1 SDK `javap`]. The command name `"Accept Codex Ghost Text"` is a new UI label and requires no protocol compatibility [ASSUMED]. `insertionText` must contain only `\\n` line separators [CITED: https://plugins.jetbrains.com/docs/intellij/documents.html].

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Overlay or temporary document text for suggestion UI | Disposable editor inlays for non-document presentation | Phase 2 design | Meets explicit non-mutation requirement and keeps Undo clean. |
| Project-local global-key replacement | App-level handler installation that routes to project-owned preview state | Phase 2 design | Keeps global handler lifecycle correct across multiple open projects. |

**Deprecated/outdated:**

- `EditorActionManager.getTypedAction()` is deprecated in the local 2026.1 SDK; do not use it. `EditorActionManager.setActionHandler(...)` is not marked deprecated in that SDK [VERIFIED: local IntelliJ 2026.1 SDK `javap -v`].
- `Application.runWriteAction` and Kotlin `runWriteAction` are low-level/obsolete alternatives for plugin code targeting current platform lines; use `WriteCommandAction` for the acceptance command [CITED: https://plugins.jetbrains.com/docs/intellij/threading-model.html].

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Priority `0` gives the desired neutral stacking order for this plugin's inlay. | Code Examples | Visual ordering may need a small targeted adjustment. |
| A2 | A null `Caret` should not consume Tab/Esc under the single-caret product rule. | Code Examples | A rare action context may need explicit original fallback coverage. |
| A3 | Editor action handler-chain installation/restoration must run on the UI thread. | Architecture Patterns | A race during dynamic plugin load/unload; add a focused smoke test / platform assertion check. |

## Open Questions

1. **Exact muted-foreground color key and opacity policy**
   - What we know: the renderer can read the editor's active color scheme and line height through public API [VERIFIED: local IntelliJ 2026.1 SDK `javap`].
   - What's unclear: no locked theme token or alpha level is specified.
   - Recommendation: derive a muted color from `EditorColorsScheme.defaultForeground` without a hard-coded theme name; defer visual approval as requested.
2. **Compatibility with target 2026.2 handler behavior**
   - What we know: the project verifier targets both 2026.1 and 2026.2 [VERIFIED: build.gradle.kts:39-43 — `verifierTarget2026_1`, `verifierTarget2026_2`].
   - What's unclear: manual interaction has been expressly deferred.
   - Recommendation: compile/test on 2026.1 now, run `verifyPlugin` after implementation, and leave manual visual UAT pending rather than marking it passed.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Java | Gradle compilation/test sandbox | ✓ | OpenJDK 21.0.12 | — |
| Gradle wrapper | test/build | ✓ | 9.0.0 | — |
| IntelliJ IDEA platform archive | 2026.1 API compilation | ✓ | 2026.1 cache available | Existing Gradle resolution |
| IntelliJ Platform test framework | light fixture tests | ✓ | configured platform test framework | — |

**Missing dependencies with no fallback:** None.  
**Missing dependencies with fallback:** None.  
**Observed validation:** `./gradlew.bat test` completed successfully on 2026-09-06 [VERIFIED: local command output].

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | IntelliJ Platform light fixture + JUnit 4.13.2 [VERIFIED: build.gradle.kts:19-25 — `testImplementation("junit:junit:4.13.2")`, platform test framework]. |
| Config file | `build.gradle.kts` |
| Quick run command | `./gradlew.bat test --tests "com.leandro.codexghosttext.preview.*" --tests "com.leandro.codexghosttext.editor.*"` |
| Full suite command | `./gradlew.bat test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PREV-01 | Fixture proposal creates exactly one valid block inlay below selected comment while document text/stamp stay unchanged. | light integration | `./gradlew.bat test --tests "com.leandro.codexghosttext.preview.GhostPreviewServiceTest"` | ❌ Wave 0 |
| PREV-02 | Edit, caret move, selection change, second request, editor release, and service disposal remove inlay and listeners. | light integration | same test class | ❌ Wave 0 |
| ACPT-01 | Fresh Tab inserts after comment/EOF once and one Undo restores source; stale stamp/snapshot does not insert. | light integration | same test class | ❌ Wave 0 |
| ACPT-02 | Esc cancels; no proposal, wrong editor, active lookup, and active template delegate exact original handler. | unit/light integration | `./gradlew.bat test --tests "com.leandro.codexghosttext.editor.GhostKeyHandlerTest"` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** relevant targeted `./gradlew.bat test --tests ...` command.
- **Per wave merge:** `./gradlew.bat test`.
- **Phase gate:** full suite green, then `./gradlew.bat verifyPlugin`; manual visual smoke remains deferred and must not be reported as complete.

### Wave 0 Gaps

- [ ] `src/test/kotlin/com/leandro/codexghosttext/preview/GhostPreviewServiceTest.kt` — PREV-01, PREV-02, ACPT-01 fixture coverage.
- [ ] `src/test/kotlin/com/leandro/codexghosttext/editor/GhostKeyHandlerTest.kt` — ACPT-02 original-fallback and native-precedence coverage.
- [ ] Small fake/original handler recorder and preview fixture factory — avoids mutating global handler chains in unrelated tests.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | no | No Codex connection/authentication in Phase 2. |
| V3 Session Management | no | No session or credential state. |
| V4 Access Control | yes | Gate mutation to one fresh proposal owned by the active project/editor; user explicitly presses Tab. |
| V5 Input Validation | yes | Reject empty/oversize output, viewer/read-only/multicaret contexts, stale snapshots, and unsafe inline block-comment placement. |
| V6 Cryptography | no | No secret, network, or cryptographic material in scope. |

### Known Threat Patterns for IntelliJ editor mutation

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Stale UI proposal writes into changed source | Tampering | Document identity/stamp + selection/editor snapshots rechecked inside the accept path. |
| Preview causes unconsented file mutation | Tampering | Inlay only before acceptance; exactly one explicit Tab command is the mutation boundary. |
| Read-only/viewer bypass | Elevation of privilege | Reject both creation and acceptance; require writable document and `ReadonlyStatusHandler` check. |
| Native Tab/Esc behavior is suppressed | Denial of service | Lookup/template precedence plus exact original-handler fallback. |

## Sources

### Primary (HIGH confidence)

- Local IntelliJ IDEA 2026.1 SDK cache, inspected with `javap` — exact public contracts for `InlayModel`, `Inlay`, `EditorCustomElementRenderer`, `EditorActionManager`, `EditorActionHandler`, `IdeActions`, `WriteCommandAction`, listeners, lookup/template managers, documents, and readonly status.
- [JetBrains Documents](https://plugins.jetbrains.com/docs/intellij/documents.html) — document listener, writable-file, command/Undo, and newline rules.
- [JetBrains Editor Events](https://plugins.jetbrains.com/docs/intellij/editor-events.html) — action-handler retrieval and handler registration pattern.
- [JetBrains Multiple Carets](https://plugins.jetbrains.com/docs/intellij/multiple-carets.html) — preserving Caret through delegated handlers.

### Secondary (MEDIUM confidence)

- [JetBrains Threading Model](https://plugins.jetbrains.com/docs/intellij/threading-model.html) — UI/write action constraints and lightweight listener guidance.
- [JetBrains Editor Basics](https://plugins.jetbrains.com/docs/intellij/editor-basics.html) — editor API boundary and handler API references.

### Tertiary (LOW confidence)

- None; implementation assumptions are isolated in the Assumptions Log.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — exact build and SDK inspection.
- Architecture: HIGH — derives directly from the locked single-preview/non-mutation requirements and public SDK ownership boundary.
- Pitfalls: HIGH — verified SDK contracts plus official JetBrains document/action guidance.

**Research date:** 2026-09-06  
**Valid until:** 2026-10-06 for the fixed 2026.1 baseline; re-run SDK inspection before changing target platform.
