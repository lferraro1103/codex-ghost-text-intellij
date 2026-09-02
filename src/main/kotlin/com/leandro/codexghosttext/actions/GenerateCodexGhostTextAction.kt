package com.leandro.codexghosttext.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.leandro.codexghosttext.selection.SelectedCommentResolver

class GenerateCodexGhostTextAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val validSelection = SelectedCommentResolver.from(event) != null
        if (event.place == ActionPlaces.EDITOR_POPUP) {
            event.presentation.isVisible = validSelection
            event.presentation.isEnabled = validSelection
        } else {
            event.presentation.isVisible = true
            event.presentation.isEnabled = event.getData(CommonDataKeys.EDITOR) != null
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        if (SelectedCommentResolver.from(event) != null) return

        val project = event.project ?: return
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(INVALID_SELECTION_MESSAGE, NotificationType.INFORMATION)
            .notify(project)
    }

    companion object {
        const val ACTION_ID = "com.leandro.codexghosttext.GenerateCodexGhostText"
        const val NOTIFICATION_GROUP_ID = "Codex Ghost Text"
        const val INVALID_SELECTION_MESSAGE = "Seleccioná exactamente un comentario para generar código."
    }
}
