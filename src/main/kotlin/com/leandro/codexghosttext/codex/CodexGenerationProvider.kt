package com.leandro.codexghosttext.codex

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.leandro.codexghosttext.generation.GenerationRequest
import com.leandro.codexghosttext.generation.GenerationResult
import com.leandro.codexghosttext.generation.LocalGenerationProvider
import com.leandro.codexghosttext.generation.ProviderDiagnostic
import com.leandro.codexghosttext.generation.ProviderId

/**
 * Provider-neutral facade for the existing Codex App Server implementation.
 *
 * The facade deliberately has no conversation persistence of its own: the wrapped project
 * services retain the established Codex thread and App Server lifecycle unchanged.
 */
@Service(Service.Level.PROJECT)
class CodexGenerationProvider private constructor(
    private val generate: (String, String, TextRange) -> GenerationResult,
    private val availability: () -> CodexDiagnostic,
    private val cancel: () -> Unit,
    private val isGenerating: () -> Boolean,
    private val reset: () -> Unit,
) : LocalGenerationProvider {
    constructor(project: Project) : this(
        generate = { comment, document, range ->
            project.getService(CodexGenerationService::class.java).generate(comment, document, range)
        },
        availability = { project.getService(CodexAvailabilityService::class.java).check() },
        cancel = { project.getService(CodexGenerationService::class.java).cancel() },
        isGenerating = { project.getService(CodexGenerationService::class.java).isGenerating() },
        reset = { project.getService(CodexGenerationService::class.java).resetConversation() },
    )

    internal constructor(
        generate: (String, String, TextRange) -> GenerationResult,
        availability: () -> CodexDiagnostic,
        cancel: () -> Unit,
        isGenerating: () -> Boolean,
        reset: () -> Unit,
        @Suppress("UNUSED_PARAMETER") testOnly: Boolean = true,
    ) : this(generate, availability, cancel, isGenerating, reset)

    override val providerId: ProviderId = ProviderId.CODEX

    override fun checkAvailability(): ProviderDiagnostic = when (availability()) {
        CodexDiagnostic.CHATGPT_READY -> ProviderDiagnostic.READY
        CodexDiagnostic.MISSING_EXECUTABLE -> ProviderDiagnostic.MISSING_EXECUTABLE
        CodexDiagnostic.LOGIN_REQUIRED, CodexDiagnostic.UNSUPPORTED_AUTH -> ProviderDiagnostic.LOGIN_REQUIRED
        CodexDiagnostic.QUOTA_EXHAUSTED -> ProviderDiagnostic.QUOTA_EXHAUSTED
        CodexDiagnostic.CONNECTION_FAILED -> ProviderDiagnostic.PROCESS_FAILED
    }

    override fun generate(request: GenerationRequest): GenerationResult =
        generate(request.comment, request.documentText, request.range)

    override fun cancel() = cancel.invoke()

    override fun isGenerating(): Boolean = isGenerating.invoke()

    override fun resetConversation() = reset.invoke()
}
