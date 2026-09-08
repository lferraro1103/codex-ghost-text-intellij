package com.leandro.codexghosttext.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.leandro.codexghosttext.diagnostics.GenerationDiagnosticDump
import com.leandro.codexghosttext.generation.ProviderDiagnostic
import com.leandro.codexghosttext.generation.ProviderId
import com.leandro.codexghosttext.provider.ProviderRouterService
import com.leandro.codexghosttext.provider.SelectedProviderAvailability

class CheckCodexConnectionAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val availability = SelectedProviderActions(project.getService(ProviderRouterService::class.java))
                .checkSelectedAvailability()
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                val message = ProviderActionFeedback.messageFor(availability)
                val dump = if (!availability.diagnostic.isReady) {
                    GenerationDiagnosticDump.write(project, availability.providerId, message, source = "connection-check")
                } else {
                    null
                }
                val messageWithDump = buildString {
                    append(message)
                    dump?.let { append("\nDiagnóstico guardado en: $it") }
                }
                NotificationGroupManager.getInstance().getNotificationGroup(GenerateCodexGhostTextAction.NOTIFICATION_GROUP_ID)
                    .createNotification(
                        messageWithDump,
                        ProviderActionFeedback.notificationTypeFor(availability),
                    )
                    .notify(project)
            }
        }
    }

    companion object { const val ACTION_ID = "com.leandro.codexghosttext.CheckCodexConnection" }
}

/** Thin selected-provider action facade so connection and reset share the exact router boundary. */
internal class SelectedProviderActions(private val router: ProviderRouterService) {
    fun checkSelectedAvailability(): SelectedProviderAvailability = router.checkSelectedAvailability()

    fun resetSelectedConversation(): ProviderId = router.resetSelectedConversation()
}

/** Non-secret, provider-specific messages. This layer never reads credentials or starts login. */
internal object ProviderActionFeedback {
    fun messageFor(availability: SelectedProviderAvailability): String = when (availability.providerId) {
        ProviderId.CODEX -> codexMessage(availability.diagnostic)
        ProviderId.CLAUDE -> claudeMessage(availability.diagnostic)
    }

    fun notificationTypeFor(availability: SelectedProviderAvailability): NotificationType =
        if (availability.diagnostic == ProviderDiagnostic.READY) NotificationType.INFORMATION else NotificationType.WARNING

    fun resetMessage(providerId: ProviderId): String =
        "La conversación de ${displayName(providerId)} para este proyecto se reinició. La próxima generación abrirá un chat nuevo."

    private fun codexMessage(diagnostic: ProviderDiagnostic): String = when (diagnostic) {
        ProviderDiagnostic.READY -> "Codex local está disponible con tu cuenta de ChatGPT."
        ProviderDiagnostic.MISSING_EXECUTABLE -> "No encontré Codex local. Instalalo o agregalo al PATH y volvé a intentar."
        ProviderDiagnostic.LOGIN_REQUIRED -> "Codex requiere inicio de sesión. Ejecutá `codex login` fuera del IDE y volvé a intentar."
        ProviderDiagnostic.QUOTA_EXHAUSTED -> "La cuota de Codex está agotada. Esperá al próximo reinicio de cuota y volvé a intentar."
        else -> "No pude comprobar Codex local. Verificá que Codex esté actualizado y volvé a intentar."
    }

    private fun claudeMessage(diagnostic: ProviderDiagnostic): String = when (diagnostic) {
        ProviderDiagnostic.READY -> "Claude local está disponible con tu cuenta autenticada."
        ProviderDiagnostic.MISSING_EXECUTABLE -> "No encontré Claude local. Instalá Claude Code o agregalo al PATH y volvé a intentar."
        ProviderDiagnostic.VERSION_UNSUPPORTED -> "La versión de Claude no es compatible. Actualizá Claude y volvé a intentar."
        ProviderDiagnostic.VERSION_UNPARSEABLE,
        ProviderDiagnostic.VERSION_COMMAND_FAILED -> "No pude comprobar la versión de Claude. Actualizá Claude y volvé a intentar."
        ProviderDiagnostic.HELP_COMMAND_FAILED,
        ProviderDiagnostic.UNSAFE_CAPABILITIES -> "Claude no admite el modo seguro sin herramientas requerido por el plugin."
        ProviderDiagnostic.LOGIN_REQUIRED -> "Claude requiere inicio de sesión. Ejecutá `claude login` fuera del IDE y volvé a intentar."
        ProviderDiagnostic.AUTH_STATUS_MALFORMED,
        ProviderDiagnostic.AUTH_STATUS_FAILED -> "No pude comprobar la sesión local de Claude. Ejecutá `claude login` y volvé a intentar."
        ProviderDiagnostic.QUOTA_EXHAUSTED -> "La cuota de Claude está agotada. Esperá al próximo reinicio de cuota y volvé a intentar."
        else -> "No pude comprobar Claude local. Verificá su instalación y volvé a intentar."
    }

    private fun displayName(providerId: ProviderId): String = when (providerId) {
        ProviderId.CODEX -> "Codex"
        ProviderId.CLAUDE -> "Claude"
    }
}
