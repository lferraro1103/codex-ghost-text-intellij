# Roadmap: Codex Ghost Text for IntelliJ

## Overview

El MVP avanza desde la invocación explícita sobre un comentario hasta una sugerencia de Codex local segura y aceptable en el editor. Primero se valida la entrada y el punto de acceso nativo, luego se prueba la experiencia no destructiva de ghost text sin depender de la red, y por último se conecta Codex App Server con límites estrictos y se endurece la convivencia con IntelliJ.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3, 4): planned MVP work.
- Decimal phases: urgent insertions between planned phases, marked `INSERTED`.

- [ ] **Phase 1: Invocación sobre comentario** - Seleccionar un comentario y disparar la acción nativa desde menú o atajo.
- [ ] **Phase 2: Ghost text y control de propuesta** - Implementada; verificación visual/manual diferida.
- [ ] **Phase 3: Acceso local a Codex** - Implementada; ejecución manual y verificador remoto pendientes.
- [ ] **Phase 4: Generación segura y convivencia con el editor** - Entregar código de Codex como preview seguro y fiable dentro del flujo real.

## Phase Details

### Phase 1: Invocación sobre comentario
**Mode:** mvp
**Goal**: El usuario puede iniciar deliberadamente una solicitud desde un único comentario válido del editor.
**Depends on**: Nothing (first phase)
**Requirements**: ACT-01, ACT-02
**Success Criteria** (what must be TRUE):
  1. El usuario puede seleccionar exactamente un comentario y elegir “Generate Codex Ghost Text” desde el menú contextual del editor.
  2. El usuario puede ejecutar la misma acción mediante un atajo configurable en el keymap de IntelliJ.
  3. La acción no se ofrece o informa claramente por qué no puede ejecutarse cuando la selección está vacía, contiene código adicional o no es un único comentario.
**Plans**: TBD
**UI hint**: yes

### Phase 2: Ghost text y control de propuesta
**Mode:** mvp
**Goal**: El usuario puede revisar una propuesta debajo de su comentario y decidir su aplicación sin que el archivo cambie prematuramente.
**Depends on**: Phase 1
**Requirements**: PREV-01, PREV-02, ACPT-01, ACPT-02
**Success Criteria** (what must be TRUE):
  1. El usuario ve una única propuesta de bloque debajo del comentario seleccionado, atenuada y sin cambios en el documento hasta aceptarla.
  2. El comentario fuente permanece intacto tanto mientras se muestra la propuesta como después de aceptarla.
  3. Al pulsar `Tab` con una propuesta actual, el usuario inserta el bloque como un único cambio reversible mediante Undo.
  4. Al pulsar `Esc`, editar, cambiar de selección/editor o iniciar otra generación, la propuesta se descarta; sin propuesta activa, `Tab` y `Esc` conservan el comportamiento nativo del IDE.
**Plans**: TBD
**UI hint**: yes

### Phase 3: Acceso local a Codex
**Mode:** mvp
**Goal**: El usuario puede comprobar y usar de forma segura la disponibilidad de su Codex local autenticado antes de pedir código.
**Depends on**: Phase 2
**Requirements**: CODEX-02
**Success Criteria** (what must be TRUE):
  1. Cuando Codex no está instalado o no puede iniciarse, el usuario recibe un mensaje que indica cómo corregirlo.
  2. Cuando Codex requiere inicio de sesión, se agotó la cuota o falla la conexión local, el usuario recibe un diagnóstico accionable y el editor sigue siendo utilizable.
  3. El plugin se comunica con un proceso `codex app-server` local mediante stdio y no solicita, guarda ni muestra API keys, contraseñas, cookies o tokens.
**Plans**: TBD

### Phase 4: Generación segura y convivencia con el editor
**Mode:** mvp
**Goal**: El usuario puede transformar el comentario seleccionado en una propuesta de código de Codex local, sin conceder capacidades autónomas ni interferir con IntelliJ.
**Depends on**: Phase 3
**Requirements**: CODEX-01, SAFE-01, QUAL-01
**Success Criteria** (what must be TRUE):
  1. Desde un comentario seleccionado, el usuario recibe una propuesta de código generada por Codex App Server usando su sesión normal, sin API key.
  2. Cada solicitud envía sólo el comentario y contexto acotado necesario; no habilita herramientas, shell, comandos, cambios autónomos de archivos ni credenciales administradas por el plugin.
  3. Una respuesta tardía, cancelada o asociada a un documento/editor cambiado nunca aparece ni puede insertarse como propuesta actual.
  4. El flujo funciona de forma predecible junto con archivos de sólo lectura, Undo, cambios de pestaña y estados de completado nativos; ante un estado incompatible, la propuesta se descarta o la acción explica el motivo sin romper el editor.
**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:** Phases execute in numeric order: 1 → 2 → 3 → 4.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Invocación sobre comentario | 0/TBD | Not started | - |
| 2. Ghost text y control de propuesta | 2/2 | Implemented — manual verification deferred | 2026-09-06 |
| 3. Acceso local a Codex | 1/1 | Implemented — runtime/manual and remote verifier deferred | 2026-09-06 |
| 4. Generación segura y convivencia con el editor | 0/TBD | Not started | - |
