# Project Research Summary

**Project:** Codex Ghost Text for IntelliJ  
**Domain:** Plugin personal nativo de IntelliJ para sugerencias de código asistidas por Codex local  
**Researched:** 2026-08-31  
**Confidence:** MEDIUM

## Executive Summary

Codex Ghost Text for IntelliJ debe construirse como un plugin nativo de IntelliJ Platform en Kotlin, no como un script o servicio externo. La interacción del MVP es deliberadamente manual: el usuario selecciona exactamente un comentario, invoca una acción desde el menú contextual o un atajo, y ve un bloque de código atenuado debajo del comentario. El archivo permanece intacto hasta que el usuario lo confirma con `Tab`; `Esc` o cualquier cambio relevante descarta la propuesta.

La integración correcta para usar la cuenta normal de Codex sin API key es lanzar `codex app-server` como proceso hijo y hablar JSON-RPC por `stdio`. El plugin debe conservar límites estrictos: hilo efímero por solicitud, contexto acotado, sandbox de sólo lectura, `approvalPolicy: "never"`, sin herramientas, sin comandos y sin acceso amplio al workspace. Así se obtiene una sugerencia revisable, no un agente que modifica el proyecto.

El mayor riesgo técnico no es generar código: es preservar la semántica del editor. Las solicitudes deben correr fuera del EDT, las propuestas atrasadas deben invalidarse, y `Tab`/`Esc` nunca deben secuestrar comportamientos nativos cuando no existe una sugerencia válida. Para el MVP, un block inlay es más estable que depender de APIs de inline completion parcialmente internas o experimentales; se puede migrar a una presentación más nativa después de validar compatibilidad.

## Key Findings

### Recommended Stack

El stack recomendado es un proyecto Gradle de módulo único con Kotlin y las APIs actuales de IntelliJ Platform. Se apunta inicialmente a una única versión comprobada de IntelliJ IDEA, se verifica con Plugin Verifier y se evita prometer compatibilidad amplia hasta probarla. La integración Codex debe quedar detrás de una interfaz de transporte para poder probarla sin sesión ni cuota reales.

**Core technologies:**

- **Kotlin 2.4.0 + JVM 21:** implementación nativa compatible con IntelliJ 2026.1+; no empaquetar una segunda stdlib.
- **Gradle Kotlin DSL + IntelliJ Platform Gradle Plugin 2.18.1:** build, sandbox IDE, packaging y verificación del plugin; no usar el plugin Gradle IntelliJ 1.x obsoleto.
- **IntelliJ IDEA Community 2026.2.0.1 como baseline inicial:** plataforma de desarrollo concreta; fijar `sinceBuild` sólo tras compilar y verificar.
- **`codex app-server --stdio` + JSON-RPC JSONL:** bridge local autenticado mediante la sesión existente de Codex; no usar WebSocket, API key ni backend propio.
- **`kotlinx-serialization-json` 1.9.0:** envelopes JSON-RPC tolerantes a cambios de protocolo, usando tipos JSON flexibles.
- **Servicios por proyecto, coroutines y block inlays:** ciclo de vida, I/O cancelable y preview multilineal no destructivo debajo del comentario.
- **IntelliJ Platform Test Framework + fake `CodexTransport`:** pruebas deterministas de cancelación, staleness e inserción, sin depender de Codex en vivo.

### Expected Features

**Must have (table stakes):**

- Acción única de editor disponible por menú contextual y atajo configurable.
- Validación de que la selección es exactamente un comentario; rechazar vacío, código, archivos readonly o estados no aptos.
- Estado de generación cancelable y no destructivo, con mensajes claros para Codex ausente, login requerido, cuota agotada o error.
- Preview de un único bloque ghost debajo del comentario, sin modificar el documento.
- `Tab` para insertar una vez y de forma undoable; `Esc`, edición, cambio de selección/editor o nueva solicitud para descartar.
- Uso de Codex App Server local y de la sesión existente, sin almacenar API key, OAuth, cookies ni tokens.

**Should have (post-MVP):**

- Prompt con contexto local mínimo, límites configurables y exclusiones de privacidad.
- Regenerar la propuesta desde el mismo comentario.
- Diagnóstico local de versión, estado y errores sin registrar código sensible.
- Formateo opcional posterior a una aceptación exitosa.

**Defer (v2+):**

- Autocompletado automático en cada pulsación.
- Múltiples candidatos, aceptación parcial, chat integrado o historial remoto.
- Backend, telemetría o autenticación propia.
- Compatibilidad declarada con múltiples IDEs/ramas de JetBrains y uso directo de inline completion experimental.

### Architecture Approach

La arquitectura debe separar por completo la UI del editor, la instantánea de contexto, la coordinación de estado y el proceso Codex. Una `SuggestionSession` por editor es la unidad de consistencia; posee un `sessionId`, snapshot inmutable, marcador de inserción y un preview temporal. El coordinador sólo instala el preview cuando la sesión sigue vigente, y sólo la acción de aceptación puede modificar el documento.

**Major components:**

1. **`GenerateFromCommentAction`:** registra menú/shortcut, valida barato el contexto y delega el inicio; nunca hace I/O en `update()`.
2. **`ContextSnapshotter` + `PromptBuilder`:** capturan comentario, contexto acotado, lenguaje, indentación, `modificationStamp` e inserción; construyen una petición de sólo código tratando la selección como datos no confiables.
3. **`SuggestionCoordinator` / `SuggestionSession`:** máquina de estados por editor, ownership de cancelación, staleness y filtrado de eventos tardíos.
4. **`CodexAppServerClient` implementando `CodexTransport`:** inicia el proceso local, hace handshake, serializa JSONL, correlaciona request/thread/turn y expone interrupción; los tests usan un fake.
5. **`GhostSuggestionPresenter`:** muestra y elimina un block inlay; no conoce el transporte y jamás edita el documento.
6. **Acciones condicionales de aceptar/descartar:** consumen `Tab`/`Esc` sólo ante una propuesta fresca del editor enfocado; de lo contrario delegan al IDE.
7. **Settings no secretas:** ruta del ejecutable, límites de contexto/salida y diagnósticos; nunca credenciales.

### Critical Pitfalls

1. **Bloquear el EDT o sostener PSI durante la solicitud:** capturar una instantánea breve, liberar la lectura y ejecutar proceso/streaming en coroutines de fondo; volver a UI sólo para inlay y aceptación.
2. **Aceptar una propuesta vieja o en un offset incorrecto:** asociar `sessionId`, editor, documento, rango, `RangeMarker` y `modificationStamp`; revalidar todos antes de insertar y descartar ante cualquier discrepancia.
3. **Modificar el archivo para simular ghost text:** usar block inlay descartable; sólo `Tab` hace una única write action dentro de un comando undoable.
4. **Romper `Tab` o `Esc` nativos:** interceptarlos únicamente cuando la propuesta actual pertenece al editor activo y no hay lookup/modal prioritario; delegar siempre en el resto de casos.
5. **Convertir App Server en un agente con acceso al repo:** CWD controlado, contexto pequeño, sandbox más restrictivo disponible, `approvalPolicy: "never"`, sin RPC de comandos/procesos/shell ni tool calls; cancelar eventos inesperados.
6. **Filtrar autenticación o abrir un puerto local:** proceso hijo por stdio, stdout sólo para JSONL y stderr sólo para logs acotados; Codex conserva su sesión y el plugin nunca toca archivos de auth.
7. **Acoplarse a APIs o schemas cambiantes:** baseline corto de IDE/CLI, handshake y comprobación de capacidades al inicio, schema generado por el binario probado y Plugin Verifier antes de ampliar versiones.

## Implications for Roadmap

### Phase 1: Baseline del plugin y comando seguro

**Rationale:** antes de integrar Codex hay que fijar una plataforma verificable y confirmar que la UX se puede activar desde el editor sin coste ni dependencia externa.

**Delivers:** proyecto Gradle/Kotlin compilable en sandbox IntelliJ, metadata, acción “Generate Codex Ghost Text” en `EditorPopup` y Keymap, y validación de una selección que sea un comentario único.

**Addresses:** invocación manual por menú/shortcut y selección segura.

**Avoids:** rangos de compatibilidad ficticios, trabajo costoso en `AnAction.update()` y generación sobre texto accidental.

### Phase 2: Preview local no destructivo y aceptación segura

**Rationale:** el valor central y el riesgo principal están en la experiencia editor; debe validarse sin latencia, cuota ni complejidad de protocolo.

**Delivers:** máquina de sesión por editor, snapshot/freshness checks, block inlay con candidato estático, `Tab` que inserta debajo del comentario como una única operación undoable y `Esc`/edición que descartan.

**Addresses:** ghost text, conservar comentario, aceptar con `Tab` y descartar con `Esc`.

**Avoids:** modificaciones antes de aceptar, offsets obsoletos, inlays huérfanos y secuestro global de teclas.

### Phase 3: Bridge seguro de Codex App Server

**Rationale:** la autenticación local y el protocolo asíncrono son un límite técnico independiente del editor; deben ser testeables y fallar de forma explícita.

**Delivers:** `CodexTransport`, cliente `codex app-server --stdio`, handshake/capability check, parser JSONL con correlación de IDs, gestión de proceso por proyecto, fake transport y diagnósticos de ejecutable, login, cuota, overload y protocolo.

**Uses:** Kotlin coroutines, `kotlinx-serialization-json`, servicios de proyecto y schema generado por el Codex CLI de referencia.

**Implements:** límite local de proceso/transporte de la arquitectura.

**Avoids:** WebSocket expuesto, manejo de tokens, parsing de stderr como protocolo y fugas de procesos.

### Phase 4: Generación con contexto mínimo, streaming y cancelación

**Rationale:** una vez estables UI y transporte, se conectan sin introducir privilegios de agente ni resultados tardíos.

**Delivers:** snapshot acotado, prompt de “sólo código”, hilo efímero por solicitud, sandbox mínimo, `approvalPolicy: "never"`, acumulación limitada de deltas, normalización de salida y `turn/interrupt` al invalidar/cancelar.

**Addresses:** generación mediante la cuenta normal de Codex, progreso cancelable y errores accionables.

**Avoids:** prompt injection, herramientas/comandos, contexto excesivo, reintentos agresivos y propuestas desactualizadas.

### Phase 5: Hardening, compatibilidad y prueba end-to-end

**Rationale:** la primera entrega personal sólo es utilizable si coexiste con el editor real y resiste variantes de ciclo de vida/versión.

**Delivers:** matriz de pruebas con lookup, live templates, multi-caret, archivos readonly, cambios de pestaña, undo, temas/escala, cierre de proyecto, sobrecarga/cuota, pruebas de seguridad adversarial y Plugin Verifier para las versiones declaradas.

**Addresses:** fiabilidad, accesibilidad y límites reales de compatibilidad.

**Avoids:** regresiones de atajos, visuales ilegibles, leaks de recursos y soporte no probado.

### Phase Ordering Rationale

- La acción y el preview deben existir antes del bridge para probar el flujo completo de selección → preview → `Tab`/`Esc` sin depender de un servicio cambiante.
- El transporte se desacopla en una fase propia porque la autenticación, JSON-RPC, schema y ciclo de vida del proceso son verificables con fakes y no deben contaminar las APIs de editor.
- La generación se habilita recién cuando la aceptación ya tiene contratos de freshness, undo y cancelación; de este modo una respuesta de Codex nunca obtiene derecho implícito a editar.
- Hardening queda al final, pero sus casos de prueba críticos deben añadirse durante cada fase, no sólo al cierre.

### Research Flags

Phases likely needing deeper research during planning:

- **Phase 2:** confirmar APIs públicas exactas para block inlays y la forma menos intrusiva de manejar `Tab`/`Esc` en el baseline IntelliJ elegido.
- **Phase 3:** contrastar el schema JSON-RPC generado por la versión real de `codex` instalada y definir el handshake/capabilities mínimo.
- **Phase 4:** validar los campos exactos de sandbox, aprobación, threads efímeros, eventos streaming e interrupción contra ese schema, no contra nombres memorizados.
- **Phase 5:** investigar/ejecutar Plugin Verifier para cada build soportado y fijar el alcance de compatibilidad resultante.

Phases with standard patterns (skip research-phase):

- **Phase 1:** Gradle, plugin metadata, `DumbAwareAction`, popup/keymap y pruebas de acción están bien documentados.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM | Gradle/IntelliJ/JVM están documentados oficialmente; el protocolo App Server evoluciona y exige validación local. |
| Features | MEDIUM | El contrato UX está decidido y respaldado por APIs de acciones/inlays; inline completion nativo no es todavía una dependencia segura. |
| Architecture | MEDIUM | Los patrones de editor, lifecycle y JSONL son sólidos; métodos y campos de App Server dependen de la versión instalada. |
| Pitfalls | MEDIUM-HIGH | Riesgos principales contrastados con documentación oficial de JetBrains y OpenAI; faltan pruebas sobre el baseline concreto. |

**Overall confidence:** MEDIUM

### Gaps to Address

- **Baseline real de IntelliJ:** elegir e instalar una rama concreta, compilar el plugin y dejar que el verifier determine `sinceBuild` en lugar de inventarlo.
- **Contrato exacto de App Server:** generar JSON Schema/TypeScript desde el `codex` del usuario y escribir el cliente contra los métodos/campos disponibles.
- **Renderizado y key handling:** probar que el block inlay multilineal es legible y que las acciones condicionales no desplazan completion, templates ni indentación nativas.
- **Política de sandbox soportada:** verificar el modo mínimo de permisos del schema local y rechazar cualquier evento de tool/approval en vez de intentar interpretarlo.
- **Límites de cuenta y latencia:** medir una solicitud manual real y definir límites iniciales de contexto/salida que mantengan el flujo práctico.

## Sources

### Primary (HIGH confidence)

- [JetBrains IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-plugins.html) — setup y packaging.
- [JetBrains Action System](https://plugins.jetbrains.com/docs/intellij/action-system.html) — acciones, menú contextual y keymap.
- [JetBrains Threading Model](https://plugins.jetbrains.com/docs/intellij/threading-model.html) — EDT, read/write actions y cancelación.
- [JetBrains Inlay Hints](https://plugins.jetbrains.com/docs/intellij/inlay-hints.html) — presentation de inlays de bloque.
- [OpenAI Codex App Server README](https://github.com/openai/codex/blob/main/codex-rs/app-server/README.md) — stdio, JSON-RPC, lifecycle, schema, auth y rate limits.

### Secondary (MEDIUM confidence)

- [JetBrains extension-point catalog](https://plugins.jetbrains.com/docs/intellij/intellij-platform-extension-point-list.html) — disponibilidad y carácter experimental/internal de partes de inline completion.
- [OpenAI: Unlocking the Codex harness](https://openai.com/index/unlocking-the-codex-harness/) — modelo de App Server.
- [GitHub Copilot inline suggestions](https://docs.github.com/en/copilot/responsible-use/inline-suggestions) — convenciones de preview, aceptación y descarte.

---
*Research completed: 2026-08-31*  
*Ready for roadmap: yes*
