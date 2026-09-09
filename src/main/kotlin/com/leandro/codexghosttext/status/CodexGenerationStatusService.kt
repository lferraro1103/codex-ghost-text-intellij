package com.leandro.codexghosttext.status

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.leandro.codexghosttext.generation.ProviderId
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Timer

/** A visible, temporary status-bar spinner for the latest local provider request. */
@Service(Service.Level.PROJECT)
class CodexGenerationStatusService(private val project: Project) : Disposable {
    private val requests = GenerationRequestTracker()
    private var timer: Timer? = null
    private var activeProvider: ProviderId? = null
    private var frame = 0

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
            activeProvider = providerId
            frame = 0
            timer?.stop()
            timer = Timer(FRAME_DELAY_MILLIS) {
                frame = (frame + 1) % GenerationStatusPresentation.frameCount
                statusBar.updateWidget(ProviderSelectorWidgetFactory.WIDGET_ID)
            }.also { it.start() }
            statusBar.updateWidget(ProviderSelectorWidgetFactory.WIDGET_ID)
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
        activeProvider = null
        frame = 0
        statusBar()?.updateWidget(ProviderSelectorWidgetFactory.WIDGET_ID)
    }

    internal fun currentText(): String? = activeProvider?.let { GenerationStatusPresentation.text(it, frame) }

    private fun statusBar() = runCatching { WindowManager.getInstance().getStatusBar(project) }.getOrNull()

    private companion object {
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
