# Requirements: Codex Ghost Text for IntelliJ

**Defined:** 2026-08-31
**Core Value:** Convertir un comentario seleccionado en una propuesta de código insertable con `Tab`, sin salir del editor ni requerir una API key.

## v1 Requirements

### Activación

- [ ] **ACT-01**: User can select exactly one comment and invoke “Generate Codex Ghost Text” from the IntelliJ editor context menu.
- [ ] **ACT-02**: User can invoke the same generation action with a configurable keyboard shortcut.

### Vista previa

- [ ] **PREV-01**: User can see one generated code block below the selected comment without the document being modified.
- [ ] **PREV-02**: User's preview is discarded when they edit, change selection/editor, or start another generation.

### Aceptación

- [ ] **ACPT-01**: User can insert a fresh preview with `Tab` as one undoable document change.
- [ ] **ACPT-02**: User can discard a preview with `Esc`, while `Tab` and `Esc` retain normal IDE behavior when no preview is active.

### Codex local

- [ ] **CODEX-01**: User can generate code through a locally launched Codex App Server using the existing Codex login and no API key.
- [ ] **CODEX-02**: User receives an actionable message when Codex is missing, login is required, quota is exhausted, or the local connection fails.

### Seguridad y calidad

- [ ] **SAFE-01**: User's request sends bounded editor context and never enables tools, commands, autonomous file changes, or plugin-managed credentials.
- [ ] **QUAL-01**: User can rely on the plugin alongside normal editor behavior, including read-only files, Undo, tab changes, and native completion states.

## v2 Requirements

### Generación avanzada

- **GEN-01**: User receives automatic suggestions while typing without selecting a comment.
- **GEN-02**: User can regenerate or choose among multiple candidate suggestions.
- **GEN-03**: User can configure privacy exclusions and context/output limits from plugin settings.

### Distribución y compatibilidad

- **DIST-01**: User can use the plugin across a tested matrix of JetBrains IDEs and versions.
- **DIST-02**: User can use a stable native inline-completion presentation if IntelliJ exposes a supported public API.

## Out of Scope

| Feature | Reason |
|---------|--------|
| API key or OpenAI billing integration | The project intentionally uses the normal local Codex session. |
| Backend, telemetry, cloud history, or custom account login | The plugin is personal and local-first. |
| Autonomous agent edits, shell commands, or tool calls | Suggestions must remain reviewable and user-confirmed. |
| Continuous completion on every keystroke | Adds latency and quota pressure; deferred until the explicit selection workflow is proven. |
| Replacing/deleting the selected comment | The comment remains as documentation of user intent. |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| ACT-01 | Unmapped | Pending |
| ACT-02 | Unmapped | Pending |
| PREV-01 | Unmapped | Pending |
| PREV-02 | Unmapped | Pending |
| ACPT-01 | Unmapped | Pending |
| ACPT-02 | Unmapped | Pending |
| CODEX-01 | Unmapped | Pending |
| CODEX-02 | Unmapped | Pending |
| SAFE-01 | Unmapped | Pending |
| QUAL-01 | Unmapped | Pending |

**Coverage:**
- v1 requirements: 10 total
- Mapped to phases: 0
- Unmapped: 10 ⚠️

---
*Requirements defined: 2026-08-31*
*Last updated: 2026-08-31 after initial definition*
