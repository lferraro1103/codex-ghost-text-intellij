# Phase 1: Invocación sobre comentario - Pattern Map

**Mapped:** 2026-09-02  
**Files analyzed:** 10 planned new files (including Gradle wrapper files)  
**Analogs found:** 0 / 10 — the repository contains no source code yet.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `settings.gradle.kts` | config | transform | None (greenfield) | no analog |
| `build.gradle.kts` | config | transform | None (greenfield) | no analog |
| `gradle.properties` | config | transform | None (greenfield) | no analog |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | build tooling | batch | None (greenfield) | no analog |
| `src/main/resources/META-INF/plugin.xml` | config / extension registration | event-driven | None (greenfield) | no analog |
| `src/main/kotlin/com/leandro/codexghosttext/actions/GenerateCodexGhostTextAction.kt` | controller / action | event-driven | None (greenfield) | no analog |
| `src/main/kotlin/com/leandro/codexghosttext/selection/SelectedCommentResolver.kt` | utility | transform | None (greenfield) | no analog |
| `src/test/kotlin/com/leandro/codexghosttext/selection/SelectedCommentResolverTest.kt` | test | transform | None (greenfield) | no analog |
| `src/test/kotlin/com/leandro/codexghosttext/actions/GenerateCodexGhostTextActionTest.kt` | test | event-driven | None (greenfield) | no analog |

## Pattern Assignments

The codebase search (`rg --files`) found only `AGENTS.md` before phase artifacts. There are no project-local Kotlin, Gradle, plugin-descriptor, or IntelliJ test files. The planner must therefore establish the first project conventions from the phase research and the cited official IntelliJ Platform patterns, rather than claim a local analog exists.

### `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, and Gradle wrapper (config / batch)

**Analog:** None in repository.

**Follow:** `01-RESEARCH.md` “Standard Stack” and “Recommended Project Structure”; the project-wide stack in `AGENTS.md`.

**Required build pattern:**

```kotlin
// build.gradle.kts shape — use pinned versions only
plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

// Configure an IntelliJ IDEA Community dependency, platform test framework,
// runIde/test/buildPlugin/verifyPlugin support, and the Java 21 baseline.
// Do not package a second Kotlin stdlib or introduce application libraries.
```

**Compatibility pattern:** The UI contract is authoritative: develop against IntelliJ Platform 2026.1 / Java 21, then verify against IntelliJ 2026.2. Keep the actual platform build and all Gradle/plugin versions as pinned properties, not floating `+` versions. Generate wrapper files through Gradle; do not hand-maintain generated wrapper metadata.

**Validation pattern:** Configure Platform test support from the first build. Run the focused resolver/action tests during development and `test buildPlugin verifyPlugin` at the phase gate.

---

### `src/main/resources/META-INF/plugin.xml` (extension registration / event-driven)

**Analog:** None in repository.

**Follow:** `01-RESEARCH.md` “Pattern 1: Static, fieldless action” (lines 140–160), derived from the IntelliJ plugin configuration descriptor contract.

**Action registration pattern:**

```xml
<actions>
  <action id="com.leandro.codexghosttext.GenerateCodexGhostText"
          class="com.leandro.codexghosttext.actions.GenerateCodexGhostTextAction"
          text="Generate Codex Ghost Text"
          description="Generate a Codex code proposal from the selected comment">
    <add-to-group group-id="EditorPopupMenu" anchor="last"/>
  </action>
</actions>
```

**Keymap contract:** Do not add a `<keyboard-shortcut>` child. Descriptor registration makes the stable action ID discoverable in Settings / Keymap while preserving D-04 (no default binding).

**Safety/ownership:** Register this one permanent action statically. Do not use runtime `ActionManager.registerAction()` and do not create a separate custom menu/key dispatcher.

---

### `src/main/kotlin/com/leandro/codexghosttext/selection/SelectedCommentResolver.kt` (utility / transform)

**Analog:** None in repository.

**Follow:** `01-RESEARCH.md` “Pattern 2: Boundary-based PSI validation” (lines 162–180) and selection cases (lines 276–285).

**Core resolver pattern:**

```kotlin
// Expose a narrow, immutable result (comment TextRange and text) or null.
// Resolve editor + PSI from the AnActionEvent; retain neither in long-lived state.
fun from(event: AnActionEvent): SelectedComment? {
    // 1. Require non-empty contiguous selection and available editor/PSI file.
    // 2. Locate first and last non-whitespace offsets inside [start, end).
    // 3. psiFile.findElementAt(offset) for each boundary.
    // 4. PsiTreeUtil.getParentOfType(..., PsiComment::class.java, false).
    // 5. Require the exact same comment and full comment range in selection.
    // 6. Require only whitespace in the two exterior gaps.
    // 7. Otherwise return null without throwing.
}
```

**Error/validation pattern:** Treat missing editor/PSI, empty or whitespace-only selection, out-of-range offsets, partial comments, multiple comments, and mixed code as ordinary invalid input (`null`), not exceptional states. Do not log selected source text. Do not scan the entire PSI tree or use regex matching.

**Performance pattern:** This runs on the action update hot path. It performs bounded character inspection and two PSI boundary lookups only; no I/O, notifications, state mutation, process calls, or whole-file traversal.

---

### `src/main/kotlin/com/leandro/codexghosttext/actions/GenerateCodexGhostTextAction.kt` (controller / event-driven)

**Analog:** None in repository.

**Follow:** `01-RESEARCH.md` “Pattern 1: Static, fieldless action” (lines 140–160), “Pattern 3: Hidden popup action, guarded shortcut action” (lines 182–186), and code example (lines 250–274).

**Action lifecycle pattern:**

```kotlin
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
            // Issue the native, non-modal notification defined by UI-SPEC.
            return
        }
        // Phase-1 handoff seam only: no Codex call and no document mutation.
    }
}
```

**Presentation and error pattern:** `update()` is pure and fast: it only decides visibility/enabled state. It hides the popup entry when invalid, but leaves it enabled when invoked from an editor so a user-assigned shortcut reaches `actionPerformed()`. `actionPerformed()` always revalidates; invalid input produces exactly `Seleccioná exactamente un comentario para generar código.` through the native non-modal notification mechanism.

**Lifecycle pattern:** Keep the action fieldless. Never retain `Project`, `Editor`, PSI, request, or preview state on the `AnAction` instance. No document write, Codex invocation, credential read, login, or source-text logging belongs in this phase.

---

### `src/test/kotlin/com/leandro/codexghosttext/selection/SelectedCommentResolverTest.kt` (test / transform)

**Analog:** None in repository.

**Follow:** `01-RESEARCH.md` “Validation Architecture” (lines 329–358) and “Selection validation cases” (lines 276–285).

**Fixture pattern:** Use a light IntelliJ PSI fixture (Java is sufficient) rather than tests that only pass raw strings. Mark a contiguous editor selection, obtain PSI-backed context, and assert resolver result/range.

**Required cases:**

```text
valid:   // create user
valid:     // create user\n        (outer whitespace)
valid:   /* create user */
invalid: partial selection inside one comment
invalid: two adjacent comments
invalid: comment plus code
invalid: whitespace-only selection
```

Include boundary cases such as a trailing newline/end-exclusive selection. Each invalid case must assert normal rejection, not an exception or document change.

---

### `src/test/kotlin/com/leandro/codexghosttext/actions/GenerateCodexGhostTextActionTest.kt` (test / event-driven)

**Analog:** None in repository.

**Follow:** `01-RESEARCH.md` “Validation Architecture” lines 340–358 plus `01-UI-SPEC.md` “Interaction Contract”.

**Test pattern:** Verify descriptor/action registration and the runtime presentation contract separately:

```text
valid complete comment => action visible and enabled in editor context
invalid selection       => popup action hidden
invalid shortcut route  => actionPerformed revalidates and emits actionable feedback
registration            => stable action ID exists and has no default shortcut declaration
all phase-1 paths       => no document mutation and no generation/process work
```

Because shortcut dispatch while a popup-hidden action is target-version sensitive, retain a `runIde` manual test: assign a shortcut in Keymap, invoke it with valid and invalid editor selections, and confirm invalid selection shows the native message.

## Shared Patterns

### Native IntelliJ Action System

**Source:** `01-RESEARCH.md` lines 140–160 and 182–186.  
**Apply to:** `plugin.xml`, `GenerateCodexGhostTextAction.kt`, action test.

Use one statically registered, fieldless `DumbAwareAction`, placed directly in `EditorPopupMenu`. Omit a default shortcut and let the standard Keymap provide user configuration. The exact same action is used by popup and shortcut.

### Selection Validation

**Source:** `01-RESEARCH.md` lines 162–180.  
**Apply to:** resolver, action, resolver test, action test.

The authoritative validity rule is PSI based: ignore only exterior whitespace, then require both non-whitespace selection boundaries to belong to the same fully-contained `PsiComment`. Call the resolver from both `update()` and `actionPerformed()`.

### Invalid-State Feedback and Safety

**Source:** `01-UI-SPEC.md` “Copywriting Contract” and “Interaction Contract”.  
**Apply to:** action and action test.

Invalid shortcut invocation uses the platform’s non-modal notification with the exact Spanish copy. The Phase 1 valid path is a seam only: it must not invoke Codex, mutate documents, create ghost text, request credentials, or retain editor/project state.

### Multi-version Compatibility

**Source:** `01-UI-SPEC.md` “Compatibility Contract”.  
**Apply to:** Gradle config and phase verification.

Develop against IntelliJ 2026.1 on Java 21 and verify the packaged plugin and manual behavior against IntelliJ 2026.2. Limit implementation to public Action System and PSI APIs common to both targets; pin all Gradle/plugin/platform coordinates.

## No Analog Found

| File group | Reason |
|---|---|
| All Phase 1 production, test, and Gradle files | The repository is intentionally new and contains only planning metadata and `AGENTS.md`; no source file is available as a local implementation analog. |

The planner should use the official IntelliJ patterns captured in `01-RESEARCH.md`, not fabricate a nonexistent project convention. Phase 1 establishes the initial Kotlin, Gradle, Action System, PSI, and test conventions for later phases.

## Metadata

**Analog search scope:** workspace source tree excluding `.git`; project skill roots `.codex/` and `.agents/` (none present)  
**Files scanned:** 1 non-planning workspace file (`AGENTS.md`)  
**Pattern extraction date:** 2026-09-02
