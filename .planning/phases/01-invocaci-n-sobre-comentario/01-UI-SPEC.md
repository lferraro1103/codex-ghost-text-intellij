---
phase: 1
slug: invocaci-n-sobre-comentario
status: draft
shadcn_initialized: false
preset: none
created: 2026-09-01
---

# Phase 1 — UI Design Contract

> Contrato visual e interactivo para el punto de entrada del editor. La Fase 1 no genera código, no abre una interfaz propia y no muestra ghost text; esas responsabilidades empiezan en fases posteriores.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none — integración nativa de IntelliJ Platform |
| Preset | not applicable |
| Component library | IntelliJ Action System y notificaciones de la plataforma |
| Icon library | none — no icono propio en esta fase |
| Font | fuente UI activa de IntelliJ; el plugin no la sustituye |
| Theme and scaling | respetar el tema claro/oscuro, alto contraste y escalado de interfaz que el usuario tenga configurado |

No crear paneles Swing, diálogos, CSS, colores, iconos ni controles de teclado personalizados. El único elemento visible propio es una acción registrada en el menú contextual nativo; las notificaciones usan el mecanismo estándar del IDE.

### Compatibility Contract

- La implementación se desarrolla contra IntelliJ Platform **2026.1** con **Java 21**.
- Antes de distribuir o usar una build nueva, ejecutar el verificador y una comprobación manual también contra **IntelliJ 2026.2**. No usar una API visual exclusiva de una de esas versiones.
- Si una versión cambia el comportamiento de una acción oculta invocada por Keymap, preservar el contrato visible: el menú no muestra la acción con selección inválida y el shortcut devuelve una indicación accionable.

---

## Interaction Contract

### Action surface

| Surface | Contract |
|---------|----------|
| Editor popup menu | Mostrar el ítem directo `Generate Codex Ghost Text` dentro de `EditorPopupMenu`, sin submenú ni icono. Aparece únicamente cuando la selección válida contiene exactamente un comentario PSI completo, permitiendo sólo whitespace exterior. |
| Keymap | Registrar la misma acción con el ID estable del plugin y sin atajo predeterminado. Debe poder encontrarse y asignarse desde **Settings / Keymap**. |
| Shortcut with valid selection | Ejecuta la misma ruta de acción que el menú contextual. En esta fase termina en el punto de entrega interno; no inicia Codex, no altera el documento y no muestra ghost text. |
| Shortcut with invalid selection | Mostrar una notificación breve, no modal y accionable. No abrir diálogos ni modificar el editor. |
| Popup with invalid selection | Ocultar el ítem por completo; no mostrar una variante deshabilitada, texto de ayuda ni submenú. |

La validación se repite al ejecutar la acción. `update()` sólo decide presentación y no realiza I/O, notificaciones ni mutación de estado.

### Selection states

| State | Menu presentation | Action result |
|-------|-------------------|---------------|
| Un comentario de línea o bloque completo, con whitespace exterior opcional | Visible y habilitado | Continúa al seam de la Fase 2/4 sin cambiar el documento. |
| Selección vacía o sólo whitespace | Oculto | El shortcut muestra la notificación de selección inválida. |
| Selección parcial de un comentario | Oculto | El shortcut muestra la notificación de selección inválida. |
| Dos o más comentarios, o comentario mezclado con código | Oculto | El shortcut muestra la notificación de selección inválida. |
| Sin editor o PSI disponible | Oculto | El shortcut no falla; muestra la notificación de selección inválida sólo si el Action System lo puede despachar desde un editor. |

### Phase boundary

- No hay estado de carga, spinner, progreso, resultado generado, preview, aceptación con `Tab`, descarte con `Esc` ni inserción de documento.
- El comentario seleccionado se conserva; la Fase 1 no lo reemplaza ni elimina.
- La acción no consulta Codex, no solicita login, no lee credenciales ni muestra estados de cuota. Esos estados pertenecen a las Fases 3 y 4.

---

## Spacing Scale

El plugin no dibuja contenedores ni controla el espaciado de los componentes nativos. Para cualquier texto breve entregado a una notificación nativa, usar únicamente el contenido definido abajo; IntelliJ controla padding, altura, márgenes y touch targets.

Declared reference values (must be multiples of 4):

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4px | Reserva para un futuro renderer nativo de icono/texto; no se usa en esta fase. |
| sm | 8px | Reserva para una futura composición nativa compacta; no se usa en esta fase. |
| md | 16px | Referencia de separación estándar de contenido del IDE; no se fuerza desde el plugin. |
| lg | 24px | Referencia para superficies futuras; fuera de alcance. |
| xl | 32px | Referencia para superficies futuras; fuera de alcance. |
| 2xl | 48px | Referencia para superficies futuras; fuera de alcance. |
| 3xl | 64px | Referencia para superficies futuras; fuera de alcance. |

Exceptions: none. No introducir píxeles fijos en `EditorPopupMenu` ni en notificaciones.

---

## Typography

El plugin hereda la tipografía de UI del IDE. Estas son referencias de jerarquía para contenido futuro y para mantener el copy conciso; no se aplican con CSS ni sustituyen las fuentes del usuario.

| Role | Size | Weight | Line Height |
|------|------|--------|-------------|
| Menu action label | 13px native UI equivalent | 400 | 1.35 |
| Notification body | 13px native UI equivalent | 400 | 1.35 |
| Keymap/action label | 13px native UI equivalent | 400 | 1.35 |
| Settings or section heading (future only) | 16px native UI equivalent | 600 | 1.2 |

Only two weights are permitted: regular (400) and semibold (600). No custom typeface, monospace treatment or editor-font override is allowed in Phase 1.

---

## Color

Hard-coded colors are prohibited because the interaction must adapt to every IntelliJ theme. The 60/30/10 allocation is semantic and is owned by the active IDE theme.

| Role | Value | Usage |
|------|-------|-------|
| Dominant (60%) | Active IntelliJ editor/menu surface | Editor and popup background, entirely platform-owned. |
| Secondary (30%) | Active IntelliJ menu selection/notification surface | Hover, focus, notification container, entirely platform-owned. |
| Accent (10%) | Active IntelliJ action/link accent | Only a native focused/selected menu action and any platform-provided notification link. No custom accent paint. |
| Destructive | not applicable | This phase exposes no destructive action. |

Accent reserved for: the platform focus/selection treatment of `Generate Codex Ghost Text` and a native notification action link if one is introduced later. It is never used as a custom background, badge or status color.

---

## Copywriting Contract

| Element | Copy |
|---------|------|
| Primary CTA | `Generate Codex Ghost Text` |
| Action description for Keymap / Find Action | `Generate a Codex code proposal from the selected comment` |
| Empty state heading | Not applicable — the phase has no collection, panel or generated result. |
| Empty state body | Not applicable — select a comment in the editor to make the contextual action available. |
| Invalid-selection notification | `Seleccioná exactamente un comentario para generar código.` |
| Error state | Not applicable for remote/local failures: no request is made in this phase. An invalid shortcut invocation uses the invalid-selection notification above and leaves the editor unchanged. |
| Destructive confirmation | None — there is no document mutation, deletion or replacement. |

Use Spanish only for the user-facing notification because the project context is Spanish. Keep the action name in English exactly as specified so Keymap, the contextual menu and later documentation refer to the same command.

---

## Accessibility and Keyboard

- The command is discoverable through both the native editor context menu and IntelliJ Keymap/Find Action; it does not depend on mouse-only interaction.
- Do not assign a default shortcut. The user chooses an accessible, non-conflicting shortcut in Keymap.
- The menu label and Keymap description are fixed, concise strings; do not expose source-code text, generated content or dynamic labels in this phase.
- Rely on IntelliJ’s native focus, keyboard navigation, screen-reader metadata, contrast mode and UI scaling. Do not intercept `Tab` or `Esc` in this phase.
- The non-modal notification communicates the correction without stealing editor focus or blocking typing.

---

## UI Considerations

Applicable state considerations resolved: 5 explicit, 0 backstop, 0 unresolved.

| Category | Element(s) | Status | Resolution / Reason |
|----------|------------|--------|---------------------|
| loading | `Generate Codex Ghost Text` action | ✅ explicit | No asynchronous request occurs in Phase 1: la invocación valida y entrega el contexto de forma síncrona; no hay indicador de carga. |
| error | `Generate Codex Ghost Text` action | ✅ explicit | La única invocación no válida tiene una notificación nativa no modal; no se hace una petición local o remota que pueda fallar en esta fase. |
| long-text | Menu label and Keymap description | ✅ explicit | Los textos son fijos y cortos; el texto fuente seleccionado nunca se muestra dentro del control. |
| overflow | Invalid-selection notification | ✅ explicit | El copy es fijo y breve; el renderizado nativo de IntelliJ controla cualquier ajuste o truncado. |
| long-text | Invalid-selection notification | ✅ explicit | No incorpora texto fuente ni contenido generado dinámico; el mensaje se mantiene conciso. |

No form, collection, navigation surface or media exists in this phase; empty, populated, partial, overflow and zero-one-many states are not applicable.

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none | not applicable — this is not a React/web UI |
| third-party | none | no third-party UI block or registry is permitted |

---

## Checker Sign-Off

- [x] Dimension 1 Copywriting: PASS
- [x] Dimension 2 Visuals: PASS
- [x] Dimension 3 Color: PASS
- [x] Dimension 4 Typography: PASS
- [x] Dimension 5 Spacing: PASS
- [x] Dimension 6 Registry Safety: PASS

**Approval:** approved — 2026-09-01
