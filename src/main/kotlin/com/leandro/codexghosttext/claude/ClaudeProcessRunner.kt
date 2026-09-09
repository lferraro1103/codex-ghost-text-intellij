package com.leandro.codexghosttext.claude

import com.leandro.codexghosttext.env.LocalCliEnvironment
import com.leandro.codexghosttext.env.LocalCliExecutableSearch
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
 * Availability checks run from a neutral directory. Generation runs from the IntelliJ project
 * root with an explicit read-only tool surface.
 */
interface ClaudeProcessRunner {
    fun probe(): ClaudeCapabilityProfile

    fun run(profile: ClaudeCapabilityProfile, request: ClaudeProcessRequest): ClaudeProcessResult

    fun cancel()

    fun dispose()

    companion object {
        /**
         * Only flags that enforce non-interactive, read-only execution are required. A numeric
         * release is deliberately not used as a proxy: packaging varies by channel and Claude's
         * own documentation warns that --help does not list every accepted flag.
         */
        val requiredCapabilityFlags = setOf(
            "--print",
            "--output-format",
            "--tools",
            "--allowed-tools",
            "--disallowed-tools",
            "--permission-mode",
            "--resume",
        )

        /** Optional execution bounds are used when the installed CLI advertises them. */
        val optionalCapabilityFlags = setOf("--max-turns")
    }
}

data class ClaudeCapabilityProfile(
    val executable: Path?,
    val diagnostic: ProviderDiagnostic,
    val supportedFlags: Set<String> = emptySet(),
    val version: String? = null,
) {
    val isReady: Boolean
        get() = executable != null && diagnostic == ProviderDiagnostic.READY &&
            supportedFlags.containsAll(ClaudeProcessRunner.requiredCapabilityFlags)

    val missingFlags: Set<String>
        get() = ClaudeProcessRunner.requiredCapabilityFlags - supportedFlags

    val userMessage: String
        get() = if (diagnostic == ProviderDiagnostic.UNSAFE_CAPABILITIES && missingFlags.isNotEmpty()) {
            "A la CLI de Claude le faltan opciones requeridas: ${missingFlags.joinToString(", ")}."
        } else {
            diagnostic.userMessage
        }
}

/** Prompt, opaque Claude-only session id, and the project directory available read-only. */
data class ClaudeProcessRequest(
    val prompt: String,
    val resumeSessionId: String?,
    val projectRoot: Path,
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
    // The login-shell PATH, not the IDE process PATH: a desktop-launched IDE does not inherit the
    // directories where Homebrew, npm, and version managers install `claude`.
    private val path: String = LocalCliEnvironment.searchPath(),
    private val userHome: String = System.getProperty("user.home").orEmpty(),
    private val osName: String = System.getProperty("os.name").orEmpty(),
) : ClaudeExecutableLocator {
    override fun find(): Path? {
        return candidates().firstOrNull { candidate ->
            LocalCliExecutableSearch.isExecutableFile(candidate.path)
        }?.path
    }

    /** A redacted, filesystem-only trace for support dumps; it never executes a shell command. */
    internal fun diagnosticReport(): String = buildString {
        val candidates = candidates()
        appendLine("pathEntryCount=${path.split(java.io.File.pathSeparatorChar).count(String::isNotBlank)}")
        appendLine("candidateCount=${candidates.size}")
        candidates.take(MAX_DIAGNOSTIC_CANDIDATES).forEach { candidate ->
            val state = when {
                LocalCliExecutableSearch.isExecutableFile(candidate.path) -> "executable"
                Files.isRegularFile(candidate.path) -> "not-executable"
                Files.exists(candidate.path) -> "not-a-regular-file"
                else -> "missing"
            }
            appendLine("candidate[${candidate.source}]=${displayPath(candidate.path)} status=$state")
        }
        if (candidates.size > MAX_DIAGNOSTIC_CANDIDATES) appendLine("candidateList=truncated")
    }.trimEnd()

    private fun candidates(): List<LocalCliExecutableSearch.Candidate> = LocalCliExecutableSearch.candidates(
        names = executableNames(),
        path = path,
        userHome = userHome,
        osName = osName,
        providerDirectories = PROVIDER_DIRECTORIES,
    )

    private fun executableNames(): List<String> = if (LocalCliExecutableSearch.isWindows(osName)) {
        // A batch shim would introduce cmd.exe as an unreviewed shell layer.
        listOf("claude.exe")
    } else {
        listOf("claude")
    }

    private fun displayPath(path: Path): String {
        val normalized = displayPathText(path)
        val home = userHome.takeIf(String::isNotBlank)?.let {
            runCatching { displayPathText(Path.of(it)) }.getOrNull()
        }
        return if (home != null && normalized.startsWith(home, ignoreCase = osName.startsWith("Windows", ignoreCase = true))) {
            "<home>" + normalized.removePrefix(home)
        } else {
            normalized
        }
    }

    private fun displayPathText(path: Path): String = path.toAbsolutePath().normalize().toString()
        .let { value -> if (LocalCliExecutableSearch.isWindows(osName)) value else value.replace('\\', '/') }

    private companion object {
        const val MAX_DIAGNOSTIC_CANDIDATES = 48

        /** Claude Code's own local installs, which are outside every package-manager directory. */
        val PROVIDER_DIRECTORIES = listOf(".claude/local", ".claude/bin")
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
                // A Node-based install of the CLI needs its interpreter, which a desktop-launched
                // IDE does not have on PATH. Credentials are removed after the shell environment
                // is applied, so a key exported by a shell profile is dropped as well.
                LocalCliEnvironment.applyTo(this, command.environmentRemovals)
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
        val installedVersion = parseVersion("${version.stdout}\n${version.stderr}")
            ?: return ClaudeCapabilityProfile(executable, ProviderDiagnostic.VERSION_UNPARSEABLE)

        val help = execute(executable, listOf("--help"))
        help.diagnostic?.let { return ClaudeCapabilityProfile(executable, it) }
        if (help.exitCode != 0) return ClaudeCapabilityProfile(executable, ProviderDiagnostic.HELP_COMMAND_FAILED)
        val helpText = "${help.stdout}\n${help.stderr}"
        val knownCapabilities = ClaudeProcessRunner.requiredCapabilityFlags + ClaudeProcessRunner.optionalCapabilityFlags
        val supported = knownCapabilities.filterTo(linkedSetOf()) { capability ->
            CAPABILITY_ALIASES.getValue(capability).any(helpText::contains)
        }
        if (!supported.containsAll(ClaudeProcessRunner.requiredCapabilityFlags)) {
            return ClaudeCapabilityProfile(executable, ProviderDiagnostic.UNSAFE_CAPABILITIES, supported, installedVersion)
        }

        val auth = execute(executable, listOf("auth", "status"))
        when (auth.stdout.authenticatedState()) {
            false -> return ClaudeCapabilityProfile(executable, ProviderDiagnostic.LOGIN_REQUIRED, supported, installedVersion)
            null -> return ClaudeCapabilityProfile(
                executable,
                auth.diagnostic ?: if (auth.exitCode == 0) ProviderDiagnostic.AUTH_STATUS_MALFORMED else ProviderDiagnostic.AUTH_STATUS_FAILED,
                supported,
                installedVersion,
            )
            true -> Unit
        }
        auth.diagnostic?.let { return ClaudeCapabilityProfile(executable, it, supported, installedVersion) }
        if (auth.exitCode != 0) return ClaudeCapabilityProfile(executable, ProviderDiagnostic.AUTH_STATUS_FAILED, supported, installedVersion)
        return ClaudeCapabilityProfile(executable, ProviderDiagnostic.READY, supported, installedVersion)
    }

    override fun run(profile: ClaudeCapabilityProfile, request: ClaudeProcessRequest): ClaudeProcessResult {
        if (!profile.isReady) return ClaudeProcessResult(diagnostic = profile.diagnostic)
        if (!running.compareAndSet(false, true)) return ClaudeProcessResult(diagnostic = ProviderDiagnostic.PROCESS_ALREADY_RUNNING)
        try {
            val arguments = buildList {
                add("-p")
                add("--output-format")
                add("json")
                // --tools restricts the built-in surface; --allowedTools would only auto-approve.
                add("--tools")
                add(READ_ONLY_TOOLS.joinToString(","))
                // Pre-approve the same read-only tools so dontAsk can run headlessly.
                add("--allowedTools")
                add(READ_ONLY_TOOLS.joinToString(","))
                // --tools does not affect MCP tools, so deny every MCP capability separately.
                // Keep the original camel-case spelling for older Claude Code builds.
                add("--disallowedTools")
                add("mcp__*")
                add("--permission-mode")
                // With dontAsk, anything outside the explicit tool surface is denied headlessly.
                add("dontAsk")
                if ("--max-turns" in profile.supportedFlags) {
                    add("--max-turns")
                    // Reading project files may require several tool/result turns before final code.
                    add("5")
                }
                request.resumeSessionId?.takeIf(String::isNotBlank)?.let { sessionId ->
                    add("--resume")
                    add(sessionId)
                }
                // Keep the positional prompt last, matching Claude's documented resume syntax.
                add(request.prompt)
            }
            val raw = commandExecutor.execute(
                ClaudeCommand(
                    executable = requireNotNull(profile.executable),
                    arguments = arguments,
                    environmentRemovals = credentialEnvironmentNames,
                    workingDirectory = request.projectRoot,
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
        includeExitFailure && exitCode != 0 -> classifyClaudeFailure(stdout, stderr)
        else -> null
    }

    private fun classifyClaudeFailure(stdout: String, stderr: String): ProviderDiagnostic {
        val output = "$stdout\n$stderr"
        return when {
            QUOTA_FAILURE.containsMatchIn(output) -> ProviderDiagnostic.QUOTA_EXHAUSTED
            AUTH_FAILURE.containsMatchIn(output) -> ProviderDiagnostic.LOGIN_REQUIRED
            ARGUMENT_FAILURE.containsMatchIn(output) -> ProviderDiagnostic.UNSAFE_CAPABILITIES
            else -> ProviderDiagnostic.PROCESS_FAILED
        }
    }

    private fun parseVersion(value: String): String? = VERSION_PATTERN.find(value)?.value

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
        private val READ_ONLY_TOOLS = listOf("Read", "Glob", "Grep")
        private val CAPABILITY_ALIASES = mapOf(
            "--print" to listOf("--print", "-p"),
            "--output-format" to listOf("--output-format"),
            "--tools" to listOf("--tools"),
            "--allowed-tools" to listOf("--allowedTools", "--allowed-tools"),
            "--disallowed-tools" to listOf("--disallowedTools", "--disallowed-tools"),
            "--permission-mode" to listOf("--permission-mode"),
            "--max-turns" to listOf("--max-turns"),
            "--resume" to listOf("--resume", "-r"),
        )

        private val VERSION_PATTERN = Regex("""(?<![0-9])(\d+)\.(\d+)\.(\d+)(?![0-9])""")
        private val AUTH_BOOLEAN = Regex("""\"(?:loggedIn|authenticated)\"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
        private val AUTH_STATUS = Regex("""\"status\"\s*:\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
        private val QUOTA_FAILURE = Regex("""quota|usage\s+limit|rate\s+limit|credit\s+balance|limit\s+(?:reached|exceeded)""", RegexOption.IGNORE_CASE)
        private val AUTH_FAILURE = Regex("""not\s+logged\s+in|authentication|unauthorized|invalid\s+(?:token|credentials?)|\b401\b|please\s+login""", RegexOption.IGNORE_CASE)
        private val ARGUMENT_FAILURE = Regex("""unknown\s+(?:option|argument)|unrecognized\s+(?:option|argument)|invalid\s+option""", RegexOption.IGNORE_CASE)
        private const val PROBE_TIMEOUT_MILLIS = 8_000L
        private const val GENERATION_TIMEOUT_MILLIS = 75_000L
        private const val MAX_PROBE_OUTPUT_BYTES = 64 * 1024
        private const val MAX_STDOUT_BYTES = 128 * 1024
        private const val MAX_STDERR_BYTES = 32 * 1024
        private fun defaultNeutralWorkingDirectory(): Path {
            val directory = Path.of(System.getProperty("java.io.tmpdir"), "codex-ghost-text", "claude")
            return runCatching { Files.createDirectories(directory) }.getOrElse { Path.of(System.getProperty("java.io.tmpdir")) }
        }
    }
}
