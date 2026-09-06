---
phase: 02
slug: ghost-text-y-control-de-propuesta
status: draft
shadcn_initialized: false
preset: none
created: 2026-09-06
---

# Phase 2 — Native editor UI contract

## Design System

IntelliJ Platform native editor only; no web framework, registry or new icons. Reuse Phase 1 action and notification group. One project-owned block inlay below the final comment line. No document-backed temporary insertion.

The proposed-code block is the primary focal point after invocation. The source comment is its contextual anchor; Tab/Esc guidance is secondary.

## Spacing Scale

Use editor character columns and editor.lineHeight, not a web pixel scale. Align generated code with its intended insertion indentation. No card border, background, toolbar, or vertical padding; this is an editor-native exception to the 4px spacing grid. Multiline preview has exactly one lineHeight per displayed line.

## Typography

Use the editor's configured plain font and font size, normal weight, lineHeight and baseline ascent. Respect zoom and scheme changes; do not hard-code a font family or CSS-like line-height. Tabs align to the editor's tab size. Preserve Unicode text.

## Color

Use the editor scheme's inline-refactoring/inactive text attributes with a theme-aware fallback, muted but legible. Editor background remains unchanged. No brand/accent or destructive color. Light/dark visual legibility remains a deferred real-IDE check.

Semantic allocation: dominant editor background (60%), existing editor text/chrome/selection owned by IntelliJ (30%), no plugin accent allocation (the remaining share remains native editor surface). Muted preview text is informational, not an interactive accent; percentages are design roles, not forced screen coverage.

## Copywriting Contract

| Element | Copy |
|---|---|
| Trigger | Generate Codex Ghost Text |
| Temporary Phase 2 notice | Vista previa de prueba local: Tab acepta, Esc descarta. |
| Invalid selection | Seleccioná exactamente un comentario para generar código. |
| Incompatible editor | Usá un único cursor en un archivo editable y seleccioná un comentario completo. |
| Unsafe anchor | El comentario debe terminar la línea para insertar código debajo. |
| Empty/oversized output | No hay una propuesta válida para mostrar. Volvé a intentarlo. |

The development proposal is explicitly identified as local demonstration, not attributed to Codex. Phase 4 replaces this fixture. Never show the generated text in a balloon or logs.

## Interaction

Tab accepts only a fresh preview in its owning editor as one undoable command. Esc discards. Original handlers run unchanged without a preview, and active IDE lookup/live-template interactions take priority. Editing, caret/selection movement, editor switching/release, project disposal and another invocation discard. Read-only/viewer/multicaret contexts do not create or accept previews. A stale preview never inserts. The source comment is untouched. Empty and >80-line or >16000-character proposals are rejected as a whole.

## UI Considerations

| Category | Element(s) | Status | Resolution / Reason |
|---|---|---|---|
| loading | interactive-control | ✅ covered | Phase 2 source is synchronous local fixture; no artificial loading state. Later generation owns loading in Phase 4. |
| error | interactive-control | ✅ covered | Invalid/incompatible requests show the corresponding fixed copy and leave document unchanged. |
| overflow | static-content | ✅ covered | Reject oversized proposals, no silent truncation; ordinary long lines use editor horizontal extent/clipping, never add wrapping not present in inserted code. |
| long-text | static-content, interactive-control | ✅ covered | Preserve bounded Unicode/multiline text; use native action label layout and editor font metrics for preview width. |

No data collection/grid/media exists. Zero proposals renders no inlay, one renders one block, replacement disposes the previous block; no multiple-candidate UI. Cancellation does not emit distracting notifications.

## Registry Safety

Not applicable: native IntelliJ APIs, no registries/assets installed.

## Checker Sign-Off

Independent gsd-ui-checker approved 2026-09-06: copywriting, typography, spacing and registry PASS; visuals/color had nonblocking FLAGs for omitted explicit hierarchy/allocation, now documented above. State probe PASS (5/5 resolved). This contract does not claim visual UAT passed.
