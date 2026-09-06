package com.leandro.codexghosttext.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.leandro.codexghosttext.codex.CodexAvailabilityService
import com.leandro.codexghosttext.codex.CodexDiagnostic

class CheckCodexConnectionAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val diagnostic = project.getService(CodexAvailabilityService::class.java).check()
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                NotificationGroupManager.getInstance().getNotificationGroup(GenerateCodexGhostTextAction.NOTIFICATION_GROUP_ID)
                    .createNotification(messageFor(diagnostic), notificationTypeFor(diagnostic)).notify(project)
            }
        }
    }

    private fun messageFor(diagnostic: CodexDiagnostic): String = when (diagnostic) {
        CodexDiagnostic.CHATGPT_READY -> "Codex local está disponible con tu cuenta de ChatGPT."
        CodexDiagnostic.MISSING_EXECUTABLE -> "No encontré Codex local. Instalalo o agregalo al PATH y volvé a intentar."
        CodexDiagnostic.LOGIN_REQUIRED -> "Codex requiere inicio de sesión. Ejecutá `codex login` fuera del IDE y volvé a intentar."
        CodexDiagnostic.UNSUPPORTED_AUTH -> "Codex no está usando una cuenta de ChatGPT compatible. Iniciá sesión con `codex login`."
        CodexDiagnostic.QUOTA_EXHAUSTED -> "La cuota de Codex está agotada. Esperá al próximo reinicio de cuota y volvé a intentar."
        CodexDiagnostic.CONNECTION_FAILED -> "No pude comprobar Codex local. Verificá que Codex esté actualizado y volvé a intentar."
    }

    private fun notificationTypeFor(diagnostic: CodexDiagnostic) =
        if (diagnostic == CodexDiagnostic.CHATGPT_READY) NotificationType.INFORMATION else NotificationType.WARNING

    companion object { const val ACTION_ID = "com.leandro.codexghosttext.CheckCodexConnection" }
}
