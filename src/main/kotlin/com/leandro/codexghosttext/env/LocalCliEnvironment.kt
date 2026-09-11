package com.leandro.codexghosttext.env

import com.intellij.util.EnvironmentUtil

/**
 * The login-shell environment of the user, used to find and launch the local CLIs.
 *
 * On macOS and Linux an IDE started from Finder, the Dock, Spotlight, or a desktop launcher
 * inherits the session manager's minimal environment, so `System.getenv("PATH")` is typically
 * `/usr/bin:/bin:/usr/sbin:/sbin`. A Homebrew, npm, nvm, or version-manager install of `codex`
 * or `claude` is then invisible, and a Node-based CLI that is found still fails to start because
 * its interpreter is not on the inherited `PATH`. IntelliJ already resolves the login-shell
 * environment, so both providers resolve and launch through it.
 *
 * This never adds provider credentials: the caller's removals are applied last, so a key exported
 * by the user's shell profile is dropped exactly like one inherited from the IDE process.
 */
internal object LocalCliEnvironment {
    fun shellEnvironment(): Map<String, String> =
        runCatching { EnvironmentUtil.getEnvironmentMap() }
            .getOrNull()
            ?.takeIf(Map<String, String>::isNotEmpty)
            ?: System.getenv()

    fun searchPath(): String = shellEnvironment()["PATH"]?.takeIf(String::isNotBlank) ?: System.getenv("PATH").orEmpty()

    /** True when the login-shell `PATH` adds directories the IDE process does not have. */
    fun searchPathIsInherited(): Boolean = searchPath() == System.getenv("PATH").orEmpty()

    fun applyTo(builder: ProcessBuilder, removals: Set<String>) {
        applyTo(builder.environment(), shellEnvironment(), removals)
    }

    internal fun applyTo(
        target: MutableMap<String, String>,
        shellEnvironment: Map<String, String>,
        removals: Set<String>,
    ) {
        target.putAll(shellEnvironment)
        removals.forEach(target::remove)
    }
}
