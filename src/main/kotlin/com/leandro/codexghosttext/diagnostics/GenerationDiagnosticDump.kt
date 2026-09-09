package com.leandro.codexghosttext.diagnostics

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.leandro.codexghosttext.claude.DefaultClaudeProcessRunner
import com.leandro.codexghosttext.claude.PathClaudeExecutableLocator
import com.leandro.codexghosttext.codex.CodexExecutableLocator
import com.leandro.codexghosttext.generation.ProviderId
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Writes a small, shareable failure report without prompts, source code, PATH, or credentials. */
internal object GenerationDiagnosticDump {
    private val pluginVersion: String by lazy {
        GenerationDiagnosticDump::class.java.classLoader
            .getResourceAsStream("codex-ghost-text-version.properties")
            ?.bufferedReader()
            ?.useLines { lines ->
                lines.firstOrNull { it.startsWith("pluginVersion=") }
                    ?.substringAfter('=')
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
            }
            ?: "unknown"
    }

    fun write(
        project: Project,
        providerId: ProviderId,
        failureMessage: String,
        source: String,
    ): Path? = runCatching {
        val directory = Path.of(PathManager.getLogPath(), "codex-ghost-text")
        Files.createDirectories(directory)
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-')
        val file = directory.resolve("failure-$timestamp-${UUID.randomUUID()}.txt")
        Files.writeString(file, render(providerId, failureMessage, source, project.basePath))
        file
    }.getOrNull()

    internal fun render(
        providerId: ProviderId,
        failureMessage: String,
        source: String,
        projectRoot: String?,
    ): String = buildString {
        appendLine("Codex Ghost Text diagnostic dump")
        appendLine("timestamp=${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}")
        appendLine("pluginVersion=$pluginVersion")
        appendLine("provider=$providerId")
        appendLine("source=${sanitize(source)}")
        appendLine("failure=${sanitize(failureMessage)}")
        appendLine("projectRoot=${sanitize(projectRoot ?: "<unavailable>")}")
        appendLine("os=${sanitize(System.getProperty("os.name"))} ${sanitize(System.getProperty("os.version"))}")
        appendLine("java=${sanitize(System.getProperty("java.version"))}")
        appendLine("codexExecutable=${CodexExecutableLocator.find()?.toString() ?: "<not found>"}")
        val claudeLocator = PathClaudeExecutableLocator()
        appendLine("claudeExecutable=${claudeLocator.find()?.toString() ?: "<not found>"}")
        appendLine("claudeLookup:")
        appendLine(claudeLocator.diagnosticReport().prependIndent("  "))
        if (providerId == ProviderId.CLAUDE) {
            val claudeProfile = DefaultClaudeProcessRunner().probe()
            appendLine("claudeProbeDiagnostic=${claudeProfile.diagnostic}")
            appendLine("claudeVersion=${claudeProfile.version ?: "<unavailable>"}")
            appendLine("claudeSupportedCapabilities=${claudeProfile.supportedFlags.sorted().joinToString(",").ifBlank { "<none>" }}")
            appendLine("claudeMissingCapabilities=${claudeProfile.missingFlags.sorted().joinToString(",").ifBlank { "<none>" }}")
        }
        appendLine("redaction=No source code, prompts, PATH values, account data, authentication tokens, or CLI output are included.")
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("(?i)(api[_-]?key|token|password|secret)\\s*[:=]\\s*\\S+"), "$1=<redacted>")
        .replace(Regex("[\\r\\n]+"), " ")
        .take(1_000)
}
