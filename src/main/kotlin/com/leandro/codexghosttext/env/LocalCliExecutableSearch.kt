package com.leandro.codexghosttext.env

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Filesystem-only lookup of a locally installed CLI. It never invokes a shell, a login command,
 * or the executable it is looking for.
 *
 * `PATH` alone is not enough on macOS and Linux: see [LocalCliEnvironment]. The extra directories
 * are the documented install locations of the supported CLIs plus the user-level package manager
 * directories that a desktop-launched IDE does not inherit.
 */
internal object LocalCliExecutableSearch {
    data class Candidate(val source: String, val path: Path)

    fun find(
        names: List<String>,
        path: String,
        userHome: String,
        osName: String,
        providerDirectories: List<String> = emptyList(),
    ): Path? = candidates(names, path, userHome, osName, providerDirectories)
        .firstOrNull { candidate -> isExecutableFile(candidate.path) }
        ?.path

    fun candidates(
        names: List<String>,
        path: String,
        userHome: String,
        osName: String,
        providerDirectories: List<String> = emptyList(),
    ): List<Candidate> {
        val home = userHome.takeIf(String::isNotBlank)?.let { runCatching { Path.of(it) }.getOrNull() }
        val fromPath = path.split(File.pathSeparatorChar)
            .asSequence()
            .filter(String::isNotBlank)
            .mapNotNull { directory -> runCatching { Path.of(directory) }.getOrNull() }
            .flatMap { directory -> names.asSequence().map { name -> Candidate("PATH", directory.resolve(name)) } }
        val provider = providerDirectories
            .asSequence()
            .mapNotNull { directory -> home?.resolve(directory) }
            .flatMap { directory -> names.asSequence().map { name -> Candidate("provider", directory.resolve(name)) } }
        val common = commonDirectories(home, osName)
            .asSequence()
            .flatMap { directory -> names.asSequence().map { name -> Candidate("common", directory.resolve(name)) } }
        val nvm = nvmDirectories(home, osName)
            .asSequence()
            .flatMap { directory -> names.asSequence().map { name -> Candidate("nvm", directory.resolve(name)) } }
        return (fromPath + provider + common + nvm).distinctBy { it.path.normalize().toString() }.toList()
    }

    fun isExecutableFile(path: Path): Boolean = Files.isRegularFile(path) && Files.isExecutable(path)

    fun isWindows(osName: String): Boolean = osName.startsWith("Windows", ignoreCase = true)

    private fun commonDirectories(home: Path?, osName: String): List<Path> = buildList {
        if (home != null) addAll(
            listOf(
                home.resolve(".local/bin"),
                home.resolve("bin"),
                home.resolve(".npm-global/bin"),
                home.resolve(".npm/bin"),
                home.resolve(".npm-packages/bin"),
                home.resolve(".volta/bin"),
                home.resolve(".yarn/bin"),
                home.resolve(".config/yarn/global/node_modules/.bin"),
                home.resolve(".bun/bin"),
                home.resolve(".local/share/pnpm"),
                home.resolve("Library/pnpm"),
                // Version managers publish their active install as a shim directory.
                home.resolve(".local/share/mise/shims"),
                home.resolve(".asdf/shims"),
                home.resolve(".fnm/aliases/default/bin"),
            ),
        )
        if (!isWindows(osName)) {
            addAll(
                listOf(
                    // Homebrew on Apple silicon, Homebrew on Intel, and MacPorts.
                    Path.of("/opt/homebrew/bin"),
                    Path.of("/usr/local/bin"),
                    Path.of("/opt/local/bin"),
                    Path.of("/usr/bin"),
                ),
            )
        }
    }

    private fun nvmDirectories(home: Path?, osName: String): List<Path> {
        if (home == null || isWindows(osName)) return emptyList()
        val root = runCatching { home.resolve(".nvm/versions/node") }.getOrNull() ?: return emptyList()
        if (!Files.isDirectory(root)) return emptyList()
        return runCatching {
            Files.list(root).use { versions ->
                versions.filter(Files::isDirectory).map { it.resolve("bin") }.toList()
            }
        }.getOrDefault(emptyList())
    }
}
