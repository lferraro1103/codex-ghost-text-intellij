package com.leandro.codexghosttext.editor

import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.Service
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
        if (event.keyCode != KeyEvent.VK_TAB && event.keyCode != KeyEvent.VK_ESCAPE) return false
        val editor = DataManager.getInstance().getDataContext(event.component)
            .getData(CommonDataKeys.EDITOR) ?: return false
        val project = editor.project ?: return false
        val preview = project.getService(GhostPreviewService::class.java)
        val consumed = when (event.keyCode) {
            KeyEvent.VK_TAB -> preview.acceptIfFresh(editor)
            KeyEvent.VK_ESCAPE -> preview.owns(editor) && preview.cancel()
            else -> false
        }
        if (consumed) event.consume()
        return consumed
    }

    override fun dispose() = Unit
}
