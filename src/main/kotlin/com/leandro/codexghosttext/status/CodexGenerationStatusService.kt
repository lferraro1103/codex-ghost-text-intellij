package com.leandro.codexghosttext.status

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import java.awt.Component
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Timer

/** A visible, temporary status-bar spinner for the latest local Codex generation. */
@Service(Service.Level.PROJECT)
class CodexGenerationStatusService(private val project: Project) : Disposable {
    private val activeRequest = AtomicLong(0)
    private var widget: GenerationWidget? = null
    private var timer: Timer? = null

    fun show(): Long {
        val request = activeRequest.incrementAndGet()
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed || activeRequest.get() != request) return@invokeLater
            val statusBar = statusBar() ?: return@invokeLater
            val activeWidget = widget ?: GenerationWidget().also {
                widget = it
                statusBar.addWidget(it, this)
            }
            timer?.stop()
            timer = Timer(FRAME_DELAY_MILLIS) {
                activeWidget.advance()
                statusBar.updateWidget(activeWidget.ID())
            }.also { it.start() }
            statusBar.updateWidget(activeWidget.ID())
        }
        return request
    }

    fun hide(request: Long) {
        if (!activeRequest.compareAndSet(request, 0)) return
        ApplicationManager.getApplication().invokeLater { removeWidget() }
    }

    override fun dispose() {
        activeRequest.set(0)
        ApplicationManager.getApplication().invokeLater { removeWidget() }
    }

    private fun removeWidget() {
        timer?.stop()
        timer = null
        val activeWidget = widget ?: return
        statusBar()?.removeWidget(activeWidget.ID())
        widget = null
    }

    private fun statusBar() = runCatching { WindowManager.getInstance().getStatusBar(project) }.getOrNull()

    private class GenerationWidget : StatusBarWidget, StatusBarWidget.TextPresentation {
        private var frame = 0

        override fun ID(): String = WIDGET_ID
        override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
        override fun getText(): String = "${FRAMES[frame]} Codex: generando…"
        override fun getAlignment(): Float = Component.CENTER_ALIGNMENT
        override fun getTooltipText(): String = "Codex está generando una propuesta de código"

        fun advance() {
            frame = (frame + 1) % FRAMES.size
        }
    }

    private companion object {
        const val WIDGET_ID = "CodexGhostText.GenerationStatus"
        const val FRAME_DELAY_MILLIS = 150
        val FRAMES = arrayOf("◐", "◓", "◑", "◒")
    }
}
