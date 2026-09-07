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
            project.getService(CodexGenerationService::class.java).cancel()
            val snapshot = editor.document.text
            val snapshotStamp = editor.document.modificationStamp
            val selectionStart = editor.selectionModel.selectionStart
            val selectionEnd = editor.selectionModel.selectionEnd
            val loadingNotification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification("Generando propuesta con Codex…", NotificationType.INFORMATION)
            loadingNotification.notify(project)
            ApplicationManager.getApplication().executeOnPooledThread {
                val generation = project.getService(CodexGenerationService::class.java)
                var waits = 0
                while (generation.isGenerating() && waits++ < 50) {
                    Thread.sleep(20)
                }
                val result = generation
                    .generate(snapshot.substring(selectedComment.range.startOffset, selectedComment.range.endOffset), snapshot, selectedComment.range)
                ApplicationManager.getApplication().invokeLater {
                    loadingNotification.expire()
                    if (project.isDisposed || editor.isDisposed || editor.document.modificationStamp != snapshotStamp ||
                        editor.selectionModel.selectionStart != selectionStart || editor.selectionModel.selectionEnd != selectionEnd) return@invokeLater
                    when (result) {
                        is GenerationResult.Success -> {
                            val shown = project.getService(GhostPreviewService::class.java).show(editor, selectedComment.range, result.code)
                            if (!shown) NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
                                .createNotification("No puedo mostrar la propuesta en este editor o selección.", NotificationType.WARNING).notify(project)
                        }
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
