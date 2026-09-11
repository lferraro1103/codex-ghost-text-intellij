package com.leandro.codexghosttext.env

import com.leandro.codexghosttext.codex.CodexExecutableLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class LocalCliEnvironmentTest {
    @Test
    fun `the login shell environment never reaches a subprocess with provider credentials`() {
        val target = mutableMapOf("PATH" to "/usr/bin", "CODEX_API_KEY" to "inherited-from-ide")

        LocalCliEnvironment.applyTo(
            target,
            shellEnvironment = mapOf("PATH" to "/opt/homebrew/bin:/usr/bin", "OPENAI_API_KEY" to "exported-by-profile"),
            removals = setOf("CODEX_API_KEY", "OPENAI_API_KEY"),
        )

        assertEquals("/opt/homebrew/bin:/usr/bin", target["PATH"])
        assertFalse(target.containsKey("CODEX_API_KEY"))
        assertFalse(target.containsKey("OPENAI_API_KEY"))
    }

    @Test
    fun `codex is found in a Homebrew style directory that the IDE PATH does not contain`() {
        assumeFalse(LocalCliExecutableSearch.isWindows(System.getProperty("os.name").orEmpty()))
        val home = Files.createTempDirectory("codex-ghost-text-home")
        val installed = executableFile(home.resolve(".codex/bin/codex"))

        val found = CodexExecutableLocator.find(path = "", localAppData = null, userHome = home.toString(), osName = "Mac OS X")

        assertEquals(installed, found)
    }

    @Test
    fun `a candidate that is not executable is never selected`() {
        assumeFalse(LocalCliExecutableSearch.isWindows(System.getProperty("os.name").orEmpty()))
        val home = Files.createTempDirectory("codex-ghost-text-home")
        val directory = Files.createDirectories(home.resolve(".codex/bin"))
        val notExecutable = Files.createFile(directory.resolve("codex"))
        notExecutable.toFile().setExecutable(false)

        val found = CodexExecutableLocator.find(path = "", localAppData = null, userHome = home.toString(), osName = "Mac OS X")

        assertNotEquals(notExecutable, found)
    }

    @Test
    fun `macOS lookup covers Homebrew MacPorts and version manager shims`() {
        val candidates = LocalCliExecutableSearch
            .candidates(listOf("claude"), path = "", userHome = "/Users/example", osName = "Mac OS X")
            .map { it.path.toString() }

        assertTrue(candidates.contains("/opt/homebrew/bin/claude"))
        assertTrue(candidates.contains("/usr/local/bin/claude"))
        assertTrue(candidates.contains("/opt/local/bin/claude"))
        assertTrue(candidates.contains("/Users/example/.local/share/mise/shims/claude"))
    }

    private fun executableFile(path: Path): Path {
        Files.createDirectories(path.parent)
        Files.createFile(path)
        path.toFile().setExecutable(true)
        return path
    }
}
