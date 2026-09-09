package com.leandro.codexghosttext.claude

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.leandro.codexghosttext.generation.GenerationRequest
import com.leandro.codexghosttext.generation.GenerationResult
import com.leandro.codexghosttext.generation.LocalGenerationProvider
import com.leandro.codexghosttext.generation.ProviderDiagnostic
import com.leandro.codexghosttext.generation.ProviderId
import com.leandro.codexghosttext.generation.SourceLanguage
import java.lang.StringBuilder
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Claude's project-scoped CLI provider. It receives the IntelliJ project's root as its working
 * directory and may inspect source files with explicitly allow-listed read-only tools.
 */
@Service(Service.Level.PROJECT)
class ClaudeGenerationService private constructor(
    private val processRunner: ClaudeProcessRunner,
    private val conversationState: ClaudeProjectConversationState,
) : LocalGenerationProvider, Disposable {
    constructor(project: Project) : this(
        DefaultClaudeProcessRunner(),
        project.getService(ClaudeProjectConversationState::class.java),
    )

    internal constructor(
        processRunner: ClaudeProcessRunner,
        conversationState: ClaudeProjectConversationState,
        @Suppress("UNUSED_PARAMETER") testOnly: Boolean = true,
    ) : this(processRunner, conversationState)

    private val generating = AtomicBoolean(false)

    override val providerId: ProviderId = ProviderId.CLAUDE

    override fun checkAvailability(): ProviderDiagnostic = processRunner.probe().diagnostic

    override fun generate(request: GenerationRequest): GenerationResult {
        if (!generating.compareAndSet(false, true)) return GenerationResult.Failure(ProviderDiagnostic.PROCESS_ALREADY_RUNNING.userMessage)
        try {
            val profile = processRunner.probe()
            if (!profile.isReady) return GenerationResult.Failure(profile.userMessage)

            val prompt = buildPrompt(request)
            val savedSession = conversationState.sessionFor(request.projectRoot)
            val first = generateOnce(profile, prompt, savedSession, request)
            if (first is ParsedProposal.Success) {
                conversationState.remember(request.projectRoot, first.sessionId)
                return GenerationResult.Success(first.code)
            }

            if (savedSession != null) return GenerationResult.Failure(
                "No pude reanudar la conversación de Claude de este proyecto. Usá ‘Reiniciar conversación del proveedor’ solo si querés crear una nueva.",
            )
            return GenerationResult.Failure((first as ParsedProposal.Failure).message)
        } finally {
            generating.set(false)
        }
    }

    override fun cancel() = processRunner.cancel()

    override fun isGenerating(): Boolean = generating.get()

    /** Forgets only Claude's persisted session; Codex owns separate state and is untouched. */
    override fun resetConversation() {
        cancel()
        conversationState.forget()
    }

    override fun dispose() = processRunner.dispose()

    private fun generateOnce(
        profile: ClaudeCapabilityProfile,
        prompt: String,
        sessionId: String?,
        request: GenerationRequest,
    ): ParsedProposal {
        val workingDirectory = runCatching { Path.of(request.projectRoot) }.getOrNull()
            ?: return ParsedProposal.Failure("El proyecto no tiene una carpeta válida para Claude.")
        val result = processRunner.run(profile, ClaudeProcessRequest(prompt, sessionId, workingDirectory))
        result.diagnostic?.let {
            return ParsedProposal.Failure(it.userMessage, mayRetryFresh = it == ProviderDiagnostic.PROCESS_FAILED)
        }
        if (result.exitCode != 0 || result.stdoutTruncated || result.stderrTruncated) {
            return ParsedProposal.Failure(ProviderDiagnostic.PROCESS_FAILED.userMessage, mayRetryFresh = true)
        }
        return ClaudeEnvelopeParser.parse(result.stdout, request.language, request.fileName)
    }

    private fun buildPrompt(request: GenerationRequest): String = """
        Devolvé únicamente código nuevo, sin explicación ni Markdown. Podés consultar los
        archivos del proyecto actual solo con herramientas de lectura si ese contexto es necesario.
        No ejecutes comandos, no navegues la web y no modifiques archivos.
        ${SourceLanguage.instruction(request)}

        Comentario seleccionado:
        ${request.comment}

        Contexto cercano:
        ${request.documentText.nearbyWindow(request.range)}
    """.trimIndent()

    private sealed interface ParsedProposal {
        data class Success(val code: String, val sessionId: String) : ParsedProposal
        data class Failure(val message: String, val mayRetryFresh: Boolean = false) : ParsedProposal
    }

    private object ClaudeEnvelopeParser {
        private const val MAX_CODE_CHARS = 16_000
        private const val MAX_CODE_LINES = 80
        private val SESSION_ID = Regex("[A-Za-z0-9._:-]{1,512}")

        /**
         * A declaration, annotation, comment, or statement in the languages an IntelliJ-based IDE
         * edits. The prose check above is what rejects an explanation; this only keeps a proposal
         * that still reads like natural language out of the document.
         */
        private val CODE_FIRST_LINE = Regex(
            """(?:@\w+.*|#.*|//.*|/\*.*|<[A-Za-z/!].*|[}\])].*|""" +
                """(?:public|private|protected|internal|open|abstract|final|sealed|static|synchronized|""" +
                """suspend|inline|override|operator|companion|fun|func|def|lambda|class|interface|object|""" +
                """struct|enum|record|trait|impl|module|namespace|package|import|export|from|require|use|""" +
                """val|var|let|const|function|async|await|return|yield|throw|new|delete|if|for|while|do|""" +
                """switch|when|try|with|type|typedef|template|typename|auto|void|boolean|bool|byte|short|""" +
                """int|uint|long|float|double|char|str|String|Int|Long|Double|Boolean|Float|Char|Any|Unit)\b.*|""" +
                """[A-Za-z_$][\w$]*(?:[.<\[]|\s*\(|\s*[:=]|\s+[A-Za-z_$*&]).*)""",
        )
        private val FENCE_TAG = Regex("[A-Za-z0-9+#._-]{0,20}")
        private val COMMENT_OR_ANNOTATION = Regex("""^\s*(?://|/\*|#|@\w)""")
        private const val SYNTAX_CHARACTERS = "(){}[];=<>:,"

        fun parse(raw: String, language: String, fileName: String): ParsedProposal {
            val root = runCatching { StrictJsonReader(raw).read() }.getOrNull() as? JsonValue.ObjectValue
                ?: return ParsedProposal.Failure("Claude no devolvió una respuesta estructurada válida.")
            val sessionId = (root.values["session_id"] as? JsonValue.StringValue)?.value
                ?.takeIf { SESSION_ID.matches(it) }
                ?: return ParsedProposal.Failure("Claude no devolvió una sesión válida.")
            val structured = root.values["structured_output"] as? JsonValue.ObjectValue
            if (structured != null && root.values.keys != setOf("session_id", "structured_output")) {
                return ParsedProposal.Failure("Claude devolvió campos no permitidos.")
            }
            if ((root.values["is_error"] as? JsonValue.LiteralValue)?.value == "true") {
                return ParsedProposal.Failure("Claude informó un error al generar el código.")
            }
            val code = ((structured?.values?.takeIf { it.keys == setOf("code") }?.get("code") as? JsonValue.StringValue)?.value
                ?: (root.values["result"] as? JsonValue.StringValue)?.value
                ?: return ParsedProposal.Failure("Claude no devolvió código estructurado."))
            val fenceTag = code.fenceTag()
            if (fenceTag != null && SourceLanguage.fenceConflicts(fenceTag, language, fileName)) {
                return ParsedProposal.Failure("Claude respondió en $fenceTag y el archivo no está en ese lenguaje.")
            }
            val unwrapped = code.withoutCodeFence()
            if (!unwrapped.isCodeOnlyProposal()) return ParsedProposal.Failure("Claude no devolvió una propuesta de código utilizable.")
            return ParsedProposal.Success(unwrapped, sessionId)
        }

        /** The language tag of a wrapping fence, when the answer is exactly one fenced block. */
        private fun String.fenceTag(): String? {
            val text = trim()
            if (!text.startsWith("```") || !text.endsWith("```")) return null
            val opening = text.indexOf('\n').takeIf { it > 0 } ?: return null
            return text.substring(3, opening).trim().takeIf { it.matches(FENCE_TAG) && it.isNotEmpty() }
        }

        /**
         * Claude wraps a code-only answer in a Markdown fence often enough that rejecting the
         * proposal would waste a usable generation. Exactly one wrapping fence is unwrapped; any
         * other fence still fails the code-only check below, so no Markdown can reach the document.
         */
        private fun String.withoutCodeFence(): String {
            val text = trim()
            if (!text.startsWith("```") || !text.endsWith("```")) return text
            val opening = text.indexOf('\n')
            if (opening < 0) return text
            // Only a language tag may follow the opening fence.
            if (!text.substring(3, opening).trim().matches(FENCE_TAG)) return text
            return text.substring(opening + 1, text.length - 3).trim()
        }

        private fun String.isCodeOnlyProposal(): Boolean {
            if (isBlank() || length > MAX_CODE_CHARS || lines().size > MAX_CODE_LINES || contains("```")) return false
            val first = lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            if (first.matches(Regex("(?i)^(voy|claro|sure|here|i |the |this |aqui|aquí|generar|implement).*$"))) return false
            if (!first.matches(CODE_FIRST_LINE)) return false
            // A sentence can also open with a keyword ("Return the sum of ..."), so an accepted
            // line must carry syntax as well, unless it is a comment or an annotation.
            return COMMENT_OR_ANNOTATION.containsMatchIn(first) || first.any { it in SYNTAX_CHARACTERS }
        }
    }
}

private fun String.nearbyWindow(range: TextRange): String {
    val start = (range.startOffset - 2_000).coerceAtLeast(0)
    val end = (range.endOffset + 4_000).coerceAtMost(length)
    return substring(start, end)
}

private sealed interface JsonValue {
    data class ObjectValue(val values: Map<String, JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class LiteralValue(val value: String) : JsonValue
}

/** Tiny strict parser: it deliberately rejects duplicate object keys and trailing values. */
private class StrictJsonReader(private val input: String) {
    private var index = 0

    fun read(): JsonValue {
        skipWhitespace()
        val value = readValue()
        skipWhitespace()
        require(index == input.length) { "Trailing JSON content" }
        return value
    }

    private fun readValue(): JsonValue = when (peek()) {
        '{' -> readObject()
        '"' -> JsonValue.StringValue(readString())
        else -> JsonValue.LiteralValue(readLiteral())
    }

    private fun readObject(): JsonValue.ObjectValue {
        expect('{')
        skipWhitespace()
        val values = linkedMapOf<String, JsonValue>()
        if (consume('}')) return JsonValue.ObjectValue(values)
        while (true) {
            skipWhitespace()
            require(peek() == '"') { "Object key required" }
            val key = readString()
            require(!values.containsKey(key)) { "Duplicate object key" }
            skipWhitespace()
            expect(':')
            skipWhitespace()
            values[key] = readValue()
            skipWhitespace()
            if (consume('}')) break
            expect(',')
        }
        return JsonValue.ObjectValue(values)
    }

    private fun readString(): String {
        expect('"')
        val value = StringBuilder()
        while (index < input.length) {
            val character = input[index++]
            when (character) {
                '"' -> return value.toString()
                '\\' -> value.append(readEscape())
                else -> {
                    require(character.code >= 0x20) { "Control character" }
                    value.append(character)
                }
            }
        }
        error("Unterminated string")
    }

    private fun readEscape(): Char {
        require(index < input.length) { "Unterminated escape" }
        return when (val escaped = input[index++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                require(index + 4 <= input.length) { "Short unicode escape" }
                val hex = input.substring(index, index + 4)
                index += 4
                hex.toIntOrNull(16)?.toChar() ?: error("Invalid unicode escape")
            }
            else -> error("Invalid escape $escaped")
        }
    }

    private fun readLiteral(): String {
        val start = index
        while (index < input.length && input[index] !in " \t\r\n,}") index++
        require(start != index) { "Value required" }
        return input.substring(start, index)
    }

    private fun peek(): Char = input.getOrNull(index) ?: error("Unexpected end of JSON")

    private fun expect(character: Char) {
        require(consume(character)) { "Expected $character" }
    }

    private fun consume(character: Char): Boolean = if (input.getOrNull(index) == character) {
        index++
        true
    } else {
        false
    }

    private fun skipWhitespace() {
        while (input.getOrNull(index)?.isWhitespace() == true) index++
    }
}
