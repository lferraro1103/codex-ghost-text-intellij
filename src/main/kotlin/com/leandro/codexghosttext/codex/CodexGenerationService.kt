package com.leandro.codexghosttext.codex

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.leandro.codexghosttext.env.LocalCliEnvironment
import com.leandro.codexghosttext.generation.CodeProposal
import com.leandro.codexghosttext.generation.GenerationRequest
import com.leandro.codexghosttext.generation.GenerationResult
import com.leandro.codexghosttext.generation.ProjectContextService
import com.leandro.codexghosttext.generation.SourceLanguage
import com.leandro.codexghosttext.json.JsonValue
import com.leandro.codexghosttext.json.obj
import com.leandro.codexghosttext.json.string
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Service(Service.Level.PROJECT)
class CodexGenerationService(private val project: Project) : Disposable {
    private val generating = AtomicBoolean(false)
    private val sessionLock = Any()
    private val writerLock = Any()
    private val requestIds = AtomicInteger(10)
    private val conversationState = project.getService(CodexProjectConversationState::class.java)
    private val reusePolicy = CodexConversationReusePolicy()

    @Volatile private var activeProcess: Process? = null
    @Volatile private var activeReader: BufferedReader? = null
    @Volatile private var activeWriter: BufferedWriter? = null
    @Volatile private var activeThreadId: String? = null
    @Volatile private var activeTurnId: String? = null
    @Volatile private var activeProjectRoot: String? = null

    /** Interrupts only the turn currently running; it intentionally keeps the project's chat alive. */
    fun cancel() {
        val writer = activeWriter ?: return
        val threadId = activeThreadId ?: return
        val turnId = activeTurnId ?: return
        runCatching {
            synchronized(writerLock) {
                writer.rpc(nextRequestId(), "turn/interrupt", "{\"threadId\":${threadId.json()},\"turnId\":${turnId.json()}}")
            }
        }
    }

    fun isGenerating(): Boolean = generating.get()

    /** Forgets only this project's chat association. The next request will create a fresh thread. */
    fun resetConversation() {
        cancel()
        synchronized(sessionLock) {
            conversationState.forget()
            closeSessionLocked()
        }
    }

    fun generate(request: GenerationRequest): GenerationResult {
        val executable = CodexExecutableLocator.find() ?: return GenerationResult.Failure("No encontré Codex local.")
        val projectRoot = project.basePath?.canonicalProjectRoot()
            ?: return GenerationResult.Failure("El proyecto no tiene una carpeta disponible.")
        if (!generating.compareAndSet(false, true)) return GenerationResult.Failure("La generación anterior se está cancelando.")

        try {
            val threadId = ensureConversation(executable.toString(), projectRoot)
                ?: return GenerationResult.Failure("No pude iniciar la conversación local de Codex.")
            val reader = activeReader ?: return GenerationResult.Failure("Codex cerró la conexión.")
            val writer = activeWriter ?: return GenerationResult.Failure("Codex cerró la conexión.")
            val prompt = """
                Generá únicamente el código nuevo que va inmediatamente debajo del comentario seleccionado.
                ${SourceLanguage.instruction(request)}

                Comentario seleccionado:
                ${request.comment}

                Contexto cercano del archivo:
                ${request.documentText.window(request.range)}
            """.trimIndent()
            val turnRequestId = nextRequestId()
            synchronized(writerLock) {
                writer.rpc(
                    turnRequestId,
                    "turn/start",
                    "{\"threadId\":${threadId.json()},\"approvalPolicy\":\"never\",\"cwd\":${projectRoot.json()},\"input\":[{\"type\":\"text\",\"text\":${prompt.json()}}]}",
                )
            }
            val turnResponse = CodexProtocol.responseForId(reader, turnRequestId)
                ?: return GenerationResult.Failure("Codex no respondió al iniciar la generación.")
            if (turnResponse.isJsonRpcError()) return GenerationResult.Failure("Codex rechazó iniciar la generación.")
            activeTurnId = turnResponse.turnId()
                ?: return GenerationResult.Failure("Codex no devolvió una generación válida.")
            return collect(reader, request)
        } catch (_: Exception) {
            return GenerationResult.Failure("Falló la conexión local con Codex.")
        } finally {
            activeTurnId = null
            generating.set(false)
        }
    }

    /** A project service owns one app-server process and one reusable Codex thread. */
    private fun ensureConversation(executable: String, projectRoot: String): String? = synchronized(sessionLock) {
        reusePolicy.liveThreadFor(
            projectRoot,
            activeProcess?.isAlive == true && activeProjectRoot == projectRoot && activeReader != null && activeWriter != null,
        )?.let { return@synchronized it }

        closeSessionLocked()
        val process = runCatching {
            ProcessBuilder(executable, "app-server", "--listen", "stdio://").apply {
                // The app-server and its thread both start in this IntelliJ project's root.
                // This gives Codex project context without embedding the whole project in a prompt.
                directory(File(projectRoot))
                // The login-shell environment, so a desktop-launched IDE still gives Codex the
                // interpreter and tools it expects. Credentials are removed after it is applied.
                LocalCliEnvironment.applyTo(this, codexCredentialEnvironmentNames)
            }.start()
        }.getOrNull() ?: return@synchronized null
        val reader = process.inputStream.bufferedReader()
        val writer = process.outputStream.bufferedWriter()
        activeProcess = process
        activeReader = reader
        activeWriter = writer
        activeProjectRoot = projectRoot
        process.discardErrorOutput()

        val initialized = request(writer, reader, "initialize", "{\"clientInfo\":{\"name\":\"codex-ghost-text\",\"version\":\"0.2.2\"}}")
        if (initialized == null || initialized.isJsonRpcError()) {
            closeSessionLocked()
            return@synchronized null
        }
        synchronized(writerLock) { writer.notification("initialized", "{}") }

        // developerInstructions belong to the thread, so the per-request prompt no longer repeats
        // the rules on every turn. MCP servers configured by the user still start: the App Server
        // has no per-thread switch for them, so an actual tool call is what cancels a proposal.
        val sessionParams = "{\"cwd\":${projectRoot.json()},\"approvalPolicy\":\"never\",\"sandbox\":\"read-only\"," +
            "\"developerInstructions\":${developerInstructions().json()}," +
            "\"config\":{\"web_search\":\"disabled\",\"features\":{\"plugins\":false}}}"
        val threadId = reusePolicy.establish(
            projectRoot,
            conversationState.threadFor(projectRoot),
            resume = { savedThreadId ->
                val resumeParams = sessionParams.dropLast(1) + ",\"threadId\":${savedThreadId.json()}}"
                request(writer, reader, "thread/resume", resumeParams)
                    ?.takeUnless(String::isJsonRpcError)
                    ?.threadId()
            },
            start = {
                request(writer, reader, "thread/start", sessionParams)
                    ?.takeUnless(String::isJsonRpcError)
                    ?.threadId()
            },
        )
        if (threadId == null) {
            closeSessionLocked()
            return@synchronized null
        }
        activeThreadId = threadId
        conversationState.remember(projectRoot, threadId)
        threadId
    }

    private fun request(writer: BufferedWriter, reader: BufferedReader, method: String, params: String): String? {
        val id = nextRequestId()
        synchronized(writerLock) { writer.rpc(id, method, params) }
        return CodexProtocol.responseForId(reader, id)
    }

    private fun collect(reader: BufferedReader, request: GenerationRequest): GenerationResult {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(GENERATION_TIMEOUT_SECONDS)
        val text = StringBuilder()
        while (System.nanoTime() < deadline) {
            if (!reader.ready()) {
                Thread.sleep(POLL_MILLIS)
                continue
            }
            val line = reader.readLine() ?: return GenerationResult.Failure("Codex cerró la conexión.")
            if (text.length > CodeProposal.MAX_CHARS * 2) return GenerationResult.Failure("Respuesta de Codex demasiado grande.")
            when (val event = codexStreamEvent(line)) {
                is CodexStreamEvent.Delta -> text.append(event.text)
                is CodexStreamEvent.FileChangeAttempt -> return GenerationResult.Failure(FILE_CHANGE_MESSAGE)
                is CodexStreamEvent.ForbiddenTool -> return GenerationResult.Failure(FORBIDDEN_TOOL_MESSAGE)
                is CodexStreamEvent.TurnFinished -> return if (event.completed) {
                    proposal(text.toString(), request)
                } else {
                    GenerationResult.Failure("Codex no pudo completar la generación.")
                }
                null -> Unit
            }
        }
        return GenerationResult.Failure("Codex tardó demasiado en responder.")
    }

    private fun proposal(raw: String, request: GenerationRequest): GenerationResult {
        CodeProposal.fenceTag(raw)?.let { tag ->
            if (SourceLanguage.fenceConflicts(tag, request.language, request.fileName)) {
                return GenerationResult.Failure("Codex respondió en $tag y el archivo no está en ese lenguaje.")
            }
        }
        // Codex sometimes opens with a sentence before the code, so the answer is trimmed to where
        // code starts; what remains still has to pass the shared insertable-code check.
        val code = CodeProposal.trimToCodeStart(CodeProposal.withoutFence(raw))
        return if (CodeProposal.isInsertable(code)) {
            GenerationResult.Success(code)
        } else {
            GenerationResult.Failure("Codex no devolvió una propuesta utilizable.")
        }
    }

    private fun closeSessionLocked() {
        reusePolicy.clearActive()
        activeThreadId = null
        activeTurnId = null
        runCatching { activeWriter?.close() }
        runCatching { activeReader?.close() }
        activeWriter = null
        activeReader = null
        activeProjectRoot = null
        activeProcess?.let { process ->
            process.destroy()
            runCatching { process.waitFor(250, TimeUnit.MILLISECONDS) }
            if (process.isAlive) process.destroyForcibly()
        }
        activeProcess = null
    }

    /** The fixed rules plus this project's brief, sent once when the thread is created. */
    private fun developerInstructions(): String =
        DEVELOPER_INSTRUCTIONS + "\n\n" + project.getService(ProjectContextService::class.java).brief()

    private fun nextRequestId(): Int = requestIds.incrementAndGet()

    private companion object {
        const val GENERATION_TIMEOUT_SECONDS = 75L
        const val POLL_MILLIS = 20L
        const val FILE_CHANGE_MESSAGE = "Codex intentó modificar archivos; la propuesta fue cancelada."
        const val FORBIDDEN_TOOL_MESSAGE = "Codex intentó usar una herramienta no permitida; la propuesta fue cancelada."
        /** The read-only rules, stated once for the whole thread instead of on every turn. */
        val DEVELOPER_INSTRUCTIONS = """
            Respondé siempre con código insertable y nada más: la primera línea visible es código.
            No expliques qué vas a hacer, no describas pasos, no respondas en lenguaje natural y no
            uses Markdown ni cercos de código. Si no podés producir código insertable, respondé vacío.
            El proyecto está disponible sólo en lectura desde tu carpeta de trabajo: inspeccioná
            archivos únicamente si necesitás ese contexto y nunca los modifiques.
        """.trimIndent()
    }

    override fun dispose() {
        synchronized(sessionLock) { closeSessionLocked() }
    }
}

/**
 * One App Server record, read as JSON.
 *
 * Records used to be matched with `line.contains("...")`, which the generated code itself could
 * trigger: a proposal containing the text `"type":"fileChange"` cancelled itself.
 */
internal sealed interface CodexStreamEvent {
    data class Delta(val text: String) : CodexStreamEvent
    data object FileChangeAttempt : CodexStreamEvent
    data object ForbiddenTool : CodexStreamEvent
    data class TurnFinished(val completed: Boolean) : CodexStreamEvent
}

private val FILE_CHANGE_ITEMS = setOf("fileChange", "patchApply")
private val FORBIDDEN_TOOL_ITEMS = setOf("mcpToolCall", "dynamicToolCall", "collabAgentToolCall", "webSearch")

internal fun codexStreamEvent(line: String): CodexStreamEvent? {
    val record = JsonValue.parseObject(line) ?: return null
    val method = record.string("method")
    if (method == "applyPatchApproval") return CodexStreamEvent.FileChangeAttempt
    val params = record.obj("params")
    val itemType = params?.obj("item")?.string("type")
    return when {
        itemType in FILE_CHANGE_ITEMS -> CodexStreamEvent.FileChangeAttempt
        itemType in FORBIDDEN_TOOL_ITEMS -> CodexStreamEvent.ForbiddenTool
        method == "item/agentMessage/delta" -> params?.string("delta")?.let(CodexStreamEvent::Delta)
        method == "turn/completed" -> CodexStreamEvent.TurnFinished(params?.obj("turn")?.string("status") == "completed")
        method == "turn/failed" -> CodexStreamEvent.TurnFinished(completed = false)
        else -> null
    }
}

private fun BufferedWriter.rpc(id: Int, method: String, params: String) {
    write("{\"jsonrpc\":\"2.0\",\"id\":$id,\"method\":\"$method\",\"params\":$params}")
    newLine()
    flush()
}

private fun BufferedWriter.notification(method: String, params: String) {
    write("{\"jsonrpc\":\"2.0\",\"method\":\"$method\",\"params\":$params}")
    newLine()
    flush()
}

private fun String.json(): String = buildString {
    append('"')
    for (character in this@json) append(
        when (character) {
            '\\' -> "\\\\"
            '"' -> "\\\""
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> character
        },
    )
    append('"')
}

/** The thread id of a `thread/start` or `thread/resume` result. */
internal fun String.threadId(): String? = JsonValue.parseObject(this)?.obj("result")?.obj("thread")?.string("id")

/** The turn id of a `turn/start` result. */
internal fun String.turnId(): String? = JsonValue.parseObject(this)?.obj("result")?.obj("turn")?.string("id")

internal fun String.isJsonRpcError(): Boolean = JsonValue.parseObject(this)
    ?.let { it.values.containsKey("error") && !it.values.containsKey("result") } ?: true

private fun String.window(range: TextRange): String {
    val start = (range.startOffset - 2000).coerceAtLeast(0)
    val end = (range.endOffset + 4000).coerceAtMost(length)
    return substring(start, end)
}

private fun String.canonicalProjectRoot(): String = runCatching { File(this).canonicalPath }.getOrDefault(this)
