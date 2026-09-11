package com.leandro.codexghosttext.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.leandro.codexghosttext.context.ConversationContextMemory
import com.leandro.codexghosttext.generation.ProjectContextService
import com.leandro.codexghosttext.provider.ProviderRouterService

class ResetCodexProjectConversationAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        // A reset also re-derives the project brief, so a moved or renamed module is picked up.
        project.getService(ProjectContextService::class.java).invalidate()
        val providerId = SelectedProviderActions(project.getService(ProviderRouterService::class.java))
            .resetSelectedConversation()
        // The new conversation knows nothing, so every declaration is offered again.
        project.getService(ConversationContextMemory::class.java).forget(providerId)
        NotificationGroupManager.getInstance().getNotificationGroup(GenerateCodexGhostTextAction.NOTIFICATION_GROUP_ID)
            .createNotification(
                ProviderActionFeedback.resetMessage(providerId),
                NotificationType.INFORMATION,
            )
            .notify(project)
    }
}
