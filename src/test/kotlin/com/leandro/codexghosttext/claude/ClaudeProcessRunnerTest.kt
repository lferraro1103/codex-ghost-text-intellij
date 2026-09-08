package com.leandro.codexghosttext.claude

import com.leandro.codexghosttext.generation.ProviderDiagnostic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

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
