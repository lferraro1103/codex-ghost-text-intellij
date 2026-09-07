package com.leandro.codexghosttext.editor

import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.ProjectManager
import com.leandro.codexghosttext.preview.GhostPreviewService
import java.awt.AWTEvent
import java.awt.event.KeyEvent

/** Captures proposal keys before IntelliJ keymaps and other completion plugins consume them. */
@Service(Service.Level.APP)
class GhostKeyHandlerInstaller : Disposable {
    private val dispatcher = IdeEventQueue.EventDispatcher(::dispatch)

    init {
        IdeEventQueue.getInstance().addPreprocessor(dispatcher, this)
    }

    private fun dispatch(event: AWTEvent): Boolean {
        if (event !is KeyEvent || event.id != KeyEvent.KEY_PRESSED || event.isConsumed) return false
        if (!isAcceptKey(event) && event.keyCode != KeyEvent.VK_ESCAPE) return false
        val dataContext = DataManager.getInstance().getDataContext(event.component)
        val preview = dataContext.getData(CommonDataKeys.PROJECT)
            ?.getService(GhostPreviewService::class.java)
            ?.takeIf { it.hasActivePreview() }
            ?: ProjectManager.getInstance().openProjects.asSequence()
                .filterNot { it.isDisposed }
                .map { it.getService(GhostPreviewService::class.java) }
                .firstOrNull { it.hasActivePreview() }
            ?: return false
        val consumed = when {
            isAcceptKey(event) -> {
                preview.acceptActiveIfFresh()
                // Do not let the key type into the editor even if a stale preview was rejected.
                true
            }
            event.keyCode == KeyEvent.VK_ESCAPE -> preview.cancel()
            else -> false
        }
        if (consumed) event.consume()
        return consumed
    }

    /** Supports the physical pipe key across common Spanish and US keyboard layouts. */
    private fun isAcceptKey(event: KeyEvent): Boolean =
        event.keyCode == KeyEvent.VK_BACK_SLASH || event.keyCode == KeyEvent.VK_BACK_QUOTE || event.keyChar == '|'

    override fun dispose() = Unit
}
