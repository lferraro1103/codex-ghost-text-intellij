# Phase 2: Ghost text y control de propuesta - Codebase Patterns

**Mapped:** 2026-09-06  
**Scope:** implementation analogs for Phase 2 only.

## Existing Analogues

| Planned responsibility | Closest existing location | Pattern to preserve |
|------------------------|---------------------------|---------------------|
| Trigger action delegates to service | `src/main/kotlin/com/leandro/codexghosttext/actions/GenerateCodexGhostTextAction.kt:12-34` | Keep `DumbAwareAction`; `update()` is cheap and BGT; move preview creation into a service rather than action-static logic. |
| Selection/comment safety snapshot | `src/main/kotlin/com/leandro/codexghosttext/selection/SelectedCommentResolver.kt:13-45` | Re-resolve current input and reject old/uncommitted or foreign document PSI. Do not retain PSI in preview state. |
| Exact UTF-16/EOF test treatment | `src/test/kotlin/com/leandro/codexghosttext/selection/SelectedCommentResolverTest.kt:27-43` | Use light Java fixture, explicit offsets, Unicode, and a stale-document test. |
| User-visible invalid-input notification | `src/main/kotlin/com/leandro/codexghosttext/actions/GenerateCodexGhostTextAction.kt:29-33` | Reuse registered notification group; do not create a second group. The exact existing ID is `"Codex Ghost Text"` [VERIFIED: src/main/resources/META-INF/plugin.xml:9-11 — `<notificationGroup id="Codex Ghost Text" displayType="BALLOON" isLogByDefault="false" />`]. |
| Action/plugin registration tests | `src/test/kotlin/com/leandro/codexghosttext/actions/GenerateCodexGhostTextActionTest.kt:33-40,108-114` | Retrieve the registered action from `ActionManager`; assert stable descriptor behavior separately from service tests. |
| Write-command test idiom | `src/test/kotlin/com/leandro/codexghosttext/selection/SelectedCommentResolverTest.kt:36-43` | Use `WriteCommandAction.runWriteCommandAction(project)` around intentional fixture mutations; Phase 2 production acceptance should use the named builder form to make Undo intent explicit. |

## File Ownership Boundary

| File / area | Phase 2 change |
|-------------|----------------|
| `actions/GenerateCodexGhostTextAction.kt` | On valid selection, call `project.service<GhostPreviewService>().showFixture(...)`; invalid behavior remains as current. |
| New `preview/` package | Own snapshot, limits, block inlay, renderer, cancel/accept semantics, and per-preview disposables. |
| New `editor/` package | Own the application-scoped handler installer and wrappers; it must know no proposal internals beyond service routing. |
| `plugin.xml` | Retain the existing action and notification registration; add only required service registration if annotations are not used. |
| Existing resolver/tests | Extend only where Phase 2 needs comment-end placement validation; do not weaken Phase 1 PSI/document safety guards. |

## Naming and Test Conventions

- Production package root is `com.leandro.codexghosttext` [VERIFIED: src/main/kotlin/com/leandro/codexghosttext/actions/GenerateCodexGhostTextAction.kt:1 — `package com.leandro.codexghosttext.actions`].
- Test classes use `LightJavaCodeInsightFixtureTestCase` and `fun test...()` methods [VERIFIED: src/test/kotlin/com/leandro/codexghosttext/selection/SelectedCommentResolverTest.kt:10-11 — `class SelectedCommentResolverTest : LightJavaCodeInsightFixtureTestCase()` and `fun testAcceptsLineBlockAndDocCommentsWithExteriorWhitespace()`].
- Current plugin action identifier is exactly `"com.leandro.codexghosttext.GenerateCodexGhostText"` [VERIFIED: src/main/resources/META-INF/plugin.xml:13-19 — `<action id="com.leandro.codexghosttext.GenerateCodexGhostText" ...>`].

## Required New Pattern (No In-Repo Analogue)

There is no current inlay, editor-handler, or service implementation in the repository. Follow the Phase 2 research contract instead of inventing a project-local variant:

1. Project service owns one preview and its child disposable.
2. Application service installs Tab/Esc wrappers once and delegates original handlers unless a fresh preview consumes the key.
3. Renderer is presentation-only; acceptance is the sole document write command.
4. Tests use dedicated fakes/recorders for wrapper delegation rather than changing global handlers inside general action tests.
