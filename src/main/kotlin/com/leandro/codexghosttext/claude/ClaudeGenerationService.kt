package com.leandro.codexghosttext.claude

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.leandro.codexghosttext.generation.GenerationRequest
import com.leandro.codexghosttext.generation.GenerationResult
import com.leandro.codexghosttext.generation.LocalGenerationProvider
import com.leandro.codexghosttext.generation.ProviderDiagnostic
import com.leandro.codexghosttext.generation.ProjectContextService
import com.leandro.codexghosttext.generation.ProviderId
import com.leandro.codexghosttext.actions.ProviderActionFeedback
import com.leandro.codexghosttext.generation.CodeProposal
import com.leandro.codexghosttext.generation.SourceLanguage
import com.leandro.codexghosttext.json.JsonValue
import com.leandro.codexghosttext.json.obj
import com.leandro.codexghosttext.json.literal
import com.leandro.codexghosttext.json.string
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
    // Constant for a given project: it rides in the appended system prompt, which the prompt cache
    // keys on, so it must not vary between the requests of one conversation.
    private val projectContextBrief: () -> String,
) : LocalGenerationProvider, Disposable {
    private val projectContext: String by lazy(projectContextBrief)
    constructor(project: Project) : this(
        DefaultClaudeProcessRunner(),
        project.getService(ClaudeProjectConversationState::class.java),
        { project.getService(ProjectContextService::class.java).brief() },
    )

    internal constructor(
        processRunner: ClaudeProcessRunner,
        conversationState: ClaudeProjectConversationState,
        @Suppress("UNUSED_PARAMETER") testOnly: Boolean = true,
    ) : this(processRunner, conversationState, { "" })

    private val generating = AtomicBoolean(false)

    override val providerId: ProviderId = ProviderId.CLAUDE

    override fun isInstalled(): Boolean = processRunner.isInstalled()

    override fun checkAvailability(): ProviderDiagnostic = processRunner.probe().diagnostic

    override fun generate(request: GenerationRequest): GenerationResult {
        if (!generating.compareAndSet(false, true)) return GenerationResult.Failure(ProviderDiagnostic.PROCESS_ALREADY_RUNNING.userMessage)
        try {
            val profile = processRunner.probe()
            // The provider-specific wording, so a failed generation says the same actionable thing
            // as Tools -> Check Selected AI Provider Connection.
            if (!profile.isReady) return GenerationResult.Failure(
                if (profile.diagnostic == ProviderDiagnostic.UNSAFE_CAPABILITIES) {
                    profile.userMessage
                } else {
                    ProviderActionFeedback.generationMessage(ProviderId.CLAUDE, profile.diagnostic)
                },
            )

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
        val result = processRunner.run(
            profile,
            ClaudeProcessRequest(prompt, sessionId, workingDirectory, projectContext),
        )
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
        private val SESSION_ID = Regex("[A-Za-z0-9._:-]{1,512}")

        fun parse(raw: String, language: String, fileName: String): ParsedProposal {
            val root = JsonValue.parseObject(raw)
                ?: return ParsedProposal.Failure("Claude no devolvió una respuesta estructurada válida.")
            val sessionId = root.string("session_id")?.takeIf { SESSION_ID.matches(it) }
                ?: return ParsedProposal.Failure("Claude no devolvió una sesión válida.")
            val structured = root.obj("structured_output")
            if (structured != null && root.values.keys != setOf("session_id", "structured_output")) {
                return ParsedProposal.Failure("Claude devolvió campos no permitidos.")
            }
            if (root.literal("is_error") == "true") {
                return ParsedProposal.Failure("Claude informó un error al generar el código.")
            }
            val answer = structured?.takeIf { it.values.keys == setOf("code") }?.string("code")
                ?: root.string("result")
                ?: return ParsedProposal.Failure("Claude no devolvió código estructurado.")
            CodeProposal.fenceTag(answer)?.let { tag ->
                if (SourceLanguage.fenceConflicts(tag, language, fileName)) {
                    return ParsedProposal.Failure("Claude respondió en $tag y el archivo no está en ese lenguaje.")
                }
            }
            val code = CodeProposal.withoutFence(answer)
            if (!CodeProposal.isInsertable(code)) {
                return ParsedProposal.Failure("Claude no devolvió una propuesta de código utilizable.")
            }
            return ParsedProposal.Success(code, sessionId)
        }
    }
}

private fun String.nearbyWindow(range: TextRange): String {
    val start = (range.startOffset - 2_000).coerceAtLeast(0)
    val end = (range.endOffset + 4_000).coerceAtMost(length)
    return substring(start, end)
}
