package com.leandro.codexghosttext.editor

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.leandro.codexghosttext.preview.GhostPreviewService

/** Application-scoped: action handlers are global, proposal state remains project scoped. */
@Service(Service.Level.APP)
class GhostKeyHandlerInstaller : Disposable {
    private val manager = EditorActionManager.getInstance()
    private val tabOriginal = manager.getActionHandler(IdeActions.ACTION_EDITOR_TAB)
    private val escapeOriginal = manager.getActionHandler(IdeActions.ACTION_EDITOR_ESCAPE)
    private val tabWrapper = DelegatingHandler(tabOriginal) { editor -> consumeTab(editor) }
    private val escapeWrapper = DelegatingHandler(escapeOriginal) { editor -> consumeEscape(editor) }

    init {
        manager.setActionHandler(IdeActions.ACTION_EDITOR_TAB, tabWrapper)
        manager.setActionHandler(IdeActions.ACTION_EDITOR_ESCAPE, escapeWrapper)
    }

    override fun dispose() {
        if (manager.getActionHandler(IdeActions.ACTION_EDITOR_TAB) === tabWrapper) {
            manager.setActionHandler(IdeActions.ACTION_EDITOR_TAB, tabOriginal)
        }
        if (manager.getActionHandler(IdeActions.ACTION_EDITOR_ESCAPE) === escapeWrapper) {
            manager.setActionHandler(IdeActions.ACTION_EDITOR_ESCAPE, escapeOriginal)
        }
    }

    private fun consumeTab(editor: Editor): Boolean {
        if (hasNativeEditorInteraction(editor)) return false
        val project = editor.project ?: return false
        return project.getService(GhostPreviewService::class.java).acceptIfFresh(editor)
    }

    private fun consumeEscape(editor: Editor): Boolean {
        if (hasNativeEditorInteraction(editor)) return false
        val project = editor.project ?: return false
        val preview = project.getService(GhostPreviewService::class.java)
        return preview.owns(editor) && preview.cancel()
    }

    private fun hasNativeEditorInteraction(editor: Editor): Boolean {
        val project = editor.project ?: return false
        return LookupManager.getActiveLookup(editor) != null || TemplateManager.getInstance(project).getActiveTemplate(editor) != null
    }

    private class DelegatingHandler(
        private val original: EditorActionHandler,
        private val consume: (Editor) -> Boolean,
    ) : EditorActionHandler() {
        override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
            if (caret != null && consume(editor)) return
            original.execute(editor, caret, dataContext)
        }
    }
}
