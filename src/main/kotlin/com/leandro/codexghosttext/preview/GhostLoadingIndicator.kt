package com.leandro.codexghosttext.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import javax.swing.Timer

/**
 * The placeholder shown under the selected comment while a provider generates, so a request is
 * visible in the editor instead of only in the status bar.
 *
 * It is an inlay like the proposal preview: the document is never written, and it disappears on
 * the first edit, on a provider switch, and when the request finishes either way.
 */
@Service(Service.Level.PROJECT)
class GhostLoadingIndicator(private val project: Project) : Disposable {
    private var active: Loading? = null

    fun show(editor: Editor, comment: TextRange): Boolean {
        hide()
        if (project.isDisposed || editor.isDisposed || comment.endOffset !in 0..editor.document.textLength) return false
        val document = editor.document
        val line = document.getLineNumber(comment.endOffset)
        val renderer = GhostSkeletonRenderer(editor, leadingIndentColumns(document, line))
        val disposable = Disposer.newDisposable("Codex Ghost Text loading")
        val inlay = editor.inlayModel.addBlockElement(document.getLineEndOffset(line), false, false, 0, renderer)
            ?: run { Disposer.dispose(disposable); return false }
        Disposer.register(disposable, inlay)

        val timer = Timer(FRAME_MILLIS) {
            renderer.phase += PHASE_STEP
            if (inlay.isValid) inlay.repaint() else hide()
        }
        timer.isRepeats = true
        timer.start()
        Disposer.register(disposable) { timer.stop() }

        val loading = Loading(editor, disposable)
        document.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) = Unit.also { hide() }
            },
            disposable,
        )
        EditorFactory.getInstance().addEditorFactoryListener(
            object : EditorFactoryListener {
                override fun editorReleased(event: EditorFactoryEvent) {
                    if (event.editor === editor) hide()
                }
            },
            disposable,
        )
        active = loading
        return true
    }

    fun hide(): Boolean {
        val current = active ?: return false
        active = null
        Disposer.dispose(current.disposable)
        return true
    }

    fun isVisible(): Boolean = active != null

    override fun dispose() {
        hide()
    }

    private fun leadingIndentColumns(document: Document, line: Int): Int {
        val start = document.getLineStartOffset(line)
        val end = document.getLineEndOffset(line)
        return document.charsSequence.subSequence(start, end)
            .takeWhile { character -> character == ' ' || character == '\t' }
            .length
    }

    private class Loading(val editor: Editor, val disposable: Disposable)

    private companion object {
        const val FRAME_MILLIS = 60
        const val PHASE_STEP = 0.02f
    }
}
