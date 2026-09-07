package com.leandro.codexghosttext.status

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import java.util.concurrent.atomic.AtomicLong

/** Shows one non-modal, animated status-bar indication for the newest generation request. */
@Service(Service.Level.PROJECT)
class CodexGenerationStatusService(private val project: Project) : Disposable {
    private val activeRequest = AtomicLong(0)

    fun show(): Long {
        val request = activeRequest.incrementAndGet()
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed && activeRequest.get() == request) {
                WindowManager.getInstance().getStatusBar(project)
                    .startRefreshIndication("Codex: generando…")
            }
        }
        return request
    }

    fun hide(request: Long) {
        if (!activeRequest.compareAndSet(request, 0)) return
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed && activeRequest.get() == 0L) {
                WindowManager.getInstance().getStatusBar(project).stopRefreshIndication()
            }
        }
    }

    override fun dispose() {
        activeRequest.set(0)
        if (!project.isDisposed) WindowManager.getInstance().getStatusBar(project).stopRefreshIndication()
    }
}
