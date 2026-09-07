package com.leandro.codexghosttext.codex

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class CodexGenerationService(private val project: Project) : Disposable {
    private val generating = AtomicBoolean(false)
    @Volatile private var active: Process? = null

    fun cancel() { active?.destroyForcibly() }
    fun isGenerating(): Boolean = generating.get()

    fun generate(comment: String, documentText: String, range: TextRange): GenerationResult {
        val executable = CodexExecutableLocator.find() ?: return GenerationResult.Failure("No encontré Codex local.")
        val basePath = project.basePath ?: return GenerationResult.Failure("El proyecto no tiene una carpeta disponible.")
        if (!generating.compareAndSet(false, true)) return GenerationResult.Failure("La generación anterior se está cancelando.")
        var process: Process? = null
        try {
            process = ProcessBuilder(executable.toString(), "app-server", "--listen", "stdio://").apply {
                environment().remove("CODEX_API_KEY"); environment().remove("OPENAI_API_KEY"); environment().remove("CODEX_ACCESS_TOKEN")
            }.start()
            active = process
            process.discardErrorOutput()
            process.outputStream.bufferedWriter().use { writer -> process.inputStream.bufferedReader().use { reader ->
                writer.rpc(1, "initialize", "{\"clientInfo\":{\"name\":\"codex-ghost-text\",\"version\":\"0.1.3\"}}")
                val initialization = CodexProtocol.responseForId(reader, 1)
                    ?: return GenerationResult.Failure("No pude iniciar Codex local.")
                if (initialization.isJsonRpcError()) return GenerationResult.Failure("Codex rechazó la inicialización local.")
                writer.notification("initialized", "{}")
                writer.rpc(2, "thread/start", "{\"cwd\":${basePath.json()},\"approvalPolicy\":\"never\",\"sandbox\":\"read-only\",\"config\":{\"web_search\":\"disabled\",\"features\":{\"plugins\":false}}}")
                val threadResponse = CodexProtocol.responseForId(reader, 2)
                    ?: return GenerationResult.Failure("No pude crear una sesión de Codex.")
                if (threadResponse.isJsonRpcError()) return GenerationResult.Failure("Codex rechazó crear la sesión.")
                val thread = threadResponse.threadId() ?: return GenerationResult.Failure("Codex no devolvió una sesión válida.")
                val prompt = """Generá SOLAMENTE el código que debe ir debajo de este comentario. No uses Markdown ni bloques de código. Podés inspeccionar el proyecto en modo lectura para entender clases relacionadas, pero nunca modifiques archivos.\n\nComentario seleccionado:\n$comment\n\nContexto cercano del archivo:\n${documentText.window(range)}"""
                writer.rpc(3, "turn/start", "{\"threadId\":${thread.json()},\"approvalPolicy\":\"never\",\"input\":[{\"type\":\"text\",\"text\":${prompt.json()}}]}")
                val turnResponse = CodexProtocol.responseForId(reader, 3)
                    ?: return GenerationResult.Failure("Codex no respondió al iniciar la generación.")
                if (turnResponse.isJsonRpcError()) return GenerationResult.Failure("Codex rechazó iniciar la generación.")
                return collect(reader)
            }}
        } catch (_: Exception) { return GenerationResult.Failure("Falló la conexión local con Codex.")
        } finally { process?.destroy(); process?.waitFor(250, TimeUnit.MILLISECONDS); if (process?.isAlive == true) process.destroyForcibly(); if (active === process) active = null; generating.set(false) }
    }

    private fun collect(reader: BufferedReader): GenerationResult {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(75)
        val text = StringBuilder()
        while (System.nanoTime() < deadline) {
            if (!reader.ready()) { Thread.sleep(20); continue }
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
        val code = raw.trim().removePrefix("```kotlin").removePrefix("```").removeSuffix("```").trim()
        return if (code.isBlank() || code.length > 16_000 || code.lines().size > 80) GenerationResult.Failure("Codex no devolvió una propuesta utilizable.") else GenerationResult.Success(code)
    }
    override fun dispose() { active?.destroyForcibly() }
}

sealed interface GenerationResult { data class Success(val code: String) : GenerationResult; data class Failure(val message: String) : GenerationResult }

private fun BufferedWriter.rpc(id: Int, method: String, params: String) { write("{\"jsonrpc\":\"2.0\",\"id\":$id,\"method\":\"$method\",\"params\":$params}"); newLine(); flush() }
private fun BufferedWriter.notification(method: String, params: String) { write("{\"jsonrpc\":\"2.0\",\"method\":\"$method\",\"params\":$params}"); newLine(); flush() }
private fun String.json(): String = buildString { append('"'); for (c in this@json) append(when(c){'\\'->"\\\\";'"'->"\\\"";'\n'->"\\n";'\r'->"\\r";'\t'->"\\t";else->c}); append('"') }
internal fun String.jsonField(name: String): String? { val marker = "\"$name\""; val start = indexOf(marker); if (start < 0) return null; val quote = indexOf('"', indexOf(':', start) + 1); if (quote < 0) return null; val out=StringBuilder(); var escape=false; for (i in quote+1 until length){val c=this[i]; if(!escape&&c=='"') return out.toString(); if(!escape&&c=='\\'){escape=true;continue}; out.append(if(escape) when(c){'n'->'\n';'r'->'\r';'t'->'\t';else->c}else c);escape=false}; return null }
internal fun String.threadId(): String? {
    val thread = indexOf("\"thread\"")
    return if (thread < 0) null else substring(thread).jsonField("id")
}
internal fun String.isJsonRpcError(): Boolean = contains("\"error\"") && !contains("\"result\"")
private fun String.window(range: TextRange): String { val start=(range.startOffset-2000).coerceAtLeast(0); val end=(range.endOffset+4000).coerceAtMost(length); return substring(start,end) }
