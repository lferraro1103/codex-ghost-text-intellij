package com.leandro.codexghosttext.codex

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
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

    fun generate(comment: String, documentText: String, range: TextRange): GenerationResult {
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

                La primera respuesta visible debe comenzar directamente con código. No expliques qué vas a hacer,
                no describas pasos, no respondas al comentario en lenguaje natural y no uses Markdown ni cercos de código.
                Si no podés producir código insertable, respondé vacío. El proyecto asociado está disponible sólo
                en lectura mediante su carpeta de trabajo: inspeccioná archivos únicamente si necesitás ese contexto
                para generar el código y nunca los modifiques.

                Comentario seleccionado:
                $comment

                Contexto cercano del archivo:
                ${documentText.window(range)}
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
            return collect(reader)
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
                environment().remove("CODEX_API_KEY")
                environment().remove("OPENAI_API_KEY")
                environment().remove("CODEX_ACCESS_TOKEN")
            }.start()
        }.getOrNull() ?: return@synchronized null
        val reader = process.inputStream.bufferedReader()
        val writer = process.outputStream.bufferedWriter()
        activeProcess = process
        activeReader = reader
        activeWriter = writer
        activeProjectRoot = projectRoot
        process.discardErrorOutput()

        val initialized = request(writer, reader, "initialize", "{\"clientInfo\":{\"name\":\"codex-ghost-text\",\"version\":\"0.1.8\"}}")
        if (initialized == null || initialized.isJsonRpcError()) {
            closeSessionLocked()
            return@synchronized null
        }
        synchronized(writerLock) { writer.notification("initialized", "{}") }

        val sessionParams = "{\"cwd\":${projectRoot.json()},\"approvalPolicy\":\"never\",\"sandbox\":\"read-only\",\"config\":{\"web_search\":\"disabled\",\"features\":{\"plugins\":false}}}"
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

    private fun collect(reader: BufferedReader): GenerationResult {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(75)
        val text = StringBuilder()
        while (System.nanoTime() < deadline) {
            if (!reader.ready()) {
                Thread.sleep(20)
                continue
            }
            val line = reader.readLine() ?: return GenerationResult.Failure("Codex cerró la conexión.")
            if (line.length > 64 * 1024) return GenerationResult.Failure("Respuesta de Codex demasiado grande.")
            when {
                line.contains("\"method\":\"item/agentMessage/delta\"") -> line.jsonField("delta")?.let(text::append)
                line.contains("\"type\":\"fileChange\"") || line.contains("\"method\":\"applyPatchApproval\"") -> return GenerationResult.Failure("Codex intentó modificar archivos; la propuesta fue cancelada.")
                line.contains("\"type\":\"mcpToolCall\"") || line.contains("\"type\":\"dynamicToolCall\"") || line.contains("\"type\":\"collabAgentToolCall\"") || line.contains("\"type\":\"webSearch\"") -> return GenerationResult.Failure("Codex intentó usar una herramienta no permitida; la propuesta fue cancelada.")
                line.contains("\"method\":\"turn/completed\"") -> return if (line.contains("\"status\":\"completed\"")) proposal(text.toString()) else GenerationResult.Failure("Codex no pudo completar la generación.")
            }
        }
        return GenerationResult.Failure("Codex tardó demasiado en responder.")
    }

    private fun proposal(raw: String): GenerationResult {
        val code = raw.codeOnlyProposal()
        return if (code.isBlank() || code.length > 16_000 || code.lines().size > 80) GenerationResult.Failure("Codex no devolvió una propuesta utilizable.") else GenerationResult.Success(code)
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

    private fun nextRequestId(): Int = requestIds.incrementAndGet()

    override fun dispose() {
        synchronized(sessionLock) { closeSessionLocked() }
    }
}

sealed interface GenerationResult {
    data class Success(val code: String) : GenerationResult
    data class Failure(val message: String) : GenerationResult
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

internal fun String.jsonField(name: String): String? {
    val marker = "\"$name\""
    val start = indexOf(marker)
    if (start < 0) return null
    val quote = indexOf('"', indexOf(':', start) + 1)
    if (quote < 0) return null
    val output = StringBuilder()
    var escape = false
    for (index in quote + 1 until length) {
        val character = this[index]
        if (!escape && character == '"') return output.toString()
        if (!escape && character == '\\') {
            escape = true
            continue
        }
        output.append(if (escape) when (character) { 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; else -> character } else character)
        escape = false
    }
    return null
}

internal fun String.threadId(): String? {
    val thread = indexOf("\"thread\"")
    return if (thread < 0) null else substring(thread).jsonField("id")
}

internal fun String.turnId(): String? {
    val turn = indexOf("\"turn\"")
    return if (turn < 0) null else substring(turn).jsonField("id")
}

internal fun String.isJsonRpcError(): Boolean = contains("\"error\"") && !contains("\"result\"")

internal fun String.codeOnlyProposal(): String {
    val text = trim().removePrefix("```kotlin").removePrefix("```").removeSuffix("```").trim()
    val starts = listOf(
        Regex("""(?m)^\s*(?=(?://|/\*|@\w|(?:public|private|protected|internal|fun|class|interface|object|data\s+class|sealed\s+class|enum\s+class|static|void|boolean|byte|short|int|long|float|double|char|String)\b))"""),
        Regex("""\b(?:public|private|protected)\s+(?:(?:static|final|abstract|synchronized)\s+)*(?:void|boolean|byte|short|int|long|float|double|char|String|[A-Z]\w*(?:<[^\n>]+>)?)\s+\w+\s*\("""),
        Regex("""(?m)^\s*(?=\w+(?:\.\w+)*\s*(?:=|\+\+|--))"""),
    )
    val start = starts.mapNotNull { it.find(text)?.range?.first }.minOrNull() ?: return ""
    return text.substring(start).trim()
}

private fun String.window(range: TextRange): String {
    val start = (range.startOffset - 2000).coerceAtLeast(0)
    val end = (range.endOffset + 4000).coerceAtMost(length)
    return substring(start, end)
}

private fun String.canonicalProjectRoot(): String = runCatching { File(this).canonicalPath }.getOrDefault(this)
