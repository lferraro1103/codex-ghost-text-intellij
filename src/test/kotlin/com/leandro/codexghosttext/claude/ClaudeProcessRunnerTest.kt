package com.leandro.codexghosttext.claude

import com.leandro.codexghosttext.generation.ProviderDiagnostic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path
import java.util.ArrayDeque

class ClaudeProcessRunnerTest {
    @Test
    fun `locator diagnoses standard macOS locations even when IntelliJ PATH is empty`() {
        val locator = PathClaudeExecutableLocator(path = "", userHome = "/Users/example", osName = "Mac OS X")
        val report = locator.diagnosticReport()

        assertTrue(report.contains("candidate[common]=<home>/.local/bin/claude status=missing"))
        assertTrue(report.contains("candidate[common]=/opt/homebrew/bin/claude"))
        assertTrue(report.contains("pathEntryCount=0"))
    }

    @Test
    fun `locator diagnoses Claude Code's own local install directory`() {
        val locator = PathClaudeExecutableLocator(path = "", userHome = "/Users/example", osName = "Mac OS X")

        assertTrue(locator.diagnosticReport().contains("candidate[provider]=<home>/.claude/local/claude"))
    }

    @Test
    fun `requires a ready profile before a generation command can be built`() {
        val executable = Path.of("C:/tools/claude.exe")
        val executor = RecordingClaudeCommandExecutor(
            ClaudeCommandResult(stdout = "2.1.259", exitCode = 0),
            ClaudeCommandResult(stdout = "--safe-mode", exitCode = 0),
        )
        val runner = DefaultClaudeProcessRunner(StaticClaudeExecutableLocator(executable), executor, Path.of("C:/plugin-neutral"))

        val result = runner.run(runner.probe(), ClaudeProcessRequest("generar codigo", null, Path.of("C:/project")))

        assertEquals(ProviderDiagnostic.UNSAFE_CAPABILITIES, result.diagnostic)
        assertEquals(2, executor.commands.size)
    }

    @Test
    fun `a ready profile is probed once instead of before every request`() {
        val executable = executableFile()
        val executor = RecordingClaudeCommandExecutor(
            ClaudeCommandResult(stdout = "2.1.259"),
            ClaudeCommandResult(stdout = ready()),
            ClaudeCommandResult(stdout = "{\"loggedIn\":true}"),
        )
        val runner = DefaultClaudeProcessRunner(StaticClaudeExecutableLocator(executable), executor, Path.of("."))

        val first = runner.probe()
        val second = runner.probe()

        assertEquals(ProviderDiagnostic.READY, first.diagnostic)
        assertEquals(ProviderDiagnostic.READY, second.diagnostic)
        assertEquals(3, executor.commands.size)
    }

    @Test
    fun `a probe that is not ready is never cached`() {
        val executable = executableFile()
        val executor = RecordingClaudeCommandExecutor(
            ClaudeCommandResult(stdout = "2.1.259"),
            ClaudeCommandResult(stdout = ready()),
            ClaudeCommandResult(stdout = "{\"loggedIn\":false}"),
            ClaudeCommandResult(stdout = "2.1.259"),
            ClaudeCommandResult(stdout = ready()),
            ClaudeCommandResult(stdout = "{\"loggedIn\":true}"),
        )
        val runner = DefaultClaudeProcessRunner(StaticClaudeExecutableLocator(executable), executor, Path.of("."))

        assertEquals(ProviderDiagnostic.LOGIN_REQUIRED, runner.probe().diagnostic)
        assertEquals(ProviderDiagnostic.READY, runner.probe().diagnostic)
        assertEquals(6, executor.commands.size)
    }

    @Test
    fun `the appended system prompt is sent only when the installed CLI advertises the flag`() {
        val executable = executableFile()
        val withFlag = RecordingClaudeCommandExecutor(
            ClaudeCommandResult(stdout = "2.1.259"),
            ClaudeCommandResult(stdout = ready() + " --append-system-prompt"),
            ClaudeCommandResult(stdout = "{\"loggedIn\":true}"),
            ClaudeCommandResult(stdout = "{\"session_id\":\"s\",\"structured_output\":{\"code\":\"fun x() = Unit\"}}"),
        )
        val runner = DefaultClaudeProcessRunner(StaticClaudeExecutableLocator(executable), withFlag, Path.of("."))
        val request = ClaudeProcessRequest("prompt", null, Path.of("."), "brief del proyecto")

        runner.run(runner.probe(), request)
        val arguments = withFlag.commands.last().arguments
        assertTrue(arguments.contains("--append-system-prompt"))
        assertEquals("brief del proyecto", arguments[arguments.indexOf("--append-system-prompt") + 1])

        val withoutFlag = RecordingClaudeCommandExecutor(
            ClaudeCommandResult(stdout = "2.1.259"),
            ClaudeCommandResult(stdout = ready()),
            ClaudeCommandResult(stdout = "{\"loggedIn\":true}"),
            ClaudeCommandResult(stdout = "{\"session_id\":\"s\",\"structured_output\":{\"code\":\"fun x() = Unit\"}}"),
        )
        val olderRunner = DefaultClaudeProcessRunner(StaticClaudeExecutableLocator(executable), withoutFlag, Path.of("."))
        olderRunner.run(olderRunner.probe(), request)

        assertFalse(withoutFlag.commands.last().arguments.contains("--append-system-prompt"))
    }

    @Test
    fun `cancellation is forwarded to the active process seam`() {
        val executor = RecordingClaudeCommandExecutor()
        val runner = DefaultClaudeProcessRunner(StaticClaudeExecutableLocator(Path.of("C:/tools/claude.exe")), executor, Path.of("C:/plugin-neutral"))

        runner.cancel()
        runner.dispose()

        assertEquals(2, executor.cancelCalls)
    }

    @Test
    fun `generation command exposes only project read tools in the requested project root`() {
        val projectRoot = "C:/work/private-project"
        val executor = RecordingClaudeCommandExecutor(
            ClaudeCommandResult(stdout = "2.1.259"),
            ClaudeCommandResult(
                stdout = (ClaudeProcessRunner.requiredCapabilityFlags + ClaudeProcessRunner.optionalCapabilityFlags).joinToString(" "),
            ),
            ClaudeCommandResult(stdout = "{\"loggedIn\":true}"),
            ClaudeCommandResult(stdout = "{\"structured_output\":{\"code\":\"fun sample() = Unit\"}}"),
        )
        val runner = DefaultClaudeProcessRunner(StaticClaudeExecutableLocator(Path.of("C:/tools/claude.exe")), executor, Path.of("C:/plugin-neutral"))

        val result = runner.run(runner.probe(), ClaudeProcessRequest("// crear método", "claude-session-1", Path.of(projectRoot)))

        assertNull(result.diagnostic)
        val command = executor.commands.last()
        assertEquals("-p", command.arguments[0])
        assertEquals("// crear método", command.arguments.last())
        assertEquals("Read,Glob,Grep", command.arguments[command.arguments.indexOf("--tools") + 1])
        assertEquals("Read,Glob,Grep", command.arguments[command.arguments.indexOf("--allowedTools") + 1])
        assertEquals("dontAsk", command.arguments[command.arguments.indexOf("--permission-mode") + 1])
        assertEquals("5", command.arguments[command.arguments.indexOf("--max-turns") + 1])
        assertEquals("claude-session-1", command.arguments[command.arguments.indexOf("--resume") + 1])
        assertEquals("mcp__*", command.arguments[command.arguments.indexOf("--disallowedTools") + 1])
        assertFalse(command.arguments.any { it.contains(projectRoot, ignoreCase = true) })
        assertFalse(command.arguments.filter { it.startsWith("--") }.any {
            it == "--add-dir" || it == "--continue" || it.contains("plugin", ignoreCase = true) || it.contains("browser", ignoreCase = true)
        })
        assertEquals(Path.of(projectRoot), command.workingDirectory)
        assertEquals(DefaultClaudeProcessRunner.credentialEnvironmentNames, command.environmentRemovals)
        assertTrue(command.timeoutMillis > 0)
        assertTrue(command.stdoutLimitBytes > 0)
        assertTrue(command.stderrLimitBytes > 0)
    }

    @Test
    fun `generation omits optional max turns when the installed CLI does not advertise it`() {
        val executor = RecordingClaudeCommandExecutor(
            ClaudeCommandResult(stdout = "2.1.205"),
            ClaudeCommandResult(stdout = ClaudeProcessRunner.requiredCapabilityFlags.joinToString(" ")),
            ClaudeCommandResult(stdout = "{\"loggedIn\":true}"),
            ClaudeCommandResult(stdout = "{\"result\":\"fun sample() = Unit\"}"),
        )
        val runner = DefaultClaudeProcessRunner(
            StaticClaudeExecutableLocator(Path.of("/opt/homebrew/bin/claude")),
            executor,
            Path.of("/tmp/plugin-neutral"),
        )

        val result = runner.run(runner.probe(), ClaudeProcessRequest("// generar", null, Path.of("/tmp/project")))

        assertNull(result.diagnostic)
        assertFalse("--max-turns" in executor.commands.last().arguments)
    }

    @Test
    fun `generation propagates bounded cancellation timeout and oversized output diagnostics`() {
        val timeout = readyRunner(ClaudeCommandResult(timedOut = true)).run()
        assertEquals(ProviderDiagnostic.PROCESS_TIMEOUT, timeout.diagnostic)

        val cancelled = readyRunner(ClaudeCommandResult(cancelled = true)).run()
        assertEquals(ProviderDiagnostic.CANCELLED, cancelled.diagnostic)

        val oversized = readyRunner(ClaudeCommandResult(stdoutTruncated = true)).run()
        assertEquals(ProviderDiagnostic.PROCESS_OUTPUT_TOO_LARGE, oversized.diagnostic)
    }

    @Test
    fun `generation classifies argument authentication and quota failures without leaking output`() {
        assertEquals(
            ProviderDiagnostic.UNSAFE_CAPABILITIES,
            readyRunner(ClaudeCommandResult(exitCode = 1, stderr = "unknown option --example")).run().diagnostic,
        )
        assertEquals(
            ProviderDiagnostic.LOGIN_REQUIRED,
            readyRunner(ClaudeCommandResult(exitCode = 1, stderr = "401 unauthorized")).run().diagnostic,
        )
        assertEquals(
            ProviderDiagnostic.QUOTA_EXHAUSTED,
            readyRunner(ClaudeCommandResult(exitCode = 1, stderr = "usage limit reached")).run().diagnostic,
        )
    }

    private fun readyRunner(generationResult: ClaudeCommandResult): ReadyRunner {
        val executor = RecordingClaudeCommandExecutor(
            ClaudeCommandResult(stdout = "2.1.259"),
            ClaudeCommandResult(stdout = ClaudeProcessRunner.requiredCapabilityFlags.joinToString(" ")),
            ClaudeCommandResult(stdout = "{\"authenticated\":true}"),
            generationResult,
        )
        val runner = DefaultClaudeProcessRunner(StaticClaudeExecutableLocator(Path.of("C:/tools/claude.exe")), executor, Path.of("C:/plugin-neutral"))
        return ReadyRunner(runner, runner.probe())
    }

    private data class ReadyRunner(
        val runner: ClaudeProcessRunner,
        val profile: ClaudeCapabilityProfile,
    ) {
        fun run(): ClaudeProcessResult = runner.run(profile, ClaudeProcessRequest("// generar", null, Path.of("C:/project")))
    }
}

internal class StaticClaudeExecutableLocator(private val executable: Path?) : ClaudeExecutableLocator {
    override fun find(): Path? = executable
}

private fun ready(): String = ClaudeProcessRunner.requiredCapabilityFlags.joinToString(" ")

/** A real file, because the probe cache is keyed by the executable's size and timestamp. */
private fun executableFile(): Path {
    val file = java.nio.file.Files.createTempFile("claude", "")
    file.toFile().setExecutable(true)
    file.toFile().deleteOnExit()
    return file
}

internal class RecordingClaudeCommandExecutor(vararg results: ClaudeCommandResult) : ClaudeCommandExecutor {
    private val scriptedResults = ArrayDeque(results.toList())
    val commands = mutableListOf<ClaudeCommand>()
    var cancelCalls = 0
        private set

    override fun execute(command: ClaudeCommand): ClaudeCommandResult {
        commands += command
        return if (scriptedResults.isEmpty()) ClaudeCommandResult() else scriptedResults.removeFirst()
    }

    override fun cancel() {
        cancelCalls += 1
    }
}
