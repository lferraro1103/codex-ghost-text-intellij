# Domain Pitfalls

**Domain:** Plugin personal de IntelliJ Platform que convierte un comentario seleccionado en una sugerencia de código de Codex App Server
**Researched:** 2026-08-31
**Confidence:** MEDIUM — hallazgos contrastados entre documentación oficial de JetBrains y OpenAI; la forma exacta de presentar *inline completion* deberá validarse contra la versión objetivo del IDE.

## Critical Pitfalls

### Pitfall 1: Bloquear el editor mientras se obtiene contexto o llega Codex

**What goes wrong:** La acción de menú, el acceso a PSI, el arranque de `codex app-server` o la lectura de JSON-RPC se ejecutan en el Event Dispatch Thread (EDT). El IDE queda congelado justo al seleccionar el comentario o al escribir.

**Why it happens:** IntelliJ tiene un único EDT para UI y escritura. A la vez, App Server es una interacción de turnos asíncrona y puede generar streaming, esperas de red y cola saturada.

**Consequences:** El plugin se siente peor que no tenerlo; puede provocar advertencias de operaciones lentas y bloqueos intermitentes difíciles de reproducir.

**Prevention:**

- Tomar una captura mínima e inmutable del editor en una lectura breve (selección, idioma, texto adyacente, `modificationStamp`, offset de inserción); nunca sostener PSI/documento durante la llamada al modelo.
- Ejecutar proceso, JSONL, streaming y postprocesado en coroutines de fondo; volver al EDT sólo para instalar/quitar el inlay y para insertar el texto aceptado.
- Hacer cancelable cada trabajo y cancelar el turno remoto con `turn/interrupt` cuando el editor, selección, proyecto o propuesta dejan de ser actuales.
- En `-32001 Server overloaded`, no hacer un bucle de reintentos: aplicar *backoff* exponencial con jitter o mostrar que Codex está ocupado.

**Detection:** Perf snapshot con actividad del plugin en EDT, aviso `SlowOperations`, solicitudes que se completan después de modificar el documento, o varias propuestas superpuestas.

### Pitfall 2: Insertar una sugerencia desactualizada o en el offset equivocado

**What goes wrong:** Entre la selección y la respuesta el usuario escribe, deshace, cambia de archivo o reformatea. El `Tab` inserta código en una posición distinta, o una respuesta vieja reemplaza una propuesta nueva.

**Why it happens:** Los offsets, PSI y `VirtualFile` no son estables a través de límites asíncronos. Los listeners pueden recibir cambios antes de que su tarea en segundo plano empiece.

**Consequences:** Corrupción de la intención del usuario, bloque de código duplicado o `Tab` que afecta al completado nativo.

**Prevention:** Asociar cada propuesta a un `requestId` y a una instantánea: `Editor`, documento, archivo, rango seleccionado, offset de inserción y `modificationStamp`. Al recibir texto y antes de aceptar, comparar todo; si no coincide, descartar silenciosamente. Invalidar la propuesta ante `DocumentEvent`, cambio de caret/selección, cierre de editor o disposición del proyecto. Instalar un marcador de rango con semántica explícita sólo como ayuda visual, no como autorización para insertar después de cambios arbitrarios.

**Detection:** Tests que editan, cambian de pestaña y ejecutan undo mientras llega el streaming; métricas de respuestas descartadas por versión/offset.

### Pitfall 3: Confundir ghost text con una modificación real del archivo

**What goes wrong:** Se implementa la vista previa escribiendo el código y luego intentando revertirlo, o se usa una API de completion de popup que no representa un bloque debajo del comentario.

**Why it happens:** El SDK diferencia el documento, los inlays y el sistema de completado. La documentación oficial de inlays contempla inlays de bloque y presentación propia, pero no garantiza que una API de *inline completion* concreta sea pública y estable para todas las versiones objetivo.

**Consequences:** El archivo se marca como modificado antes de `Tab`, se ensucia el undo/VCS, desaparece el comentario, o el resultado visual cambia al actualizar IntelliJ.

**Prevention:** Para el MVP, renderizar una propuesta temporal como block inlay/editor renderer anclado al final de la línea del comentario; no tocar `Document` hasta aceptar. El renderer debe dibujar como código atenuado, con saltos de línea, tabulación y colores legibles. Al aceptar, ejecutar una única escritura breve y agrupada en undo; al cancelar, remover sólo el inlay. Confirmar el comportamiento en la versión fijada del IDE antes de invertir en una integración con la API de inline completion.

**Detection:** Abrir el diff y la pila de undo antes de `Tab`; probar comentarios al final de archivo, líneas largas, texto plegado, editores divididos y distintos temas/escala DPI.

### Pitfall 4: Secuestrar `Tab` o `Esc` del IDE

**What goes wrong:** Un handler global consume `Tab`/`Esc` incluso cuando la propuesta no está activa, o compite con Live Templates, la lista de completion, identación, snippets y diálogos.

**Why it happens:** `Tab` y `Esc` ya tienen semántica contextual rica en IntelliJ; un ghost text de terceros no tiene prioridad automática frente a todas ellas.

**Consequences:** Pérdida de la experiencia nativa y percepción de que el plugin “rompe” el editor.

**Prevention:** Registrar acciones con atajos configurables y manejarlas sólo si hay una propuesta válida en el editor activo, no hay lookup/modal con prioridad y el caret sigue en el ancla esperada. En todo otro caso, delegar al comportamiento original. `Esc` debe descartar únicamente la propuesta propia, sin cerrar componentes ajenos. Ofrecer menú contextual y atajo como disparadores; no depender de un click dentro del inlay para aceptar.

**Detection:** Matriz manual/automatizada con completion normal, templates, multi-caret, selección activa, reformat, search bar y Keymap personalizado.

### Pitfall 5: Dar al modelo acceso innecesario al proyecto o dejar que ejecute herramientas

**What goes wrong:** El comentario seleccionado —que es texto no confiable— induce al agente a explorar archivos, leer secretos, ejecutar un comando o solicitar permisos. También es fácil pasar `cwd` del repositorio y heredar un sandbox permisivo por defecto.

**Why it happens:** App Server está diseñado para agentes completos: `turn/start` puede definir `cwd`, sandbox y política de aprobación, y el protocolo incluye ejecución de comandos y cambios de archivo. La propia documentación advierte que algunos RPC de proceso son experimentales y sin sandbox.

**Consequences:** Fuga de contexto local, una aprobación inesperada, cambios de archivo fuera del flujo de `Tab` o una superficie de *prompt injection* desde un comentario del repositorio.

**Prevention:** El modo “sólo sugerencia” debe ser de mínima autoridad: proceso por `stdio`, hilo efímero por propuesta, sin herramientas dinámicas, sin llamadas a `command/*`, `process/*` ni `thread/shellCommand`, y con aprobación denegada (`approvalPolicy: "never"`). No pasar el directorio del proyecto como `cwd` ni contenido de archivos completo en v1; usar un directorio vacío/controlado y sólo la selección más un contexto local limitado. Instruir al modelo a tratar el comentario y código como datos, a devolver exclusivamente un bloque de código y a no ejecutar ni pedir acciones. Si llega un pedido de aprobación, tool call o formulario inesperado, denegarlo/cancelar el turno y registrar un error seguro.

**Detection:** Fixtures con comentarios adversariales (“ejecutá…”, “ignorá…”), auditoría de cada RPC saliente, y prueba que falla si se llama cualquier método de ejecución o cambio de archivo.

### Pitfall 6: Exponer autenticación o abrir un servicio local accesible

**What goes wrong:** El plugin copia archivos de sesión/tokens de Codex, implementa OAuth propio o levanta App Server en WebSocket para simplificar el cliente.

**Why it happens:** Parece más fácil “reutilizar” la sesión existente o conectar a un puerto fijo, pero los tokens son responsabilidad de Codex y el transporte WebSocket está documentado como experimental/no soportado; además se han reportado configuraciones sin autenticación incorporada.

**Consequences:** Credenciales expuestas, otro proceso local conectándose al servidor, compatibilidad rota tras una actualización o incumplimiento del objetivo de no manejar secretos.

**Prevention:** Iniciar `codex app-server --stdio` como proceso hijo sin shell y comunicarse sólo por sus pipes privados; separar `stdout` (JSONL del protocolo) de `stderr` (logs). Leer `account/read` para el estado y dejar que Codex posea el flujo `account/login/start` si hiciera falta; nunca leer, copiar ni persistir refresh tokens. No abrir puertos ni sockets para el MVP. Resolver el ejecutable desde una configuración explícita o instalación validada, y terminar/limpiar el hijo al disponer el proyecto.

**Detection:** Revisión de que no se leen rutas de auth ni se imprimen tokens/logs; test que no hay listener TCP y que un `stderr` ruidoso no rompe el parser JSONL.

### Pitfall 7: Acoplarse a una versión no compatible de App Server o IntelliJ

**What goes wrong:** El plugin asume nombres/campos de una versión de Codex, usa campos experimentales sin optar por ellos, o declara un rango amplio de builds IntelliJ aunque cambien APIs de threading/editor.

**Why it happens:** App Server evoluciona por protocolo JSON-RPC; la documentación indica que el TypeScript/JSON Schema generado corresponde exactamente a la versión de Codex instalada. JetBrains también ha cambiado reglas de threading y APIs entre releases.

**Consequences:** “Not initialized”, campos ignorados/rechazados, ghost text roto tras actualizar Codex/IDE, o errores sólo en determinados productos JetBrains.

**Prevention:** Fijar y documentar un baseline corto para v1 (un IntelliJ IDEA y un Codex CLI probados). Al iniciar, hacer `initialize` una vez por conexión, identificar `clientInfo`, comprobar versión/capacidades y fallar con diagnóstico claro si son insuficientes. Preferir métodos/campos no experimentales; no anunciar extensiones que el cliente no implementa. Generar o validar el schema desde el binario local durante desarrollo y mantener pruebas de compatibilidad en las versiones declaradas. No prometer soporte para JetBrains Client/Remote Development inicialmente: detectar modo remoto y deshabilitar la acción con explicación.

**Detection:** Smoke tests con la versión mínima y la más nueva compatible de IntelliJ/Codex; errores de protocolo clasificados, no mensajes genéricos de timeout.

## Moderate Pitfalls

### Pitfall 1: Latencia, cuota y demasiados turnos persistentes

**What goes wrong:** Una acción manual aún tarda lo suficiente para romper el flujo; turnos persistentes acumulan contexto, coste/cuota y respuestas contaminadas de solicitudes anteriores.

**Prevention:** Crear una propuesta por selección, usar hilo efímero y cancelar al invalidarse. Mostrar estado discreto “Generando…” y no instalar inlay hasta disponer de una respuesta que cumpla el formato. Consultar/escuchar `account/rateLimits/read` y `account/rateLimits/updated`; ante límite, informar cuándo se reinicia si el dato existe y no reintentar automáticamente.

### Pitfall 2: Formato de salida no insertable

**What goes wrong:** Codex responde con Markdown, explicación, código que repite el comentario, triple backticks o indentación equivocada.

**Prevention:** Solicitar una sola continuación de código sin fences y con un contrato de salida estricto. Validar/rechazar caracteres de control y limpiar sólo envoltorios inequívocos; nunca “adivinar” texto explicativo como código. Indentar relativo a la línea de inserción y usar el final de línea/documento del editor.

### Pitfall 3: Fugas de recursos y propuestas huérfanas

**What goes wrong:** Un proceso `codex` queda vivo, una coroutine sigue leyendo después de cerrar proyecto, o un inlay queda en un editor disposed.

**Prevention:** Propietario por `Project`/`Disposable`; registrar listeners y jobs bajo ese ciclo de vida; apagar stdin, interrumpir turnos y esperar/terminar proceso con timeout. Mantener como máximo una solicitud activa por editor y eliminar el inlay en todos los caminos de éxito, error, cancelación y dispose.

### Pitfall 4: Contexto insuficiente o excesivo

**What goes wrong:** Sólo el comentario produce código irrelevante; enviar todo el archivo/repo compromete privacidad, velocidad y relevancia.

**Prevention:** Hacer explícito el contrato v1: comentario seleccionado + lenguaje + bloque/función contenedor + ventana acotada de líneas, con límite de tamaño configurable. Añadir contexto mayor sólo como opción explícita posterior y mostrar exactamente qué se enviará.

## Minor Pitfalls

### Pitfall 1: Confundir selección válida con cualquier texto

**What goes wrong:** La acción se habilita sobre código, selección vacía o un comentario incompleto, por lo que el resultado es impredecible.

**Prevention:** En `update()` validar rápidamente que la selección no está vacía y corresponde a un comentario del `PsiFile`/lenguaje soportado; si se está indexando o no se puede verificar sin coste, deshabilitar con mensaje breve. Conservar el comentario siempre y calcular la inserción en la línea siguiente.

### Pitfall 2: Accesibilidad y legibilidad del texto transparente

**What goes wrong:** El gris tiene bajo contraste, se corta o no se entiende con zoom, tema claro/oscuro, fuente proporcional o lector de pantalla.

**Prevention:** Usar atributos de esquema de color del IDE, no un color hard-coded; respetar fuente/escala, soportar wrapping/scroll y ofrecer acciones visibles en menú/Keymap además de la pista visual.

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| Bootstrap del plugin | Rango de compatibilidad ficticio | Elegir una versión baseline de IntelliJ y probarla antes de declarar soporte amplio. |
| Acción sobre selección | Trabajo costoso en `AnAction.update()` | Validación ligera; contexto/PSI completo sólo después de invocar, en background cancelable. |
| Cliente App Server | Parser JSONL o handshake defectuoso | `stdio`, inicialización única, correlación por id, stdout separado de stderr y prueba con eventos en streaming. |
| Generación | Respuesta vieja, sobrecarga o cuota agotada | `requestId`, `modificationStamp`, `turn/interrupt`, backoff y UI de límite. |
| Ghost text | Inlay que modifica archivo o `Tab` global | Inlay temporal de bloque y handlers condicionales que delegan al IDE. |
| Seguridad | Prompt injection / acceso al workspace | CWD vacío, sandbox mínimo, `approvalPolicy: "never"`, no tools/exec y contexto acotado. |
| Aceptación | Insertar a pesar de cambios | Revalidar instantánea en EDT y hacer una sola write action undoable. |

## Sources

- [OpenAI Codex App Server README](https://github.com/openai/codex/blob/main/codex-rs/app-server/README.md) — MEDIUM (documentación primaria, revisada el 2026-08-31): transportes, handshake, schema por versión, backpressure, cancelación, permisos, auth y rate limits.
- [OpenAI: Unlocking the Codex harness](https://openai.com/index/unlocking-the-codex-harness/) — MEDIUM (fuente primaria): contexto y motivación del protocolo App Server.
- [JetBrains: Threading Model](https://plugins.jetbrains.com/docs/intellij/threading-model.html) — MEDIUM (documentación primaria): EDT, read/write actions, cancelación y validez después de trabajo asíncrono.
- [JetBrains: Background Processes](https://plugins.jetbrains.com/docs/intellij/background-processes.html) — MEDIUM (documentación primaria): cancelación de cálculos de completion obsoletos.
- [JetBrains: Inlay Hints](https://plugins.jetbrains.com/docs/intellij/inlay-hints.html) — MEDIUM (documentación primaria): inlays de bloque e inline, límites de presentación y compatibilidad.
- [JetBrains: Notable API Changes](https://plugins.jetbrains.com/docs/intellij/api-notable.html) — MEDIUM (documentación primaria): cambios de APIs/plataforma que obligan a testear el rango declarado.
