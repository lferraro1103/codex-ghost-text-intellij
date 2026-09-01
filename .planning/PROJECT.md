# Codex Ghost Text for IntelliJ

## What This Is

Un plugin personal para IntelliJ que conecta con Codex local ya autenticado y transforma comentarios seleccionados en sugerencias de código. El usuario selecciona un comentario, invoca un atajo o una acción del menú contextual, y el plugin muestra un bloque de código transparente inmediatamente debajo del comentario.

## Core Value

Convertir un comentario seleccionado en una propuesta de código insertable con `Tab`, sin salir del editor ni requerir una API key.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] El usuario puede invocar la generación sobre un comentario seleccionado desde IntelliJ.
- [ ] El usuario puede previsualizar el código sugerido como ghost text debajo del comentario sin modificar el archivo.
- [ ] El usuario puede aceptar la sugerencia con `Tab` o descartarla con `Esc`.
- [ ] El plugin usa el Codex App Server local y la sesión normal de Codex del usuario, sin API key.

### Out of Scope

- Autocompletado continuo en cada pulsación de tecla — el MVP se activa manualmente sobre una selección para controlar latencia y uso.
- Backend propio, cuentas de terceros o almacenamiento remoto — el plugin es personal y local.
- Reemplazar o eliminar el comentario fuente — el comentario se conserva y el código se propone debajo.

## Context

La experiencia buscada es similar a Copilot Ghost Text, pero disparada de manera explícita: el usuario subraya un comentario con el mouse y elige una acción de clic derecho o un shortcut. Codex corre localmente mediante su App Server y utiliza la cuenta de Codex ya iniciada; el plugin no maneja credenciales de OpenAI.

## Constraints

- **IDE**: IntelliJ Platform — el resultado debe ser un plugin nativo integrado al editor.
- **Authentication**: Codex local/App Server — sin API key ni OAuth implementado por el plugin.
- **Interaction**: Selección manual + menú contextual o shortcut — el usuario controla cuándo se consulta Codex.
- **Safety**: El archivo no se modifica hasta que el usuario acepte explícitamente con `Tab`.
- **Scope**: Uso personal inicialmente — se prioriza simplicidad y robustez local sobre distribución pública.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Activación manual sobre texto seleccionado | Evita solicitudes al escribir y hace el comportamiento predecible. | — Pending |
| Mostrar el código debajo del comentario | Conserva la intención del usuario y permite revisar antes de insertar. | — Pending |
| Aceptar con `Tab`, descartar con `Esc` | Replica una interacción conocida de ghost text. | — Pending |
| Conectar al Codex App Server local | Usa la sesión normal de Codex sin exponer ni requerir API key. | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `$gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `$gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-08-31 after initialization*
