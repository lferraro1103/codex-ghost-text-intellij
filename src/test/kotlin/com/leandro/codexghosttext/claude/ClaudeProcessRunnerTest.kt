package com.leandro.codexghosttext.claude

import com.leandro.codexghosttext.generation.ProviderDiagnostic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val result = runner.run(runner.probe(), ClaudeProcessRequest("generar codigo", null))

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
