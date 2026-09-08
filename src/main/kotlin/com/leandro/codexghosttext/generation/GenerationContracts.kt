package com.leandro.codexghosttext.generation

import com.intellij.openapi.util.TextRange

/** The two locally authenticated CLIs that can be selected explicitly by the user. */
enum class ProviderId {
    CODEX,
    CLAUDE,
}

/**
 * Immutable editor data passed to a locally selected provider.
 *
 * [projectRoot] is plugin metadata used only for provider-specific state routing. It is not
 * prompt material and Claude must never use it as a process working directory.
 */
data class GenerationRequest(
    val comment: String,
    val documentText: String,
    val range: TextRange,
    val projectRoot: String,
)

sealed interface GenerationResult {
    data class Success(val code: String) : GenerationResult
    data class Failure(val message: String) : GenerationResult
}

/** Non-secret, actionable result of checking a local provider executable. */
enum class ProviderDiagnostic(val isReady: Boolean, val userMessage: String) {
    READY(true, "Listo para generar código."),
    MISSING_EXECUTABLE(false, "No encontré la CLI local."),
    VERSION_UNSUPPORTED(false, "La versión instalada no es compatible."),
    VERSION_UNPARSEABLE(false, "No pude verificar la versión instalada."),
    VERSION_COMMAND_FAILED(false, "La CLI no pudo informar su versión."),
    HELP_COMMAND_FAILED(false, "La CLI no pudo informar sus capacidades."),
    UNSAFE_CAPABILITIES(false, "La CLI no admite el modo seguro sin herramientas requerido."),
    LOGIN_REQUIRED(false, "Iniciá sesión en la CLI local antes de generar."),
    AUTH_STATUS_MALFORMED(false, "La CLI devolvió un estado de sesión no verificable."),
    AUTH_STATUS_FAILED(false, "No pude comprobar el estado de sesión de la CLI."),
    PROCESS_TIMEOUT(false, "La CLI tardó demasiado en responder."),
    PROCESS_OUTPUT_TOO_LARGE(false, "La CLI devolvió demasiada salida."),
    PROCESS_FAILED(false, "La CLI local no pudo completar la operación."),
    PROCESS_ALREADY_RUNNING(false, "Ya hay una generación en curso."),
    CANCELLED(false, "La generación fue cancelada."),
}

/**
 * Provider-neutral lifecycle used by the router planned for the next phase blocks.
 * Implementations never write to an IntelliJ document; they only return a proposal value.
 */
interface LocalGenerationProvider {
    val providerId: ProviderId

    fun checkAvailability(): ProviderDiagnostic

    fun generate(request: GenerationRequest): GenerationResult

    fun cancel()

    fun isGenerating(): Boolean

    fun resetConversation()
}
