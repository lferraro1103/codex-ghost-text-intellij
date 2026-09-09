package com.leandro.codexghosttext.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.leandro.codexghosttext.context.ConversationContextMemory
import com.leandro.codexghosttext.context.ProjectContextGathering
import com.leandro.codexghosttext.diagnostics.GenerationDiagnosticDump
import com.leandro.codexghosttext.generation.GenerationRequest
import com.leandro.codexghosttext.generation.GenerationResult
import com.leandro.codexghosttext.preview.GhostLoadingIndicator
import com.leandro.codexghosttext.preview.GhostPreviewService
import com.leandro.codexghosttext.provider.ProviderRouterService
import com.leandro.codexghosttext.provider.ProviderSelectionSnapshot
import com.leandro.codexghosttext.selection.SelectedCommentResolver
import com.leandro.codexghosttext.status.CodexGenerationStatusService
import java.io.File
import java.util.concurrent.atomic.AtomicLong

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
            val router = project.getService(ProviderRouterService::class.java)
            // The only automatic substitution: a selected CLI that is not installed while the
            // other one is. It is announced, never silent.
            val substituted = router.switchToInstalledProvider()
            val selectedProvider = router.snapshot()
            if (substituted != null) notify(project, ProviderActionFeedback.substitutionMessage(substituted), NotificationType.INFORMATION)
            val dispatch = project.getService(ActionGenerationRequestTracker::class.java)
                .capture(selectedProvider) { router.isCurrent(selectedProvider) }
            // A proposal is independent from a request. Remove an old inlay before starting one
            // selected provider request, but never write the document from this action.
            project.getService(GhostPreviewService::class.java).cancel()
            selectedProvider.provider.cancel()
            // A placeholder under the comment, so the request is visible where the user is looking
            // and not only in the status bar. It writes nothing and is removed on every outcome.
            project.getService(GhostLoadingIndicator::class.java).show(editor, selectedComment.range)
            val snapshot = editor.document.text
            val snapshotStamp = editor.document.modificationStamp
            val generationStatus = project.getService(CodexGenerationStatusService::class.java)
            val statusRequest = generationStatus.show(selectedProvider.providerId)
            val request = GenerationRequest(
                comment = snapshot.substring(selectedComment.range.startOffset, selectedComment.range.endOffset),
                documentText = snapshot,
                range = selectedComment.range,
                // This key is provider state metadata only. Claude's process/prompt must never see it.
                projectRoot = project.basePath?.let { runCatching { File(it).canonicalPath }.getOrDefault(it) } ?: project.locationHash,
                language = selectedComment.language,
                fileName = selectedComment.fileName,
            )
            val psiFile = event.getData(CommonDataKeys.PSI_FILE)
            ApplicationManager.getApplication().executeOnPooledThread {
                // Resolved project context, gathered off the EDT under a read action that yields to
                // the user's own edits. It is optional: an indexing IDE or a language without a
                // structure view simply produces less context, never a failed request.
                val dependencies = ProjectContextGathering.gather(project, psiFile, selectedComment.range)
                val memory = project.getService(ConversationContextMemory::class.java)
                val skeletons = memory.unsent(selectedProvider.providerId, dependencies.skeletons)
                val enriched = request.copy(
                    dependencyPaths = dependencies.paths,
                    dependencySkeletons = skeletons,
                )
                val result = runCatching { dispatch.generate(enriched) }
                    .getOrElse { GenerationResult.Failure("Falló la generación local.") }
                val failureDump = (result as? GenerationResult.Failure)?.let { failure ->
                    GenerationDiagnosticDump.write(
                        project,
                        selectedProvider.providerId,
                        failure.message,
                        source = "generation",
                    )
                }
                // Only a proposal that came back proves the conversation received the
                // declarations; otherwise they are offered again on the next request.
                if (result is GenerationResult.Success) memory.remember(selectedProvider.providerId, skeletons)
                ApplicationManager.getApplication().invokeLater {
                    generationStatus.hide(statusRequest)
                    if (!project.isDisposed) project.getService(GhostLoadingIndicator::class.java).hide()
                    // The selected comment is only input to the request. The user can navigate
                    // elsewhere while a provider works; an edit or provider switch invalidates it.
                    if (!dispatch.isCurrent() || project.isDisposed || editor.isDisposed ||
                        editor.document.modificationStamp != snapshotStamp
                    ) return@invokeLater
                    when (result) {
                        is GenerationResult.Success -> {
                            val shown = project.getService(GhostPreviewService::class.java).show(editor, selectedComment.range, result.code)
                            if (!shown) notify(project, "No puedo mostrar la propuesta en este editor o selección.", NotificationType.WARNING)
                        }
                        is GenerationResult.Failure -> {
                            val message = buildString {
                                append(result.message)
                                failureDump?.let { append("\nDiagnóstico guardado en: $it") }
                            }
                            notify(project, message, NotificationType.WARNING)
                        }
                    }
                }
            }
            return
        }

        project ?: return
        notify(project, INVALID_SELECTION_MESSAGE, NotificationType.INFORMATION)
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(message, type)
            .notify(project)
    }

    companion object {
        const val ACTION_ID = "com.leandro.codexghosttext.GenerateCodexGhostText"
        const val NOTIFICATION_GROUP_ID = "Codex Ghost Text"
        const val INVALID_SELECTION_MESSAGE = "Seleccioná exactamente un comentario para generar código."
    }
}

/**
 * Project-local latest-request guard. Provider epochs prevent cross-provider stale delivery;
 * this token also prevents an older request of the *same* provider from publishing afterwards.
 */
@Service(Service.Level.PROJECT)
internal class ActionGenerationRequestTracker {
    private val latestToken = AtomicLong(0)

    fun capture(
        snapshot: ProviderSelectionSnapshot,
        providerIsCurrent: () -> Boolean,
    ): CapturedProviderGeneration = CapturedProviderGeneration(
        snapshot = snapshot,
        token = latestToken.incrementAndGet(),
        tokenIsCurrent = { token -> latestToken.get() == token },
        providerIsCurrent = providerIsCurrent,
    )
}

/** One explicit provider invocation; it has no knowledge of any alternative provider. */
internal class CapturedProviderGeneration(
    private val snapshot: ProviderSelectionSnapshot,
    private val token: Long,
    private val tokenIsCurrent: (Long) -> Boolean,
    private val providerIsCurrent: () -> Boolean,
) {
    fun generate(request: GenerationRequest): GenerationResult {
        if (!isCurrent()) return GenerationResult.Failure(CANCELLED_MESSAGE)
        var waits = 0
        while (snapshot.provider.isGenerating() && waits++ < WAIT_ATTEMPTS) Thread.sleep(WAIT_MILLIS)
        return if (isCurrent()) snapshot.provider.generate(request) else GenerationResult.Failure(CANCELLED_MESSAGE)
    }

    fun isCurrent(): Boolean = tokenIsCurrent(token) && providerIsCurrent()

    private companion object {
        const val WAIT_ATTEMPTS = 50
        const val WAIT_MILLIS = 20L
        const val CANCELLED_MESSAGE = "La generación fue cancelada."
    }
}
