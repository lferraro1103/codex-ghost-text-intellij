package com.leandro.codexghosttext.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.leandro.codexghosttext.selection.SelectedCommentResolver
import com.leandro.codexghosttext.preview.GhostPreviewService

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
        val selectedComment = SelectedCommentResolver.from(event)
        val editor = event.getData(CommonDataKeys.EDITOR)
        val project = event.project
        if (selectedComment != null && editor != null && project != null) {
            project.getService(GhostPreviewService::class.java).showFixture(editor, selectedComment)
            return
        }

        project ?: return
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
