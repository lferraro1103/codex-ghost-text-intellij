# Feature Landscape

**Domain:** Plugin personal de IntelliJ para generar código desde comentarios mediante Codex local
**Researched:** 2026-08-31
**Confidence:** MEDIUM — el flujo está corroborado por documentación de JetBrains, Codex y Copilot; la compatibilidad exacta de la API de inline completion debe fijarse contra una versión concreta del IDE.

## Table Stakes

Funciones que debe tener la primera entrega para satisfacer el flujo definido en `PROJECT.md`.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Acción manual sobre selección | El usuario necesita seleccionar un comentario y disparar la generación sin salir del editor. | Low | Registrar una `AnAction`, visible sólo cuando hay un editor y selección no vacía. JetBrains permite añadir una acción al menú contextual del editor y asignar un atajo configurable. |
| Entrada desde menú contextual y shortcut | El requisito pide ambas formas de invocación; no deben comportarse distinto. | Low | Un único action ID, presentado en `EditorPopup`, con atajo por defecto que el usuario puede cambiar en Keymap. |
| Validación de comentario seleccionado | Evita enviar por accidente código amplio, credenciales u otro texto no destinado como instrucción. | Medium | Primera versión: habilitar sólo si la selección completa pertenece a un comentario reconocido por PSI; si no, mostrar una notificación breve y no iniciar una petición. |
| Estado de solicitud no destructivo | Codex puede tardar; el usuario necesita saber que la solicitud está en curso sin bloquear el editor. | Medium | Mostrar un indicador discreto de “Generando…” y permitir cancelar con `Esc`; no editar el `Document` durante esta etapa. |
| Ghost text debajo del comentario | Es el valor visual principal: ver el bloque propuesto conservando el comentario original y sin alterar el archivo. | High | Usar el mecanismo de inline completion sólo si es compatible con la plataforma objetivo. Su catálogo expone `InlineCompletionProvider`, pero varios hooks relacionados están marcados internal/experimental; fijar una versión de IntelliJ probada y encapsular esta integración. |
| Aceptar con `Tab` | La interacción debe insertar el bloque completo debajo del comentario de manera explícita. | Medium | Antes de insertar, confirmar que selección, documento y caret siguen correspondiendo a la solicitud que creó la propuesta. La convención de Copilot para JetBrains también usa `Tab`. |
| Descartar con `Esc` y al seguir editando | Un preview no debe quedar obsoleto ni interceptar el editor. | Medium | `Esc`, cambio de selección, cambio de documento, cierre de editor o una nueva generación eliminan/cancelan el preview. La convención de sugerencias inline también permite ignorar una sugerencia continuando al escribir. |
| Conexión local a Codex App Server | Sin API key: usar la sesión normal de Codex ya instalada en el equipo. | Medium | Lanzar/conectar por `stdio` al App Server y comunicarse por JSON-RPC. Consultar `account/read` para estado; el plugin nunca lee, pega ni almacena tokens. |
| Errores y estados accionables | Sin Codex instalado, sin sesión o sin cuota, el usuario debe saber qué hacer. | Low | Estados: “Codex no encontrado”, “Iniciá sesión en Codex”, “Límite de uso alcanzado”, “Solicitud cancelada” y “Error de generación”; ninguno modifica el archivo. |

## Differentiators

Funciones de alto valor para un uso personal, pero sólo después de cerrar la interacción central.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Prompt estructurado con contexto local mínimo | Mejora la utilidad de la generación sin convertir el plugin en un agente que modifica el repo. | Medium | Enviar comentario, lenguaje, ruta relativa y una ventana limitada alrededor de la inserción; excluir archivos sensibles/configurables. Debe ser una preferencia visible. |
| Reintentar / regenerar desde el mismo comentario | Permite pedir una variante sin copiar ni borrar nada. | Low | Reutilizar la acción mientras existe el preview; reemplaza el preview anterior, nunca código aceptado. |
| Vista previa de prompt y diagnóstico local | Hace transparente qué contexto va a Codex y facilita depurar instalaciones personales. | Medium | Panel de diagnóstico con versión de Codex, estado de cuenta y últimas razones de error; ocultar contenido de código y datos de sesión por defecto. |
| Formateo tras aceptar, opcional | Hace que el bloque aceptado adopte el estilo del archivo. | Medium | Ejecutarlo sólo después de insertar y sólo cuando el usuario lo habilite; si falla, conservar exactamente el texto aceptado. |
| Lenguajes amplios | El valor se extiende a cualquier lenguaje con comentarios reconocibles por el IDE. | Medium | No prometer igual calidad: el plugin puede ser agnóstico de lenguaje, pero la utilidad del resultado depende del contexto y modelo. |

## Anti-Features

Decisiones explícitas para proteger el alcance, la previsibilidad y los límites de la cuenta normal de Codex.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Autocompletado automático en cada pulsación | Aumenta uso y latencia, puede inundar el App Server y contradice la activación manual decidida para el MVP. | Generar sólo desde una selección y acción explícita. |
| Reemplazar/eliminar el comentario | Pierde la intención del usuario y viola el requisito de mantenerlo como referencia. | Insertar el bloque únicamente debajo del comentario tras `Tab`. |
| Aplicar cambios o ejecutar comandos por cuenta propia | Convierte una propuesta revisable en automatización riesgosa y puede provocar solicitudes de aprobación del App Server. | Solicitar una continuación de código de solo lectura y aceptar únicamente la inserción explícita del usuario. |
| Gestionar OAuth, contraseña o API key dentro del plugin | Duplica la superficie de seguridad y no es necesario para el caso personal. | Delegar autenticación a Codex App Server; usar `account/read` y mostrar instrucciones si no hay sesión. |
| Backend remoto propio, telemetría o historial en nube | No aporta al MVP personal y añade privacidad, coste y mantenimiento. | Todo ocurre entre IntelliJ, el proceso Codex local y la cuenta del usuario. |
| Compatibilidad ilimitada con versiones de IntelliJ | La integración de inline completion incluye puntos marcados internal/experimental; prometer “todas las versiones” produciría fragilidad. | Publicar una matriz clara de versiones soportadas y verificar la actualización de plataforma antes de ampliar el rango. |
| Aceptación parcial, múltiples candidatos o chat embebido | Multiplica el estado UI y las colisiones de atajos antes de probar el flujo básico. | Una propuesta completa, un único ciclo: generar → preview → `Tab`/`Esc`. |

## Feature Dependencies

```text
Codex instalado + sesión de ChatGPT/Codex
  → conexión App Server + comprobación de estado
  → solicitud de generación cancelable
  → ghost text no destructivo
  → Tab inserta / Esc descarta

Acción contextual + shortcut
  → selección validada como comentario
  → solicitud de generación cancelable

Ghost text no destructivo
  → invalidación por edición/selección
  → inserción segura y opcionalmente formateada
```

## MVP Recommendation

Priorizar:

1. **Acción del editor y selección segura:** menú contextual y atajo que sólo se habiliten para un comentario seleccionado.
2. **Cliente App Server local y estados de error:** iniciar/comunicar por `stdio`, detectar sesión y presentar fallas sin credenciales ni escritura de archivo.
3. **Un preview de ghost text y ciclo `Tab`/`Esc`:** proponer bajo el comentario, cancelar/invalidar correctamente y hacer una sola inserción atómica al aceptar.

Defer:

- **Generación automática:** incompatible con el objetivo de uso y latencia controlados.
- **Múltiples sugerencias y aceptación parcial:** no es necesaria para validar valor ni estabilidad del flujo.
- **Formateo, regeneración y controles de privacidad avanzados:** muy útiles, pero dependen de una inserción y cancelación totalmente correctas.
- **Compatibilidad con múltiples IDEs JetBrains:** primero confirmar la API y UX en IntelliJ IDEA Community/Ultimate para una versión fijada.

## UX Contract for the MVP

1. El usuario selecciona un comentario completo con el mouse.
2. Ejecuta **“Generate Codex Ghost Text”** desde clic derecho o shortcut.
3. El plugin no cambia el archivo; presenta progreso y luego un único bloque semitransparente bajo el comentario.
4. `Tab` inserta exactamente ese bloque debajo del comentario; `Esc` lo retira. Cualquier edición relevante lo invalida y no deja cambios.
5. Si Codex local no está listo o alcanzó su límite, el plugin explica el estado y permite volver a intentarlo cuando corresponda.

## Sources

- [JetBrains Action System](https://plugins.jetbrains.com/docs/intellij/action-system.html) — acciones de plugin, entradas en menú contextual y atajos configurables. **MEDIUM** (documentación oficial; verificada).
- [JetBrains Creating Actions](https://plugins.jetbrains.com/docs/intellij/creating-actions-tutorial.html) — registro de acciones, shortcut y contexto de selección. **MEDIUM** (documentación oficial; verificada).
- [JetBrains extension-point catalog](https://plugins.jetbrains.com/docs/intellij/intellij-platform-extension-point-list.html) — `InlineCompletionProvider` y clasificación internal/experimental de hooks asociados. **MEDIUM** (documentación oficial; verificada).
- [OpenAI Codex App Server README](https://github.com/openai/codex/blob/main/codex-rs/app-server/README.md) — JSON-RPC local, `stdio`, estado/autenticación gestionada por Codex. **MEDIUM** (repositorio oficial; verificado).
- [GitHub Copilot inline suggestions](https://docs.github.com/en/copilot/responsible-use/inline-suggestions) y [shortcuts para JetBrains](https://docs.github.com/en/enterprise-cloud@latest/copilot/reference/keyboard-shortcuts?tool=jetbrains) — patrón de preview, aceptación y descarte. **MEDIUM** (documentación oficial; verificada).
