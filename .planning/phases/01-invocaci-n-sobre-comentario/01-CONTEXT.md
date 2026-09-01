# Phase 1: Invocación sobre comentario - Context

**Gathered:** 2026-09-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Entregar el punto de entrada nativo del editor para iniciar deliberadamente una solicitud desde un único comentario válido. Esta fase cubre únicamente validación de selección, el ítem del menú contextual y el registro de la acción en Keymap; no genera, previsualiza ni inserta código.

</domain>

<decisions>
## Implementation Decisions

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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Alcance y requisitos
- `.planning/PROJECT.md` — objetivo central, restricciones de integración local y límites de seguridad.
- `.planning/REQUIREMENTS.md` — ACT-01 y ACT-02 son los requisitos cubiertos por esta fase; el resto queda fuera de alcance.
- `.planning/ROADMAP.md` — objetivo, dependencia y criterios de éxito de la Fase 1.

### Investigación técnica
- `.planning/research/SUMMARY.md` — stack recomendado; acción `DumbAwareAction`, menú `EditorPopupMenu`, Keymap y la regla de no hacer I/O en `update()`.
- `.planning/research/STACK.md` — detalles de la plataforma IntelliJ, versiones y Action System.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Ninguno: el proyecto comienza sin código de plugin.

### Established Patterns
- Ninguno: esta fase establecerá el scaffold y el patrón de acción de editor.

### Integration Points
- El nuevo plugin se integra con el Action System de IntelliJ mediante el menú contextual del editor y una acción registrable en Keymap.

</code_context>

<specifics>
## Specific Ideas

- La interacción nace de subrayar un comentario con el mouse y elegir clic derecho o un shortcut.
- El flujo posterior conservará el comentario y mostrará el código debajo como ghost text; esa visualización pertenece a la Fase 2.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 1-Invocación sobre comentario*
*Context gathered: 2026-09-01*
