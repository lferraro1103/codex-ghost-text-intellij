package com.leandro.codexghosttext.claude

import com.leandro.codexghosttext.generation.ProviderDiagnostic
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shell-free boundary for the locally installed Claude executable.
 *
 * The working directory below is only neutral process placement. It is deliberately not a
 * containment or authorization boundary: the command itself exposes no tools or project path.
 */
interface ClaudeProcessRunner {
    fun probe(): ClaudeCapabilityProfile

    fun run(profile: ClaudeCapabilityProfile, request: ClaudeProcessRequest): ClaudeProcessResult

    fun cancel()

    fun dispose()

    companion object {
        const val minimumVersion = "2.1.259"
        val requiredCapabilityFlags = setOf(
            "--safe-mode",
            "--output-format",
            "--json-schema",
            "--tools",
            "--disallowedTools",
            "--permission-mode",
            "--permission-prompts",
            "--max-turns",
        )
    }
}

data class ClaudeCapabilityProfile(
    val executable: Path?,
    val diagnostic: ProviderDiagnostic,
    val supportedFlags: Set<String> = emptySet(),
) {
    val isReady: Boolean
        get() = executable != null && diagnostic == ProviderDiagnostic.READY &&
            supportedFlags.containsAll(ClaudeProcessRunner.requiredCapabilityFlags)
}

/** Prompt and opaque Claude-only session id. Project identity never crosses this boundary. */
data class ClaudeProcessRequest(
    val prompt: String,
    val resumeSessionId: String?,
)

data class ClaudeProcessResult(
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = -1,
    val diagnostic: ProviderDiagnostic? = null,
    val stdoutTruncated: Boolean = false,
    val stderrTruncated: Boolean = false,
)

/** Fully observable direct-execution command, intentionally without a shell string. */
data class ClaudeCommand(
    val executable: Path,
    val arguments: List<String>,
    val environmentRemovals: Set<String>,
    val workingDirectory: Path,
    val timeoutMillis: Long,
    val stdoutLimitBytes: Int,
    val stderrLimitBytes: Int,
)

data class ClaudeCommandResult(
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = 0,
    val timedOut: Boolean = false,
    val cancelled: Boolean = false,
    val stdoutTruncated: Boolean = false,
    val stderrTruncated: Boolean = false,
)

internal fun interface ClaudeExecutableLocator {
    fun find(): Path?
}

internal interface ClaudeCommandExecutor {
    fun execute(command: ClaudeCommand): ClaudeCommandResult

    fun cancel()
}

/** Locates only a regular, executable local CLI file; it never invokes a shell or login command. */
internal class PathClaudeExecutableLocator(
    private val path: String = System.getenv("PATH").orEmpty(),
) : ClaudeExecutableLocator {
    override fun find(): Path? {
        val names = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            // A batch shim would introduce cmd.exe as an unreviewed shell layer.
            listOf("claude.exe")
        } else {
            listOf("claude")
        }
        return path.split(java.io.File.pathSeparatorChar)
            .asSequence()
            .filter(String::isNotBlank)
            .mapNotNull { directory -> runCatching { Path.of(directory) }.getOrNull() }
            .flatMap { directory -> names.asSequence().map(directory::resolve) }
            .firstOrNull { candidate -> Files.isRegularFile(candidate) && Files.isExecutable(candidate) }
    }
}

/**
 * Production implementation. Output is retained only up to the configured bounds; over-limit
 * stream data is drained rather than stored so a child cannot deadlock the plugin process.
 */
internal class JdkClaudeCommandExecutor : ClaudeCommandExecutor {
    private val cancellationRequested = AtomicBoolean(false)

    @Volatile
    private var activeProcess: Process? = null

    override fun execute(command: ClaudeCommand): ClaudeCommandResult {
        cancellationRequested.set(false)
        val process = try {
            ProcessBuilder(listOf(command.executable.toString()) + command.arguments).apply {
                directory(command.workingDirectory.toFile())
                environment().apply { command.environmentRemovals.forEach(::remove) }
            }.start()
        } catch (error: Exception) {
            return ClaudeCommandResult(stderr = error.javaClass.simpleName, exitCode = -1)
        }
        activeProcess = process
        val readers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "Codex Ghost Text Claude stream").apply { isDaemon = true }
        }
        try {
            val stdout = readers.submit<CapturedStream> { process.inputStream.use { it.capture(command.stdoutLimitBytes) } }
            val stderr = readers.submit<CapturedStream> { process.errorStream.use { it.capture(command.stderrLimitBytes) } }
            val completed = process.waitFor(command.timeoutMillis, TimeUnit.MILLISECONDS)
            if (!completed) terminate(process)
            val output = stdout.get(STREAM_JOIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            val errors = stderr.get(STREAM_JOIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            return ClaudeCommandResult(
                stdout = output.text,
                stderr = errors.text,
                exitCode = if (completed) process.exitValue() else -1,
                timedOut = !completed,
                cancelled = cancellationRequested.get(),
                stdoutTruncated = output.truncated,
                stderrTruncated = errors.truncated,
            )
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            terminate(process)
            return ClaudeCommandResult(cancelled = true, stderr = error.javaClass.simpleName, exitCode = -1)
        } catch (error: Exception) {
            terminate(process)
            return ClaudeCommandResult(
                cancelled = cancellationRequested.get(),
                stderr = error.javaClass.simpleName,
                exitCode = -1,
            )
        } finally {
            readers.shutdownNow()
            if (activeProcess === process) activeProcess = null
            if (process.isAlive) terminate(process)
        }
    }

    override fun cancel() {
        cancellationRequested.set(true)
        activeProcess?.let(::terminate)
    }

    private fun terminate(process: Process) {
        process.destroy()
        runCatching { process.waitFor(PROCESS_GRACE_MILLIS, TimeUnit.MILLISECONDS) }
        if (process.isAlive) process.destroyForcibly()
    }

    private fun java.io.InputStream.capture(limit: Int): CapturedStream {
        val retained = ByteArrayOutputStream(limit.coerceAtMost(STREAM_BUFFER_BYTES))
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        var truncated = false
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            val remaining = limit - retained.size()
            if (remaining > 0) retained.write(buffer, 0, minOf(remaining, read))
            if (read > remaining) truncated = true
        }
        return CapturedStream(retained.toString(StandardCharsets.UTF_8), truncated)
    }

    private data class CapturedStream(val text: String, val truncated: Boolean)

    private companion object {
        const val STREAM_BUFFER_BYTES = 8 * 1024
        const val STREAM_JOIN_TIMEOUT_MILLIS = 1_000L
        const val PROCESS_GRACE_MILLIS = 250L
    }
}

internal class DefaultClaudeProcessRunner(
    private val executableLocator: ClaudeExecutableLocator = PathClaudeExecutableLocator(),
    private val commandExecutor: ClaudeCommandExecutor = JdkClaudeCommandExecutor(),
    private val neutralWorkingDirectory: Path = defaultNeutralWorkingDirectory(),
) : ClaudeProcessRunner {
    private val running = AtomicBoolean(false)

    override fun probe(): ClaudeCapabilityProfile {
        val executable = executableLocator.find() ?: return ClaudeCapabilityProfile(null, ProviderDiagnostic.MISSING_EXECUTABLE)
        val version = execute(executable, listOf("--version"))
        version.diagnostic?.let { return ClaudeCapabilityProfile(executable, it) }
        if (version.exitCode != 0) return ClaudeCapabilityProfile(executable, ProviderDiagnostic.VERSION_COMMAND_FAILED)
        when (parseVersion(version.stdout)) {
            VersionCheck.OLD -> return ClaudeCapabilityProfile(executable, ProviderDiagnostic.VERSION_UNSUPPORTED)
            VersionCheck.UNPARSEABLE -> return ClaudeCapabilityProfile(executable, ProviderDiagnostic.VERSION_UNPARSEABLE)
            VersionCheck.SUPPORTED -> Unit
        }

        val help = execute(executable, listOf("--help"))
        help.diagnostic?.let { return ClaudeCapabilityProfile(executable, it) }
        if (help.exitCode != 0) return ClaudeCapabilityProfile(executable, ProviderDiagnostic.HELP_COMMAND_FAILED)
        val supported = ClaudeProcessRunner.requiredCapabilityFlags.filterTo(linkedSetOf()) { flag -> help.stdout.contains(flag) }
        if (!supported.containsAll(ClaudeProcessRunner.requiredCapabilityFlags)) {
            return ClaudeCapabilityProfile(executable, ProviderDiagnostic.UNSAFE_CAPABILITIES, supported)
        }

        val auth = execute(executable, listOf("auth", "status"))
        when (auth.stdout.authenticatedState()) {
            false -> return ClaudeCapabilityProfile(executable, ProviderDiagnostic.LOGIN_REQUIRED, supported)
            null -> return ClaudeCapabilityProfile(
                executable,
                auth.diagnostic ?: if (auth.exitCode == 0) ProviderDiagnostic.AUTH_STATUS_MALFORMED else ProviderDiagnostic.AUTH_STATUS_FAILED,
                supported,
            )
            true -> Unit
        }
        auth.diagnostic?.let { return ClaudeCapabilityProfile(executable, it, supported) }
        if (auth.exitCode != 0) return ClaudeCapabilityProfile(executable, ProviderDiagnostic.AUTH_STATUS_FAILED, supported)
        return ClaudeCapabilityProfile(executable, ProviderDiagnostic.READY, supported)
    }

    override fun run(profile: ClaudeCapabilityProfile, request: ClaudeProcessRequest): ClaudeProcessResult {
        if (!profile.isReady) return ClaudeProcessResult(diagnostic = profile.diagnostic)
        if (!running.compareAndSet(false, true)) return ClaudeProcessResult(diagnostic = ProviderDiagnostic.PROCESS_ALREADY_RUNNING)
        try {
            val arguments = buildList {
                add("-p")
                add(request.prompt)
                add("--safe-mode")
                add("--output-format")
                add("json")
                add("--json-schema")
                add(CODE_ONLY_SCHEMA)
                // An empty allow-list is the primary D-01 capability boundary.
                add("--tools")
                add("")
                // Explicit denials protect against a CLI capability-default regression.
                add("--disallowedTools")
                add(DISALLOWED_TOOLS.joinToString(","))
                add("--permission-mode")
                add("dontAsk")
                add("--permission-prompts")
                add("none")
                add("--max-turns")
                add("1")
                request.resumeSessionId?.takeIf(String::isNotBlank)?.let { sessionId ->
                    add("--resume")
                    add(sessionId)
                }
            }
            val raw = commandExecutor.execute(
                ClaudeCommand(
                    executable = requireNotNull(profile.executable),
                    arguments = arguments,
                    environmentRemovals = credentialEnvironmentNames,
                    workingDirectory = neutralWorkingDirectory,
                    timeoutMillis = GENERATION_TIMEOUT_MILLIS,
                    stdoutLimitBytes = MAX_STDOUT_BYTES,
                    stderrLimitBytes = MAX_STDERR_BYTES,
                ),
            )
            return ClaudeProcessResult(
                stdout = raw.stdout,
                stderr = raw.stderr,
                exitCode = raw.exitCode,
                diagnostic = raw.boundaryDiagnostic(includeExitFailure = true),
                stdoutTruncated = raw.stdoutTruncated,
                stderrTruncated = raw.stderrTruncated,
            )
        } finally {
            running.set(false)
        }
    }

    override fun cancel() = commandExecutor.cancel()

    override fun dispose() = cancel()

    private fun execute(executable: Path, arguments: List<String>): ClaudeProcessResult {
        val raw = commandExecutor.execute(
            ClaudeCommand(
                executable = executable,
                arguments = arguments,
                environmentRemovals = credentialEnvironmentNames,
                workingDirectory = neutralWorkingDirectory,
                timeoutMillis = PROBE_TIMEOUT_MILLIS,
                stdoutLimitBytes = MAX_PROBE_OUTPUT_BYTES,
                stderrLimitBytes = MAX_PROBE_OUTPUT_BYTES,
            ),
        )
        return ClaudeProcessResult(
            stdout = raw.stdout,
            stderr = raw.stderr,
            exitCode = raw.exitCode,
            diagnostic = raw.boundaryDiagnostic(),
            stdoutTruncated = raw.stdoutTruncated,
            stderrTruncated = raw.stderrTruncated,
        )
    }

    private fun ClaudeCommandResult.boundaryDiagnostic(includeExitFailure: Boolean = false): ProviderDiagnostic? = when {
        cancelled -> ProviderDiagnostic.CANCELLED
        timedOut -> ProviderDiagnostic.PROCESS_TIMEOUT
        stdoutTruncated || stderrTruncated -> ProviderDiagnostic.PROCESS_OUTPUT_TOO_LARGE
        includeExitFailure && exitCode != 0 -> ProviderDiagnostic.PROCESS_FAILED
        else -> null
    }

    private enum class VersionCheck { SUPPORTED, OLD, UNPARSEABLE }

    private fun parseVersion(value: String): VersionCheck {
        val match = VERSION_PATTERN.find(value) ?: return VersionCheck.UNPARSEABLE
        val installed = match.groupValues.drop(1).map(String::toIntOrNull)
        if (installed.any { it == null }) return VersionCheck.UNPARSEABLE
        val minimum = listOf(2, 1, 259)
        for (index in minimum.indices) {
            val comparison = installed[index]!!.compareTo(minimum[index])
            if (comparison > 0) return VersionCheck.SUPPORTED
            if (comparison < 0) return VersionCheck.OLD
        }
        return VersionCheck.SUPPORTED
    }

    private fun String.authenticatedState(): Boolean? {
        val value = AUTH_BOOLEAN.find(this)?.groupValues?.get(1)?.toBooleanStrictOrNull()
        if (value != null) return value
        return AUTH_STATUS.find(this)?.groupValues?.get(1)?.lowercase()?.let { status ->
            when (status) {
                "authenticated", "logged_in", "loggedin" -> true
                "unauthenticated", "logged_out", "loggedout" -> false
                else -> null
            }
        }
    }

    companion object {
        val credentialEnvironmentNames: Set<String> = setOf(
            "ANTHROPIC_API_KEY",
            "ANTHROPIC_AUTH_TOKEN",
            "CLAUDE_CODE_OAUTH_TOKEN",
        )
        private val DISALLOWED_TOOLS = listOf(
            "Read",
            "Glob",
            "Grep",
            "Bash",
            "Edit",
            "WebFetch",
            "WebSearch",
            "Agent",
            "NotebookEdit",
            "MCP",
            "Browser",
            "ProjectInspection",
            "mcp__*",
        )

        private val VERSION_PATTERN = Regex("""(?<![0-9])(\d+)\.(\d+)\.(\d+)(?![0-9])""")
        private val AUTH_BOOLEAN = Regex("""\"(?:loggedIn|authenticated)\"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
        private val AUTH_STATUS = Regex("""\"status\"\s*:\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
        private const val PROBE_TIMEOUT_MILLIS = 8_000L
        private const val GENERATION_TIMEOUT_MILLIS = 75_000L
        private const val MAX_PROBE_OUTPUT_BYTES = 64 * 1024
        private const val MAX_STDOUT_BYTES = 128 * 1024
        private const val MAX_STDERR_BYTES = 32 * 1024
        private const val CODE_ONLY_SCHEMA = "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"code\":{\"type\":\"string\"}},\"required\":[\"code\"]}"

        private fun defaultNeutralWorkingDirectory(): Path {
            val directory = Path.of(System.getProperty("java.io.tmpdir"), "codex-ghost-text", "claude")
            return runCatching { Files.createDirectories(directory) }.getOrElse { Path.of(System.getProperty("java.io.tmpdir")) }
        }
    }
}
