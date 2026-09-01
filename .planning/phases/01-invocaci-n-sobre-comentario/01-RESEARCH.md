# Phase 1: Invocación sobre comentario - Research

**Researched:** 2026-09-01  
**Domain:** IntelliJ Platform editor action, PSI selection validation, plugin scaffold  
**Confidence:** MEDIUM — the APIs and build constraints were checked in current JetBrains documentation; the exact target platform must still be compiled locally after JDK 25 is installed.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

### Selección válida
- **D-01:** La acción acepta una selección contigua que, al ignorar whitespace exterior, corresponde exactamente a un único elemento de comentario reconocido por IntelliJ. Debe admitir comentarios de línea o bloque completos; rechaza selección parcial, varios comentarios o cualquier código mezclado.

### Acción no disponible
- **D-02:** El menú contextual sólo muestra la acción cuando la selección es válida. Si el usuario ejecuta la acción desde un shortcut en un contexto inválido, el plugin muestra una notificación breve y accionable: seleccionar exactamente un comentario.

### Menú contextual
- **D-03:** Usar un ítem directo llamado `Generate Codex Ghost Text` en el menú contextual del editor, no un submenú adicional. Sólo aparece cuando la selección es válida.

### Atajo inicial
- **D-04:** Registrar la acción en IntelliJ Keymap sin un atajo predeterminado para no interferir con combinaciones existentes. El usuario asigna su shortcut desde Settings/Keymap.

### the agent's Discretion
El usuario delegó la optimización de estas decisiones. El planner puede elegir las APIs públicas de IntelliJ que materialicen los contratos anteriores, siempre que `update()` siga siendo barata y sin I/O.

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ACT-01 | User can select exactly one comment and invoke “Generate Codex Ghost Text” from the IntelliJ editor context menu. | Static `plugin.xml` registration in `EditorPopupMenu`; `DumbAwareAction` uses the PSI-backed selection resolver in `update()` and revalidates in `actionPerformed()`. |
| ACT-02 | User can invoke the same generation action with a configurable keyboard shortcut. | Register the same static action without a `<keyboard-shortcut>` child. Its action ID is then available to IntelliJ Keymap settings; retain enablement for editor shortcuts and emit the required invalid-selection notification from `actionPerformed()`. |
</phase_requirements>

## Project Constraints (from AGENTS.md)

- Deliver a native IntelliJ Platform plugin, not a script or external integration.
- The eventual Codex connection must use the locally authenticated App Server; the plugin must not implement API-key, OAuth, or account login flows.
- Invocation is explicitly manual through a selected comment, editor menu, or configurable shortcut.
- The document must remain unchanged until the later explicit `Tab` acceptance flow.
- This personal/local-first plugin prioritizes a small robust implementation over Marketplace concerns.
- This phase is entered through the GSD workflow and covers only selection validation, context-menu action, and Keymap registration; generation, preview, and insertion are deferred.

## Summary

Build Phase 1 as a minimal, static IntelliJ action: Kotlin `DumbAwareAction`, declared in `META-INF/plugin.xml`, placed directly in `EditorPopupMenu`, and with no shortcut declaration. A static action registration is the supported route for a menu item and makes the same action discoverable/configurable by IntelliJ’s action/keymap infrastructure. [CITED: https://plugins.jetbrains.com/docs/intellij/action-system.html] [CITED: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html]

The selection check must be a small PSI resolver, shared by `update()` and `actionPerformed()`. It must derive the first and last non-whitespace characters of the contiguous selection, resolve each to a `PsiComment` ancestor, require that they are the *same* comment, require the comment’s complete `TextRange` to be contained by the selection, and require both outer gaps to be whitespace only. This performs two `PsiFile.findElementAt()` calls rather than scanning the whole file, accepts line/block/doc comments represented as `PsiComment`, and rejects partial or mixed selections. `findElementAt()` returns a leaf; `PsiTreeUtil.getParentOfType()` is the documented way to obtain an exact parent type. [CITED: https://plugins.jetbrains.com/docs/intellij/psi-elements.html]

**Primary recommendation:** Establish the Gradle/Kotlin plugin scaffold and one fieldless `DumbAwareAction`; keep selection parsing pure, cheap, and unit-testable. Before implementation, correct the project stack/environment mismatch: IntelliJ Platform 2026.2 requires Java 25, but this environment currently exposes Java 21 only. [CITED: https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Editor context-menu entry and configurable action | IntelliJ client / Action System | Plugin descriptor | IntelliJ owns presentation, action dispatch, keymaps, and the `EditorPopupMenu` group. |
| Validity of a selected comment | IntelliJ client / PSI | Editor document/selection | PSI recognizes language comments; the editor provides the contiguous range. |
| Invalid shortcut feedback | IntelliJ client / notification UI | — | The action owns immediate context feedback; no Codex process is started in this phase. |
| Code generation, preview, and insertion | Deferred | — | These are explicitly Phase 2–4 responsibilities. |

## Standard Stack

### Core

| Library / Platform | Version | Purpose | Why Standard |
|--------------------|---------|---------|--------------|
| Kotlin JVM Gradle plugin | `2.4.0` | Compile the plugin | Existing project-stack choice; Kotlin 2.x is recommended for plugins targeting 2024.3+ and required for 2025.1+. [CITED: https://plugins.jetbrains.com/docs/intellij/using-kotlin.html] |
| IntelliJ Platform Gradle Plugin | `2.18.1` | Resolve the IDE, run sandbox IDE/tests, package and verify | JetBrains documents `org.jetbrains.intellij.platform` 2.18.1 as the current root-module plugin, with `runIde`, `test`, `buildPlugin`, and `verifyPlugin` tasks. [CITED: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html] |
| IntelliJ Platform Action System | Target IDE bundled | Invoke the command | `DumbAwareAction`, `AnActionEvent`, `Presentation`, and `ActionUpdateThread` are public platform APIs intended for actions. [CITED: https://plugins.jetbrains.com/docs/intellij/action-system.html] |
| Core PSI | Target IDE bundled | Identify a language-recognized comment | `PsiFile.findElementAt`, `PsiComment`, `TextRange`, and `PsiTreeUtil` avoid language-specific parsers. [CITED: https://plugins.jetbrains.com/docs/intellij/psi-elements.html] |

### Supporting

| Library / Platform | Version | Purpose | When to Use |
|--------------------|---------|---------|-------------|
| IntelliJ Platform test framework | Target-matched | Fast PSI/action tests | Add `TestFrameworkType.Platform`; add `TestFrameworkType.Plugin.Java` only for Java PSI fixture tests. [CITED: https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html] [CITED: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-testing-extension.html] |
| Java toolchain | `25` | Compile/run against IntelliJ 2026.2 | Required if retaining the project’s selected `2026.2` platform target. [CITED: https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Static `<action>` registration | Runtime `ActionManager.registerAction()` | Runtime registration adds lifecycle/unregistration work and is unnecessary for a permanent menu/keymap action. Use descriptor registration. [CITED: https://plugins.jetbrains.com/docs/intellij/action-system.html] |
| PSI-backed `PsiComment` validation | Regexes over `selectedText` | Regexes cannot reliably know language grammar, doc comments, or whether a comment is complete. |
| Target 2026.2 + JDK 25 | Retarget 2026.1 + JDK 21 | Retargeting is viable only as an explicit project decision; it diverges from the recorded stack target. |

**Installation / build setup:** Do not add application libraries in this phase. Create the Gradle wrapper at version 9.0.0 or later and configure JDK 25 before compiling the pinned 2026.2 target. The Platform Gradle Plugin documentation lists Gradle 9.0.0 and Java 17 as its own minimums, while the target-platform table raises 2026.2 runtime compatibility to Java 25. [CITED: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html] [CITED: https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html]

## Package Legitimacy Audit

Not applicable: Phase 1 introduces no npm/PyPI/crates dependency. It relies on the IntelliJ Platform Gradle Plugin and Kotlin Gradle plugin already selected in the project stack, resolved from official Gradle/JetBrains repositories.

## Architecture Patterns

### System Architecture Diagram

```text
Mouse-selected text / configured shortcut
                 |
                 v
  GenerateCodexGhostTextAction (Action System)
                 |
                 +--> update() on BGT
                 |      |
                 |      v
                 |  SelectedCommentResolver
                 |      |
                 |      +--> Editor SelectionModel
                 |      +--> PsiFile + PsiComment
                 |      |
                 |      v
                 |  visible in EditorPopupMenu only if valid
                 |
                 +--> actionPerformed()
                        |
                        +--> revalidate; invalid => actionable notification
                        +--> valid => Phase-2/4 handoff seam only (no document mutation)
```

### Recommended Project Structure

```text
.
├── build.gradle.kts                         # pinned IntelliJ/Kotlin build definition
├── gradle.properties                        # platform version and JVM/stdlib settings
├── settings.gradle.kts                      # plugin name and repositories
├── src/main/kotlin/com/leandro/codexghosttext/
│   ├── actions/GenerateCodexGhostTextAction.kt
│   └── selection/SelectedCommentResolver.kt
├── src/main/resources/META-INF/plugin.xml   # plugin/action registration
└── src/test/kotlin/com/leandro/codexghosttext/
    └── selection/SelectedCommentResolverTest.kt
```

### Pattern 1: Static, fieldless action

**What:** Register one action in `plugin.xml`; extend `DumbAwareAction`; put no state, editor, project, or service in action fields.

**When to use:** This permanent editor command must be visible to Keymap and editor popups for the life of the IDE.

**Why:** Action instances can live for the application lifetime; JetBrains warns that action fields holding shorter-lived objects leak projects. The action framework requires `actionPerformed()` and recommends a fast `update()`; `DumbAwareAction` is the supported form for dumb-mode availability. [CITED: https://plugins.jetbrains.com/docs/intellij/action-system.html]

```xml
<!-- Source pattern: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html -->
<actions>
  <action id="com.leandro.codexghosttext.GenerateCodexGhostText"
          class="com.leandro.codexghosttext.actions.GenerateCodexGhostTextAction"
          text="Generate Codex Ghost Text"
          description="Generate a Codex code proposal from the selected comment">
    <add-to-group group-id="EditorPopupMenu" anchor="last"/>
  </action>
</actions>
```

Do **not** add `<keyboard-shortcut>`: the element is optional, and omitting it preserves D-04 while IntelliJ exposes the registered action for user assignment in Keymap. [CITED: https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html]

### Pattern 2: Boundary-based PSI validation

**What:** Resolve only the first and last non-whitespace offsets inside the selected range, then prove both are in one complete `PsiComment`.

**When to use:** In `update()` and again in `actionPerformed()` for every explicit request.

**Algorithm (prescriptive):**

1. Require a non-empty `SelectionModel` range and a non-null `Editor` and `PsiFile` from `AnActionEvent`.
2. Find the first and last selected characters for which `Char.isWhitespace()` is false. If either is absent, reject.
3. At each offset call `psiFile.findElementAt(offset)`, then call `PsiTreeUtil.getParentOfType(leaf, PsiComment::class.java, false)`.
4. Reject unless both resolved parents are non-null and refer to the same `PsiComment`.
5. Reject unless `selectionStart <= comment.textRange.startOffset` and `comment.textRange.endOffset <= selectionEnd`.
6. Reject unless document characters in `[selectionStart, comment.startOffset)` and `[comment.endOffset, selectionEnd)` are each whitespace only.
7. Return an immutable value containing the comment `TextRange` and text; do not retain PSI/editor references beyond the current invocation.

This respects `TextRange`’s end-exclusive selection convention, accepts surrounding spaces/newlines, and detects code/multiple-comment/partial-comment selections without a whole-file PSI traversal. It uses the documented PSI leaf-to-parent lookup pattern. [CITED: https://plugins.jetbrains.com/docs/intellij/psi-elements.html]

### Pattern 3: Hidden popup action, guarded shortcut action

**What:** In `update()`, make the action visible only for a valid selection, but keep it enabled for an editor context so a user-configured shortcut can reach `actionPerformed()` and receive D-02 feedback. `actionPerformed()` always repeats validation before any handoff.

**When to use:** The action appears in an editor popup and is configurable in Keymap.

**Implementation note:** `getActionUpdateThread()` returns `ActionUpdateThread.BGT` because the check reads PSI; BGT gives read access to PSI/VFS/project models. Do not touch Swing components there. The action’s `update()` is called frequently and must have no I/O, subprocess call, notification, full-file scan, or state mutation. [CITED: https://plugins.jetbrains.com/docs/intellij/action-system.html]

### Anti-Patterns to Avoid

- **Selection regex only:** It will treat language text resembling a comment as a comment and cannot prove the full PSI comment was selected.
- **Whole-file `PsiTreeUtil.collectElementsOfType` in `update()`:** It scales with file size and violates the strict hot-path budget; inspect only selection boundaries.
- **Side effects in `update()`:** Do not start Codex, write a file, log source text, or create notifications while IntelliJ repeatedly recalculates presentation.
- **Stateful action singleton:** Do not store preview/request/editor/project fields on `AnAction`; later state belongs in a project service.
- **Default keyboard shortcut:** It violates D-04 and risks collisions with a user/IDE keymap.
- **Document mutation in this phase:** It violates the safety constraint and belongs solely to Phase 2 acceptance.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Editor menu and shortcut dispatch | Swing listeners or a custom key dispatcher | IntelliJ Action System + `plugin.xml` | It supplies context, menu placement, Keymap integration, enablement, and IDE lifecycle behavior. |
| Language comment recognition | Regex or ad-hoc lexer | PSI `PsiComment` | PSI knows the active language’s token structure. |
| Editor context acquisition | Global active-editor lookup | `AnActionEvent` + `CommonDataKeys.EDITOR` / `PSI_FILE` | The action event represents the actual invocation context. [CITED: https://plugins.jetbrains.com/docs/intellij/action-system.html] |
| Test IDE/sandbox setup | Bespoke launch scripts | IntelliJ Platform Gradle Plugin tasks | The plugin supplies `runIde`, `test`, `buildPlugin`, and `verifyPlugin`. [CITED: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-plugins.html] |

**Key insight:** The IntelliJ Platform already provides the stable seams for the whole phase. The only custom logic should be the narrow semantic contract “this contiguous selection is exactly one complete `PsiComment`, ignoring exterior whitespace.”

## Common Pitfalls

### Pitfall 1: Stale Java baseline

**What goes wrong:** The current project stack couples IntelliJ 2026.2 with Java 21, then Gradle/IDE indexing fails because 2026.2 is a Java 25 platform.

**Why it happens:** The prior stack research was made before the current build-number documentation changed.

**How to avoid:** Keep target `2026.2` and install/configure JDK 25 before the first Gradle build, or explicitly revise both target and stack to 2026.1/JDK 21. Do not silently mix them. [CITED: https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html]

**Warning signs:** `Unsupported class file major version`, Gradle resolving but tests not launching, or `runIde` refusing the platform runtime.

### Pitfall 2: Hiding also disables a Keymap command

**What goes wrong:** Setting both `visible=false` and `enabled=false` on invalid selection prevents `actionPerformed()` from emitting the required shortcut guidance.

**How to avoid:** Separate popup visibility from the shortcut execution guard; revalidate in `actionPerformed()` and cover it by a manual sandbox test.

**Warning signs:** An assigned shortcut does nothing on an invalid selection.

### Pitfall 3: Selection offsets at whitespace boundaries

**What goes wrong:** A line comment with a selected trailing newline, leading indentation, or end-exclusive `selectionEnd` is incorrectly rejected or crashes at EOF.

**How to avoid:** Find non-whitespace offsets inside `[start, end)`, use the last non-whitespace character rather than `end - 1`, and bounds-check every offset.

**Warning signs:** `// todo` only works when selected pixel-perfectly, or fails with trailing newline.

### Pitfall 4: Action update performance / leaks

**What goes wrong:** The menu freezes on large files or the project cannot be collected after close.

**How to avoid:** Make two boundary PSI lookups on BGT and retain no short-lived object in action fields. JetBrains explicitly requires fast updates and warns against action fields. [CITED: https://plugins.jetbrains.com/docs/intellij/action-system.html]

### Pitfall 5: Testing only a text matcher

**What goes wrong:** Unit tests pass while a Java/Kotlin block comment is not actually represented/located as expected in the configured platform.

**How to avoid:** Keep pure range tests fast, and add at least one light PSI fixture test using a supported language plugin (Java is sufficient for the Phase 1 contract). JetBrains recommends light tests when possible and requires explicit test-framework dependencies. [CITED: https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html]

## Code Examples

### Action skeleton

```kotlin
// API pattern source: https://plugins.jetbrains.com/docs/intellij/action-system.html
class GenerateCodexGhostTextAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val valid = SelectedCommentResolver.from(event) != null
        event.presentation.isVisible = valid
        event.presentation.isEnabled = event.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val selection = SelectedCommentResolver.from(event)
        if (selection == null) {
            // Phase 1 notification: "Seleccioná exactamente un comentario."
            return
        }
        // Handoff seam only. Do not start Codex or edit a document in this phase.
    }
}
```

The exact notification helper may be chosen during implementation; the important contract is no I/O in `update()`, revalidation on execution, and no action fields. [CITED: https://plugins.jetbrains.com/docs/intellij/action-system.html]

### Selection validation cases

| Selected text | Expected |
|---------------|----------|
| `// create user` | valid |
| `  // create user\n` | valid |
| `/* create user */` | valid |
| `create user` inside `// create user` | invalid (partial comment) |
| `// one\n// two` | invalid (two comments) |
| `// one\nval x = 1` | invalid (code mixed) |
| whitespace only | invalid |

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Gradle IntelliJ Plugin `org.jetbrains.intellij` 1.x | IntelliJ Platform Gradle Plugin `org.jetbrains.intellij.platform` 2.x | Use 2.x for 2024.2+; 1.x is obsolete. [CITED: https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html] |
| Implicit action update thread | Explicit `getActionUpdateThread()` | Current SDK inspection expects an explicit BGT/EDT choice for target 2022.3+. [CITED: https://plugins.jetbrains.com/docs/intellij/action-system.html] |

**Deprecated/outdated:** Do not use the old `org.jetbrains.intellij` Gradle plugin or deprecated project components for this scaffold. The current project stack already chooses the 2.x plugin and services for later lifecycle state.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | A hidden but enabled action remains dispatchable through a user-assigned Keymap shortcut on the target 2026.2 IDE. | Architecture Pattern 3 | D-02 feedback would not appear; validate in `runIde` and adjust presentation policy if needed. |
| A2 | Java PSI light-fixture tests can be supplied through `TestFrameworkType.Plugin.Java` with the selected IDEA target. | Validation Architecture | Tests may need a target-specific bundled Java plugin dependency. |

## Open Questions

1. **JDK 25 provisioning**
   - What we know: the workspace has Temurin Java 21 only; target 2026.2 requires Java 25. [CITED: https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html]
   - What's unclear: whether the user wants JDK 25 installed or wants an explicit retarget to 2026.1.
   - Recommendation: retain the approved 2026.2 target and add JDK 25 as Wave 0 prerequisite; stop before implementation if it cannot be provisioned.

2. **Shortcut behavior while popup-hidden**
   - What we know: D-02 requires actionable feedback for invalid shortcut context.
   - What's unclear: exact visibility/dispatch interaction in the pinned target build.
   - Recommendation: add a `runIde` manual acceptance check before declaring ACT-02 complete; the fallback is a visible-but-disabled-free action only in Keymap/Find Action, never in `EditorPopupMenu`.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| Java / JDK 25 | IntelliJ IDEA 2026.2 build and test target | ✗ | Java 21.0.12 installed | Explicitly retarget to 2026.1 only if user approves; otherwise install JDK 25. |
| Git | Project scaffold/docs commits | ✓ | 2.53.0 | — |
| Gradle wrapper | Repeatable build | ✗ (not yet created) | — | Generate wrapper in Phase 1 after JDK decision. |
| Network access to JetBrains/Gradle repositories | IDE and Gradle dependency resolution | Not probed | — | Required for first build unless dependencies are cached. |

**Missing dependencies with no fallback:** JDK 25 while retaining 2026.2.

**Missing dependencies with fallback:** Gradle is intentionally supplied by the generated wrapper; target can be revised to 2026.1/JDK 21 only by explicit decision.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | IntelliJ Platform test framework, light tests preferred [CITED: https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html] |
| Config file | `build.gradle.kts` — create in Wave 0 |
| Quick run command | `./gradlew.bat test --tests "*SelectedCommentResolverTest"` |
| Full suite command | `./gradlew.bat test buildPlugin verifyPlugin` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ACT-01 | Direct editor-popup action only resolves one complete PSI comment, allowing outer whitespace | Light PSI fixture + manual `runIde` popup check | `./gradlew.bat test --tests "*SelectedCommentResolverTest"` | ❌ Wave 0 |
| ACT-02 | Registered action has no default shortcut and invalid execution reports guidance | Action-registration test + manual Keymap sandbox check | `./gradlew.bat test --tests "*GenerateCodexGhostTextActionTest"` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew.bat test`
- **Per wave merge:** `./gradlew.bat test buildPlugin`
- **Phase gate:** `./gradlew.bat test buildPlugin verifyPlugin`, then manual sandbox checks: valid line/block comment popup, no popup for invalid selection, assign shortcut in Keymap, invalid shortcut notification.

### Wave 0 Gaps

- [ ] Generate Gradle wrapper and JDK-25-compatible `build.gradle.kts`, `settings.gradle.kts`, and `gradle.properties`.
- [ ] Add `TestFrameworkType.Platform`; add `TestFrameworkType.Plugin.Java` if Java PSI fixture is used. [CITED: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-testing-extension.html]
- [ ] Add `SelectedCommentResolverTest` for all validation table cases.
- [ ] Add `GenerateCodexGhostTextActionTest` for registration/no-default-shortcut/revalidation policy.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | No | No authentication is added in this phase. |
| V3 Session Management | No | No session state is added in this phase. |
| V4 Access Control | No | The action operates only on the current local editor context. |
| V5 Input Validation | Yes | Validate editor/PSI availability, non-empty bounded selection, one complete `PsiComment`, and whitespace-only exterior. |
| V6 Cryptography | No | No secrets or cryptography are added. |

### Known Threat Patterns for this phase

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malformed/stale editor selection causing exceptions | Denial of service | Bounds-check offsets; return invalid instead of throwing; revalidate immediately before action handoff. |
| Source text leaked through diagnostics | Information disclosure | Do not log selected comment text; log only action/validation status. |
| Accidental document mutation | Tampering | Phase 1 contains no write action, document API mutation, process call, or generation implementation. |

## Sources

### Primary (MEDIUM confidence — official docs retrieved through web search)

- [JetBrains Action System](https://plugins.jetbrains.com/docs/intellij/action-system.html) — `DumbAwareAction`, action lifetime, update/threading, registration, presentation semantics.
- [JetBrains Plugin Configuration File](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html) — action, `add-to-group`, and optional `keyboard-shortcut` descriptor syntax.
- [JetBrains PSI Elements](https://plugins.jetbrains.com/docs/intellij/psi-elements.html) — leaf `findElementAt` and `PsiTreeUtil.getParentOfType` parent lookup.
- [JetBrains IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) — root plugin ID, repositories, target IDE dependency, and local IDE option.
- [JetBrains Build Number Ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html) — 2026.2 / Java 25 and 2026.1 / Java 21 compatibility table.
- [JetBrains Kotlin Support](https://plugins.jetbrains.com/docs/intellij/using-kotlin.html) — Kotlin 2.x guidance and bundled Kotlin/coroutines compatibility policy.
- [JetBrains Light and Heavy Tests](https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html) — light-test preference and explicit Java test framework dependency.
- [JetBrains Testing Extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-testing-extension.html) — test-framework configuration and isolated test sandboxes.

### Secondary

- [JetBrains IntelliJ SDK Code Samples](https://github.com/JetBrains/intellij-sdk-code-samples) — official maintained examples containing editor/action patterns.

## Metadata

**Confidence breakdown:**

- Standard stack: MEDIUM — official current Gradle/Java docs confirm the build choices, but the stored 2026.2/JDK 21 combination must be corrected before a local proof build.
- Architecture: MEDIUM — public Action System and PSI APIs directly support the recommended design; keyboard dispatch while hidden is flagged for sandbox validation.
- Pitfalls: MEDIUM — based on official action lifecycle/threading guidance and the concrete local JDK mismatch.

**Research date:** 2026-09-01  
**Valid until:** 2026-10-01, unless the target IntelliJ platform is changed.
