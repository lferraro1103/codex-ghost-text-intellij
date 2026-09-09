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
 * [projectRoot] is not prompt material. Providers use it to attach their reusable project
 * conversation to the IntelliJ project's directory under their respective read-only policies.
 */
data class GenerationRequest(
    val comment: String,
    val documentText: String,
    val range: TextRange,
    val projectRoot: String,
    /**
     * The edited file's language and name, so a provider proposes code in the language of the
     * file instead of guessing from the surrounding context. Both are empty only where no file is
     * available; a provider then falls back to the nearby context exactly as before.
     */
    val language: String = "",
    val fileName: String = "",
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
    UNSAFE_CAPABILITIES(false, "La CLI instalada no ofrece todas las capacidades seguras requeridas."),
    LOGIN_REQUIRED(false, "Iniciá sesión en la CLI local antes de generar."),
    AUTH_STATUS_MALFORMED(false, "La CLI devolvió un estado de sesión no verificable."),
    AUTH_STATUS_FAILED(false, "No pude comprobar el estado de sesión de la CLI."),
    PROCESS_TIMEOUT(false, "La CLI tardó demasiado en responder."),
    PROCESS_OUTPUT_TOO_LARGE(false, "La CLI devolvió demasiada salida."),
    PROCESS_FAILED(false, "La CLI local no pudo completar la operación."),
    QUOTA_EXHAUSTED(false, "La cuota de la CLI local está agotada."),
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

    /**
     * Whether this provider's CLI exists on this machine. It only looks at the filesystem: no
     * process is started, so the router can ask both providers before every request.
     */
    fun isInstalled(): Boolean = true

    fun generate(request: GenerationRequest): GenerationResult

    fun cancel()

    fun isGenerating(): Boolean

    fun resetConversation()
}
