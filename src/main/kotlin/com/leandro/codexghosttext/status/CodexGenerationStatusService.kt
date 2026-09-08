package com.leandro.codexghosttext.status

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import com.leandro.codexghosttext.generation.ProviderId
import java.awt.Component
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Timer

/** A visible, temporary status-bar spinner for the latest local provider request. */
@Service(Service.Level.PROJECT)
class CodexGenerationStatusService(private val project: Project) : Disposable {
    private val requests = GenerationRequestTracker()
    private var widget: GenerationWidget? = null
    private var timer: Timer? = null

    /** Compatibility overload until all callers pass their captured provider explicitly. */
    fun show(): Long = show(ProviderId.CODEX)

    /**
     * Displays the provider captured at request start. Later provider selection changes must not
     * relabel this transient status widget.
     */
    fun show(providerId: ProviderId): Long {
        val request = requests.begin()
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed || !requests.isActive(request)) return@invokeLater
            val statusBar = statusBar() ?: return@invokeLater
            val activeWidget = widget ?: GenerationWidget().also {
                widget = it
                statusBar.addWidget(it, this)
            }
            activeWidget.showProvider(providerId)
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
        if (!requests.finish(request)) return
        ApplicationManager.getApplication().invokeLater { removeWidget() }
    }

    override fun dispose() {
        requests.clear()
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
        private var providerId = ProviderId.CODEX

        override fun ID(): String = WIDGET_ID
        override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
        override fun getText(): String = GenerationStatusPresentation.text(providerId, frame)
        override fun getAlignment(): Float = Component.CENTER_ALIGNMENT
        override fun getTooltipText(): String = "${GenerationStatusPresentation.providerName(providerId)} está generando una propuesta de código"

        fun advance() {
            frame = (frame + 1) % GenerationStatusPresentation.frameCount
        }

        fun showProvider(providerId: ProviderId) {
            this.providerId = providerId
            frame = 0
        }
    }

    private companion object {
        const val WIDGET_ID = "CodexGhostText.GenerationStatus"
        const val FRAME_DELAY_MILLIS = 150
    }
}

/** Atomically keeps the transient widget associated with only its newest request token. */
internal class GenerationRequestTracker {
    private val activeRequest = AtomicLong(0)

    fun begin(): Long = activeRequest.incrementAndGet()

    fun activeRequest(): Long = activeRequest.get()

    fun isActive(request: Long): Boolean = activeRequest.get() == request

    fun finish(request: Long): Boolean = activeRequest.compareAndSet(request, 0)

    fun clear() {
        activeRequest.set(0)
    }
}

/** Pure presentation helpers let tests pin the provider captured at request start. */
internal object GenerationStatusPresentation {
    private val frames = arrayOf("◐", "◓", "◑", "◒")
    val frameCount: Int get() = frames.size

    fun text(providerId: ProviderId, frame: Int): String = "${frames[frame % frames.size]} ${providerName(providerId)}: generando…"

    fun providerName(providerId: ProviderId): String = when (providerId) {
        ProviderId.CODEX -> "Codex"
        ProviderId.CLAUDE -> "Claude"
    }
}
