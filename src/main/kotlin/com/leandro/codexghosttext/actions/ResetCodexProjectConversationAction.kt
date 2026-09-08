package com.leandro.codexghosttext.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.leandro.codexghosttext.provider.ProviderRouterService

class ResetCodexProjectConversationAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val providerId = SelectedProviderActions(project.getService(ProviderRouterService::class.java))
            .resetSelectedConversation()
        NotificationGroupManager.getInstance().getNotificationGroup(GenerateCodexGhostTextAction.NOTIFICATION_GROUP_ID)
            .createNotification(
                ProviderActionFeedback.resetMessage(providerId),
                NotificationType.INFORMATION,
            )
            .notify(project)
    }
}
