# Walking Skeleton — Codex Ghost Text for IntelliJ

**Phase:** 1  
**Generated:** 2026-09-02

## Capability Proven End-to-End

An IntelliJ user can select one complete PSI comment and invoke the same safe native command from the editor popup or a user-assigned Keymap shortcut, while the document remains unchanged.

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Framework | Native IntelliJ Platform plugin in Kotlin | The product must live inside the editor and use supported Action System/editor APIs. |
| Build | Gradle Kotlin DSL, generated Gradle 9.0.0 wrapper, IntelliJ Platform Gradle Plugin 2.18.1 | Gives reproducible tests, sandbox, packaging and verifier tasks. |
| Compile baseline | IntelliJ Platform 2026.1 + Java 21 | Matches the approved multiversion policy and available JDK baseline. |
| Compatibility target | Plugin Verifier and manual sandbox on IntelliJ 2026.2 | Proves compatibility without making a Java-25 IDE the compilation baseline. |
| UI entry point | One static, fieldless `DumbAwareAction` registered directly in `EditorPopupMenu` | Reuses native menu, Keymap, focus, accessibility, theme and lifecycle behavior. |
| Selection model | Editor UTF-16 offsets plus PSI `PsiComment` boundary validation | Correctly recognizes language comments without regex or whole-file scanning. |
| State/data layer | None in Phase 1 | The action retains no editor, PSI, document, source text or preview state. |
| Authentication/Codex | No integration in Phase 1; later local App Server seam | Prevents API-key/login scope from entering the editor-action skeleton. |
| Distribution | Local installable ZIP from `buildPlugin` | Fits personal use without Marketplace/signing scope. |
| Directory layout | `actions/`, `selection/`, `META-INF/plugin.xml`, mirrored test packages | Separates presentation/dispatch from pure selection semantics for later phases. |

## Stack Touched in Phase 1

- [x] Project scaffold — pinned Kotlin/Gradle/IntelliJ configuration and generated wrapper
- [x] Native registration — one ActionManager action in `EditorPopupMenu` and Keymap
- [x] Semantic input — PSI-backed complete-comment resolver
- [x] UI interaction — popup/shortcut dispatch and native invalid-selection notification
- [x] Safety — zero document mutation, process invocation, network/API access or retained source state
- [x] Validation — light PSI/action tests, `buildPlugin`, Plugin Verifier and 2026.1/2026.2 sandbox matrix
- [x] Local distribution — installable ZIP and documented `runIde`/sandbox workflow

## Out of Scope (Deferred to Later Slices)

- Ghost preview rendering, lifecycle and cancellation
- `Tab` acceptance, `Esc` dismissal and undoable insertion
- `codex app-server`, existing-account authentication diagnostics and quota handling
- Context bounding, generated-result freshness and editor coexistence hardening
- Automatic typing completion, multiple candidates, privacy settings and broad IDE/version matrix

## Subsequent Slice Plan

- Phase 2: render one non-document ghost proposal and support explicit accept/discard behavior.
- Phase 3: diagnose and connect to the locally authenticated Codex App Server.
- Phase 4: generate a bounded, fresh proposal through Codex while preserving editor safety.
