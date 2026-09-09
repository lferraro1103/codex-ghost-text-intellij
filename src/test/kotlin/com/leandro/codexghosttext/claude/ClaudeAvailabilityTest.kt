package com.leandro.codexghosttext.claude

import com.leandro.codexghosttext.generation.ProviderDiagnostic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class ClaudeAvailabilityTest {
    @Test
    fun `recognizes documented short and camel case capability aliases from either help stream`() {
        val executable = Path.of("C:/tools/claude.exe")
        val help = "-p --output-format --tools --allowedTools --disallowedTools --permission-mode --max-turns -r"
        val runner = DefaultClaudeProcessRunner(
            StaticClaudeExecutableLocator(executable),
            RecordingClaudeCommandExecutor(
                ClaudeCommandResult(stdout = "Claude Code 2.1.100", exitCode = 0),
                ClaudeCommandResult(stderr = help, exitCode = 0),
                ClaudeCommandResult(stdout = "{\"loggedIn\":true}", exitCode = 0),
            ),
            Path.of("C:/plugin-neutral"),
        )

        val profile = runner.probe()

        assertEquals(ProviderDiagnostic.READY, profile.diagnostic)
        assertEquals("2.1.100", profile.version)
        assertTrue(profile.missingFlags.isEmpty())
    }

    @Test
    fun `reports ready only when one resolved executable passes version help and auth`() {
        val executable = Path.of("C:/tools/claude.exe")
        val executor = RecordingClaudeCommandExecutor(
            ClaudeCommandResult(stdout = "2.1.259", exitCode = 0),
            ClaudeCommandResult(stdout = ClaudeProcessRunner.requiredCapabilityFlags.joinToString(" "), exitCode = 0),
            ClaudeCommandResult(stdout = "{\"loggedIn\":true}", exitCode = 0),
        )
        val runner = DefaultClaudeProcessRunner(StaticClaudeExecutableLocator(executable), executor, Path.of("C:/plugin-neutral"))

        val profile = runner.probe()

        assertEquals(ProviderDiagnostic.READY, profile.diagnostic)
        assertEquals(executable, profile.executable)
        assertEquals(setOf(executable), executor.commands.map { it.executable }.toSet())
        assertEquals(listOf(listOf("--version"), listOf("--help"), listOf("auth", "status")), executor.commands.map { it.arguments })
        executor.commands.forEach { command ->
            assertEquals(DefaultClaudeProcessRunner.credentialEnvironmentNames, command.environmentRemovals)
            assertEquals(Path.of("C:/plugin-neutral"), command.workingDirectory)
            assertTrue(command.timeoutMillis > 0)
        }
    }

    @Test
    fun `keeps each unavailable capability failure actionable and never launches login`() {
        val executable = Path.of("C:/tools/claude.exe")

        val olderButCapable = DefaultClaudeProcessRunner(
            StaticClaudeExecutableLocator(executable),
            RecordingClaudeCommandExecutor(
                ClaudeCommandResult(stdout = "2.1.258", exitCode = 0),
                ClaudeCommandResult(stdout = ClaudeProcessRunner.requiredCapabilityFlags.joinToString(" "), exitCode = 0),
                ClaudeCommandResult(stdout = "{\"loggedIn\":true}", exitCode = 0),
            ),
            Path.of("C:/plugin-neutral"),
        )
        assertEquals(ProviderDiagnostic.READY, olderButCapable.probe().diagnostic)
        assertEquals(
            ProviderDiagnostic.VERSION_UNPARSEABLE,
            DefaultClaudeProcessRunner(
                StaticClaudeExecutableLocator(executable),
                RecordingClaudeCommandExecutor(ClaudeCommandResult(stdout = "not a version", exitCode = 0)),
                Path.of("C:/plugin-neutral"),
            ).probe().diagnostic,
        )

        val unsafeExecutor = RecordingClaudeCommandExecutor(
            ClaudeCommandResult(stdout = "2.1.259", exitCode = 0),
            ClaudeCommandResult(stdout = "--safe-mode --output-format", exitCode = 0),
        )
        assertEquals(
            ProviderDiagnostic.UNSAFE_CAPABILITIES,
            DefaultClaudeProcessRunner(StaticClaudeExecutableLocator(executable), unsafeExecutor, Path.of("C:/plugin-neutral")).probe().diagnostic,
        )

        val unauthenticatedExecutor = RecordingClaudeCommandExecutor(
            ClaudeCommandResult(stdout = "2.1.259", exitCode = 0),
            ClaudeCommandResult(stdout = ClaudeProcessRunner.requiredCapabilityFlags.joinToString(" "), exitCode = 0),
            ClaudeCommandResult(stdout = "{\"loggedIn\":false}", exitCode = 1),
        )
        assertEquals(
            ProviderDiagnostic.LOGIN_REQUIRED,
            DefaultClaudeProcessRunner(StaticClaudeExecutableLocator(executable), unauthenticatedExecutor, Path.of("C:/plugin-neutral")).probe().diagnostic,
        )
        assertFalse(unauthenticatedExecutor.commands.flattenArguments().any { it.contains("login", ignoreCase = true) })

        val malformedExecutor = RecordingClaudeCommandExecutor(
            ClaudeCommandResult(stdout = "2.1.259", exitCode = 0),
            ClaudeCommandResult(stdout = ClaudeProcessRunner.requiredCapabilityFlags.joinToString(" "), exitCode = 0),
            ClaudeCommandResult(stdout = "status unknown", exitCode = 0),
        )
        assertEquals(
            ProviderDiagnostic.AUTH_STATUS_MALFORMED,
            DefaultClaudeProcessRunner(StaticClaudeExecutableLocator(executable), malformedExecutor, Path.of("C:/plugin-neutral")).probe().diagnostic,
        )
    }

    @Test
    fun `reports missing timeout and nonzero command failures separately`() {
        assertEquals(
            ProviderDiagnostic.MISSING_EXECUTABLE,
            DefaultClaudeProcessRunner(StaticClaudeExecutableLocator(null), RecordingClaudeCommandExecutor(), Path.of("C:/plugin-neutral")).probe().diagnostic,
        )

        val executable = Path.of("C:/tools/claude.exe")
        assertEquals(
            ProviderDiagnostic.PROCESS_TIMEOUT,
            DefaultClaudeProcessRunner(
                StaticClaudeExecutableLocator(executable),
                RecordingClaudeCommandExecutor(ClaudeCommandResult(timedOut = true)),
                Path.of("C:/plugin-neutral"),
            ).probe().diagnostic,
        )
        assertEquals(
            ProviderDiagnostic.VERSION_COMMAND_FAILED,
            DefaultClaudeProcessRunner(
                StaticClaudeExecutableLocator(executable),
                RecordingClaudeCommandExecutor(ClaudeCommandResult(exitCode = 9, stderr = "failed")),
                Path.of("C:/plugin-neutral"),
            ).probe().diagnostic,
        )
    }
}

private fun List<ClaudeCommand>.flattenArguments(): List<String> = flatMap(ClaudeCommand::arguments)
