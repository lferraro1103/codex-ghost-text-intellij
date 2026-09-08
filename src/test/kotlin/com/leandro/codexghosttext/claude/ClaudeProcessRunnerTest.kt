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
            ClaudeCommandResult(stdout = ClaudeProcessRunner.requiredCapabilityFlags.joinToString(" ")),
            ClaudeCommandResult(stdout = "{\"loggedIn\":true}"),
            ClaudeCommandResult(stdout = "{\"structured_output\":{\"code\":\"fun sample() = Unit\"}}"),
        )
        val runner = DefaultClaudeProcessRunner(StaticClaudeExecutableLocator(Path.of("C:/tools/claude.exe")), executor, Path.of("C:/plugin-neutral"))

        val result = runner.run(runner.probe(), ClaudeProcessRequest("// crear método", "claude-session-1", Path.of(projectRoot)))

        assertNull(result.diagnostic)
        val command = executor.commands.last()
        assertEquals("-p", command.arguments[0])
        assertEquals("// crear método", command.arguments[1])
        assertEquals("Read,Glob,Grep", command.arguments[command.arguments.indexOf("--allowedTools") + 1])
        assertEquals("none", command.arguments[command.arguments.indexOf("--permission-prompts") + 1])
        assertEquals("1", command.arguments[command.arguments.indexOf("--max-turns") + 1])
        assertEquals("claude-session-1", command.arguments[command.arguments.indexOf("--resume") + 1])
        listOf("Bash", "Edit", "Write", "WebFetch", "WebSearch", "Agent", "NotebookEdit", "MCP", "Browser", "ProjectInspection", "mcp__*")
            .forEach { denied -> assertTrue(command.arguments[command.arguments.indexOf("--disallowedTools") + 1].contains(denied)) }
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
    fun `generation propagates bounded cancellation timeout and oversized output diagnostics`() {
        val timeout = readyRunner(ClaudeCommandResult(timedOut = true)).run()
        assertEquals(ProviderDiagnostic.PROCESS_TIMEOUT, timeout.diagnostic)

        val cancelled = readyRunner(ClaudeCommandResult(cancelled = true)).run()
        assertEquals(ProviderDiagnostic.CANCELLED, cancelled.diagnostic)

        val oversized = readyRunner(ClaudeCommandResult(stdoutTruncated = true)).run()
        assertEquals(ProviderDiagnostic.PROCESS_OUTPUT_TOO_LARGE, oversized.diagnostic)
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
