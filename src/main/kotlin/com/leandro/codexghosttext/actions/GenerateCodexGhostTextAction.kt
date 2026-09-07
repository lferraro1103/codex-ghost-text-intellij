package com.leandro.codexghosttext.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.application.ApplicationManager
import com.leandro.codexghosttext.selection.SelectedCommentResolver
import com.leandro.codexghosttext.preview.GhostPreviewService
import com.leandro.codexghosttext.codex.CodexGenerationService
import com.leandro.codexghosttext.codex.GenerationResult

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
            val snapshot = editor.document.text
            ApplicationManager.getApplication().executeOnPooledThread {
                val result = project.getService(CodexGenerationService::class.java)
                    .generate(snapshot.substring(selectedComment.range.startOffset, selectedComment.range.endOffset), snapshot, selectedComment.range)
                ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed || editor.isDisposed) return@invokeLater
                    when (result) {
                        is GenerationResult.Success -> project.getService(GhostPreviewService::class.java).show(editor, selectedComment.range, result.code)
                        is GenerationResult.Failure -> NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
                            .createNotification(result.message, NotificationType.WARNING).notify(project)
                    }
                }
            }
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
