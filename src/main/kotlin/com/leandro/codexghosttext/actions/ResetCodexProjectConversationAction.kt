package com.leandro.codexghosttext.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.leandro.codexghosttext.codex.CodexGenerationService

class ResetCodexProjectConversationAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        project.getService(CodexGenerationService::class.java).resetConversation()
        NotificationGroupManager.getInstance().getNotificationGroup(GenerateCodexGhostTextAction.NOTIFICATION_GROUP_ID)
            .createNotification(
                "La conversación de Codex para este proyecto se reinició. La próxima generación abrirá un chat nuevo.",
                NotificationType.INFORMATION,
            )
            .notify(project)
    }
}
