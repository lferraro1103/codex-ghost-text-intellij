package com.leandro.codexghosttext.codex

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class CodexAvailabilityService : Disposable {
    private val isChecking = AtomicBoolean(false)

    @Volatile
    private var activeProcess: Process? = null

    fun check(): CodexDiagnostic {
        val executable = CodexExecutableLocator.find() ?: return CodexDiagnostic.MISSING_EXECUTABLE
        if (!isChecking.compareAndSet(false, true)) return CodexDiagnostic.CONNECTION_FAILED
        var process: Process? = null

        return try {
            process = ProcessBuilder(executable.toString(), "app-server", "--listen", "stdio://")
                .apply {
                    environment().remove("CODEX_API_KEY")
                    environment().remove("OPENAI_API_KEY")
                    environment().remove("CODEX_ACCESS_TOKEN")
                }
                .start()
            activeProcess = process
            process.discardErrorOutput()

            process.outputStream.bufferedWriter().use { writer ->
                process.inputStream.bufferedReader().use { reader ->
                    writer.jsonRpc(1, "initialize", "{\"clientInfo\":{\"name\":\"codex-ghost-text\",\"version\":\"0.1.7\"}}")
                    if (CodexProtocol.responseForId(reader, 1) == null) return CodexDiagnostic.CONNECTION_FAILED
                    writer.jsonRpcNotification("initialized", "{}")

                    writer.jsonRpc(2, "account/read", "{\"refreshToken\":false}")
                    val accountResponse = CodexProtocol.responseForId(reader, 2)
                        ?: return CodexDiagnostic.CONNECTION_FAILED
                    val accountState = CodexProtocol.classifyAccount(accountResponse)
                    if (accountState != CodexDiagnostic.CHATGPT_READY) return accountState

                    writer.jsonRpc(3, "account/rateLimits/read", "{}")
                    val rateLimitResponse = CodexProtocol.responseForId(reader, 3)
                        ?: return CodexDiagnostic.CONNECTION_FAILED
                    if (CodexProtocol.isQuotaExhausted(rateLimitResponse)) {
                        CodexDiagnostic.QUOTA_EXHAUSTED
                    } else {
                        CodexDiagnostic.CHATGPT_READY
                    }
                }
            }
        } catch (_: Exception) {
            CodexDiagnostic.CONNECTION_FAILED
        } finally {
            process?.destroy()
            try {
                process?.waitFor(250, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (process?.isAlive == true) process.destroyForcibly()
            if (activeProcess === process) activeProcess = null
            isChecking.set(false)
        }
    }

    override fun dispose() {
        activeProcess?.destroyForcibly()
    }
}

enum class CodexDiagnostic {
    CHATGPT_READY,
    MISSING_EXECUTABLE,
    LOGIN_REQUIRED,
    UNSUPPORTED_AUTH,
    QUOTA_EXHAUSTED,
    CONNECTION_FAILED,
}

/**
 * Parses only the fixed, non-secret fields used by the availability check.  The App Server
 * protocol evolves independently from the plugin, so this deliberately does not deserialize or
 * retain complete server payloads.
 */
internal object CodexProtocol {
    private const val MAX_LINE_LENGTH = 64 * 1024
    private const val RESPONSE_TIMEOUT_MILLIS = 8_000L
    private val methodPattern = Regex("\\\"method\\\"\\s*:")
    private val accountTypePattern = Regex("\\\"type\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
    private val usedPercentPattern = Regex("\\\"usedPercent\\\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)")

    fun responseForId(reader: BufferedReader, id: Int): String? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RESPONSE_TIMEOUT_MILLIS)
        val expectedId = Regex("\\\"id\\\"\\s*:\\s*(?:$id|\\\"$id\\\")(?=\\s*[,}])")

        while (System.nanoTime() < deadline) {
            if (!reader.ready()) {
                Thread.sleep(20)
                continue
            }

            val line = reader.readLine() ?: return null
            if (line.length > MAX_LINE_LENGTH || !line.trimStart().startsWith("{")) return null

            // The App Server is bidirectional. It can send notifications, or even a server
            // request with its own id, while a client request is in flight. Those records are
            // not the correlated JSON-RPC response we are waiting for.
            if (expectedId.containsMatchIn(line) &&
                !methodPattern.containsMatchIn(line) &&
                (line.contains("\"result\"") || line.contains("\"error\""))
            ) return line

            // Every caller has a bounded timeout. With approvalPolicy=never and a read-only
            // sandbox, no ignored callback can authorize a write.
            continue
        }
        return null
    }

    fun classifyAccount(response: String): CodexDiagnostic {
        if (!response.contains("\"result\"")) return CodexDiagnostic.CONNECTION_FAILED
        if (Regex("\\\"requiresOpenaiAuth\\\"\\s*:\\s*true").containsMatchIn(response)) {
            return CodexDiagnostic.LOGIN_REQUIRED
        }
        val accountType = accountTypePattern.find(response)?.groupValues?.get(1)?.lowercase()
            ?: return CodexDiagnostic.LOGIN_REQUIRED
        return if (accountType == "chatgpt") {
            CodexDiagnostic.CHATGPT_READY
        } else {
            CodexDiagnostic.UNSUPPORTED_AUTH
        }
    }

    fun isQuotaExhausted(response: String): Boolean =
        usedPercentPattern.findAll(response).any { it.groupValues[1].toDouble() >= 100.0 }
}

internal object CodexExecutableLocator {
    fun find(
        path: String = System.getenv("PATH").orEmpty(),
        localAppData: String? = System.getenv("LOCALAPPDATA"),
    ): Path? {
        val executableNames = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            listOf("codex.exe")
        } else {
            listOf("codex")
        }

        path.split(File.pathSeparatorChar)
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { directory -> runCatching { Path.of(directory) }.getOrNull() }
            .flatMap { directory -> executableNames.asSequence().map(directory::resolve) }
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
            ?.let { return it }

        val nativeRoot = localAppData?.let { runCatching { Path.of(it, "OpenAI", "Codex", "bin") }.getOrNull() }
            ?: return null
        if (!Files.isDirectory(nativeRoot)) return null

        return runCatching {
            Files.walk(nativeRoot, 2).use { paths ->
                paths.filter { candidate ->
                    Files.isRegularFile(candidate) &&
                        executableNames.any { name -> candidate.fileName.toString().equals(name, ignoreCase = true) } &&
                        Files.isExecutable(candidate)
                }.findFirst().orElse(null)
            }
        }.getOrNull()
    }
}

private fun BufferedWriter.jsonRpc(id: Int, method: String, params: String) {
    write("{\"jsonrpc\":\"2.0\",\"id\":$id,\"method\":\"$method\",\"params\":$params}")
    newLine()
    flush()
}

private fun BufferedWriter.jsonRpcNotification(method: String, params: String) {
    write("{\"jsonrpc\":\"2.0\",\"method\":\"$method\",\"params\":$params}")
    newLine()
    flush()
}

internal fun Process.discardErrorOutput() {
    Thread({
        errorStream.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (input.read(buffer) != -1) {
                // Discard process diagnostics: they can contain local account details.
            }
        }
    }, "Codex Ghost Text stderr discard").apply {
        isDaemon = true
        start()
    }
}
