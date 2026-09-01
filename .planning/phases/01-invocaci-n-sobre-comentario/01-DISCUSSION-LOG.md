# Phase 1: Invocación sobre comentario - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-01
**Phase:** 1-Invocación sobre comentario
**Areas discussed:** Selección válida, Acción no disponible, Menú contextual, Atajo inicial

---

## Selección válida

| Option | Description | Selected |
|--------|-------------|----------|
| Línea completa | Exigir que el usuario seleccione la línea textual completa. | |
| Texto parcial | Permitir cualquier fragmento dentro de un comentario. | |
| Un único comentario PSI con whitespace exterior | Aceptar el comentario completo de línea o bloque y tolerar espacios externos. | ✓ |

**User's choice:** Delegado al agente.
**Notes:** Se eligió la validación semántica más segura y predecible.

---

## Acción no disponible

| Option | Description | Selected |
|--------|-------------|----------|
| Ocultar/deshabilitar | No ofrecer la acción en contexto inválido. | |
| Mostrar y fallar | Mostrar siempre y explicar el fallo al ejecutarla. | |
| Ocultar en menú y notificar desde shortcut | Menú limpio y explicación al ejecutar mediante Keymap. | ✓ |

**User's choice:** Delegado al agente.
**Notes:** Mantiene el menú contextual limpio sin perder feedback para el atajo.

---

## Menú contextual

| Option | Description | Selected |
|--------|-------------|----------|
| Ítem directo | Una acción visible de primer nivel. | ✓ |
| Submenú Codex | Agrupar futuras acciones bajo un submenú. | |
| Menú Tools | Ubicarla fuera del menú contextual del editor. | |

**User's choice:** Delegado al agente.
**Notes:** Un único flujo del MVP no justifica un submenú.

---

## Atajo inicial

| Option | Description | Selected |
|--------|-------------|----------|
| Atajo por defecto | Asignar una combinación inicial. | |
| Sólo Keymap configurable | Registrar sin combinación inicial para que el usuario elija. | ✓ |

**User's choice:** Delegado al agente.
**Notes:** Reduce el riesgo de colisiones con el keymap del usuario.

## the agent's Discretion

- El usuario pidió elegir las decisiones más convenientes y optimizadas.

## Deferred Ideas

None.
