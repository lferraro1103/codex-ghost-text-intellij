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
    // Named apart from the overridden member so the delegation cannot resolve back into itself.
    private val generateProposal: (GenerationRequest) -> GenerationResult,
    private val availability: () -> CodexDiagnostic,
    private val installed: () -> Boolean = { CodexExecutableLocator.find() != null },
    private val cancel: () -> Unit,
    private val isGenerating: () -> Boolean,
    private val reset: () -> Unit,
) : LocalGenerationProvider {
    constructor(project: Project) : this(
        generateProposal = { request -> project.getService(CodexGenerationService::class.java).generate(request) },
        availability = { project.getService(CodexAvailabilityService::class.java).check() },
        cancel = { project.getService(CodexGenerationService::class.java).cancel() },
        isGenerating = { project.getService(CodexGenerationService::class.java).isGenerating() },
        reset = {
            project.getService(CodexAvailabilityService::class.java).invalidate()
            project.getService(CodexGenerationService::class.java).resetConversation()
        },
    )

    internal constructor(
        generate: (GenerationRequest) -> GenerationResult,
        availability: () -> CodexDiagnostic,
        cancel: () -> Unit,
        isGenerating: () -> Boolean,
        reset: () -> Unit,
        installed: () -> Boolean = { true },
        @Suppress("UNUSED_PARAMETER") testOnly: Boolean = true,
    ) : this(
        generateProposal = generate,
        availability = availability,
        installed = installed,
        cancel = cancel,
        isGenerating = isGenerating,
        reset = reset,
    )

    override val providerId: ProviderId = ProviderId.CODEX

    override fun isInstalled(): Boolean = installed()

    override fun checkAvailability(): ProviderDiagnostic = when (availability()) {
        CodexDiagnostic.CHATGPT_READY -> ProviderDiagnostic.READY
        CodexDiagnostic.MISSING_EXECUTABLE -> ProviderDiagnostic.MISSING_EXECUTABLE
        CodexDiagnostic.LOGIN_REQUIRED, CodexDiagnostic.UNSUPPORTED_AUTH -> ProviderDiagnostic.LOGIN_REQUIRED
        CodexDiagnostic.QUOTA_EXHAUSTED -> ProviderDiagnostic.QUOTA_EXHAUSTED
        CodexDiagnostic.CONNECTION_FAILED -> ProviderDiagnostic.PROCESS_FAILED
    }

    override fun generate(request: GenerationRequest): GenerationResult {
        // Do the same non-secret preflight used by the Tools action before opening an App Server
        // thread. This turns missing CLI, account access, and exhausted quota into actionable UI
        // feedback instead of a generic connection failure.
        val diagnostic = availability()
        if (diagnostic != CodexDiagnostic.CHATGPT_READY) {
            return GenerationResult.Failure(diagnostic.generationFailureMessage())
        }
        return generateProposal(request)
    }

    override fun cancel() = cancel.invoke()

    override fun isGenerating(): Boolean = isGenerating.invoke()

    override fun resetConversation() = reset.invoke()
}

private fun CodexDiagnostic.generationFailureMessage(): String = when (this) {
    CodexDiagnostic.CHATGPT_READY -> error("A ready Codex diagnostic is not a generation failure.")
    CodexDiagnostic.MISSING_EXECUTABLE -> "No encontré Codex local. Instalalo o agregalo al PATH y volvé a intentar."
    CodexDiagnostic.LOGIN_REQUIRED, CodexDiagnostic.UNSUPPORTED_AUTH ->
        "Codex requiere una sesión de ChatGPT con acceso a Codex. Iniciá sesión con `codex login` y verificá tu plan."
    CodexDiagnostic.QUOTA_EXHAUSTED -> "La cuota de Codex está agotada. Esperá al próximo reinicio de cuota y volvé a intentar."
    CodexDiagnostic.CONNECTION_FAILED -> "No pude comprobar Codex local. Verificá la instalación, tu acceso y volvé a intentar."
}
