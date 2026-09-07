# Codex Ghost Text for IntelliJ

Plugin personal para IntelliJ IDEA que transforma un comentario seleccionado en una propuesta de código generada por Codex local. La propuesta se muestra como *ghost text* verde debajo del comentario y el archivo no cambia hasta aceptarla.

No requiere una API key: usa la sesión de Codex CLI ya autenticada con la cuenta normal de ChatGPT.

## Funciones

- Seleccionar un comentario y ejecutar **Generate Codex Ghost Text** desde el menú contextual del editor.
- Solicitud manual: no genera código mientras se escribe.
- Indicador de generación en la barra de estado.
- Vista previa multilínea, verde y translúcida, con la misma sangría del comentario.
- Aceptación con `|` y cancelación con `Esc`.
- El archivo se modifica únicamente al aceptar la propuesta.
- Las respuestas se filtran para aceptar sólo código insertable, sin explicaciones ni bloques Markdown.

## Requisitos

- IntelliJ IDEA basado en la plataforma 2026.1 o posterior.
- [Codex CLI](https://developers.openai.com/codex/cli/) instalado y disponible como `codex` en el `PATH` (en Windows también se detecta la instalación de Codex en `LOCALAPPDATA`).
- Una cuenta de ChatGPT con acceso a Codex y sesión iniciada localmente.

## Instalación

1. Descargá el archivo ZIP de la [última release](../../releases/latest).
2. En IntelliJ: **Settings / Preferences → Plugins → ⚙ → Install Plugin from Disk…**.
3. Elegí el ZIP descargado y reiniciá el IDE.

## Uso

1. Escribí y seleccioná con el mouse un comentario, por ejemplo `// crear un método que quite la raíz`.
2. Hacé clic derecho en el editor y elegí **Generate Codex Ghost Text**.
3. Esperá el indicador de generación en la barra de estado.
4. Revisá el bloque verde debajo del comentario.
5. Presioná `|` para insertar el código o `Esc` para descartarlo.

El atajo `|` se procesa en la tubería de tipeo del editor antes de que el carácter pueda escribirse. Así la aceptación no deja un `|` en el archivo ni invalida la propuesta.

## Inicio de sesión y límites de Codex

El plugin no implementa OAuth, no pide una API key y no lee ni guarda credenciales. Al generar una propuesta inicia un proceso local:

```text
codex app-server --listen stdio://
```

Ese proceso reutiliza la autenticación que ya configuraste en Codex CLI, normalmente mediante `codex login`. Como defensa adicional, el plugin elimina de su subproceso las variables `CODEX_API_KEY`, `OPENAI_API_KEY` y `CODEX_ACCESS_TOKEN`; por lo tanto no cambia silenciosamente al flujo de facturación por API.

La acción **Tools → Check Codex Connection** verifica que Codex local esté disponible, que la cuenta sea de ChatGPT y que aún tenga cuota. Cuando se alcanza el límite de la cuenta, el plugin deja de generar hasta que Codex vuelva a disponer de cuota. La cuenta, la facturación y los límites los gestiona Codex/ChatGPT, no este plugin.

## Privacidad y seguridad

Para cada generación se envía a Codex local:

- el comentario seleccionado;
- contexto cercano del archivo: hasta 2.000 caracteres anteriores y 4.000 posteriores;
- la carpeta del proyecto como directorio de trabajo.

La sesión se crea con sandbox de sólo lectura, aprobación `never`, búsqueda web desactivada y plugins/herramientas desactivados. Si Codex intenta modificar archivos o usar una herramienta, la propuesta se cancela. El plugin no aplica cambios automáticos ni conserva la respuesta después de cerrar la vista previa.

## Arquitectura

| Componente | Responsabilidad |
| --- | --- |
| `GenerateCodexGhostTextAction` | Valida la selección y dispara la generación en segundo plano. |
| `CodexGenerationService` | Ejecuta el App Server local mediante JSON-RPC sobre `stdio`, con sesión read-only. |
| `CodexAvailabilityService` | Comprueba ejecutable, autenticación ChatGPT y cuota disponible. |
| `GhostPreviewService` | Mantiene la propuesta fuera del documento, la invalida si éste cambia e inserta sólo al aceptar. |
| `GhostBlockRenderer` | Dibuja el bloque verde con la sangría correcta. |
| `CodexGhostTypedHandler` | Captura `|` antes de que IntelliJ escriba el carácter y acepta la propuesta. |
| `GhostKeyHandlerInstaller` | Cancela una propuesta activa con `Esc`. |

## Desarrollo

El proyecto usa Kotlin, Java 21 y Gradle con IntelliJ Platform Gradle Plugin.

```powershell
.\gradlew.bat buildPlugin --no-daemon
```

El ZIP queda en `build/distributions/codex-ghost-text-<versión>.zip`.

Para ejecutar las pruebas:

```powershell
.\gradlew.bat test --no-daemon
```

> El sandbox de pruebas de IntelliJ requiere memoria virtual suficiente. Si Windows informa que el archivo de paginación es demasiado pequeño, aumentalo o cerrá procesos pesados antes de ejecutarlas.

## Estado

La versión actual es **0.1.7**. Es un plugin personal y local; no está publicado en JetBrains Marketplace.
