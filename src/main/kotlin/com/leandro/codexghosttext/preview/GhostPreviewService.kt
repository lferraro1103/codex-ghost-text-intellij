package com.leandro.codexghosttext.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.editor.EditorFactory
import com.leandro.codexghosttext.selection.SelectedComment

@Service(Service.Level.PROJECT)
class GhostPreviewService(private val project: Project) : Disposable {
    private var preview: ProposalPreview? = null

    fun showFixture(editor: Editor, comment: SelectedComment): Boolean =
        show(editor, comment.range, DEVELOPMENT_FIXTURE)

    fun show(editor: Editor, comment: TextRange, proposal: String): Boolean {
        cancel()
        if (!canPreview(editor, comment, proposal)) return false
        val document = editor.document
        val line = document.getLineNumber(comment.endOffset)
        if (!safeLineTail(document, comment.endOffset, line)) return false
        val child = Disposer.newDisposable("Codex Ghost Text preview")
        val snapshot = ProposalPreview(
            editor = editor,
            document = document,
            commentEnd = comment.endOffset,
            commentLine = line,
            stamp = document.modificationStamp,
            proposal = proposal,
            disposable = child,
        )
        val inlay = editor.inlayModel.addBlockElement(
            document.getLineEndOffset(line), false, false, 0, GhostBlockRenderer(editor, proposal),
        ) ?: run { Disposer.dispose(child); return false }
        snapshot.inlay = inlay
        Disposer.register(child, inlay)
        installCancellation(snapshot)
        preview = snapshot
        return true
    }

    fun cancel(): Boolean {
        val old = preview ?: return false
        preview = null
        Disposer.dispose(old.disposable)
        return true
    }

    fun acceptIfFresh(editor: Editor): Boolean {
        val active = preview ?: return false
        if (!isFresh(active, editor)) { cancel(); return false }
        val insertion = insertion(active) ?: run { cancel(); return false }
        preview = null
        try {
            WriteCommandAction.writeCommandAction(project)
                .withName("Accept Codex Ghost Text")
                .run<RuntimeException> { active.document.insertString(insertion.first, insertion.second) }
        } finally {
            Disposer.dispose(active.disposable)
        }
        return true
    }

    fun acceptActiveIfFresh(): Boolean = preview?.let { acceptIfFresh(it.editor) } ?: false

    fun hasActivePreview(): Boolean = preview != null

    fun owns(editor: Editor): Boolean = preview?.editor === editor

    override fun dispose() { cancel() }

    private fun canPreview(editor: Editor, comment: TextRange, proposal: String): Boolean =
        !project.isDisposed && !editor.isDisposed && !editor.isViewer && editor.caretModel.caretCount == 1 &&
            editor.document.isWritable && comment.endOffset in 1..editor.document.textLength && validProposal(proposal)

    private fun validProposal(text: String): Boolean = text.isNotBlank() && text.length <= MAX_CHARACTERS && text.lines().size <= MAX_LINES

    private fun safeLineTail(document: Document, commentEnd: Int, line: Int): Boolean {
        val end = document.getLineEndOffset(line)
        return (commentEnd until end).all { document.charsSequence[it].isWhitespace() }
    }

    private fun isFresh(active: ProposalPreview, editor: Editor): Boolean =
        active.editor === editor && active.document === editor.document && !editor.isDisposed && !editor.isViewer &&
            editor.caretModel.caretCount == 1 && active.document.isWritable && active.stamp == active.document.modificationStamp &&
            validProposal(active.proposal) && safeLineTail(active.document, active.commentEnd, active.commentLine)

    private fun insertion(active: ProposalPreview): Pair<Int, String>? {
        val lineEnd = active.document.getLineEndOffset(active.commentLine)
        if (lineEnd < active.commentEnd) return null
        return lineEnd to "\n" + active.proposal.trimEnd()
    }

    private fun installCancellation(active: ProposalPreview) {
        active.document.addDocumentListener(object : DocumentListener { override fun documentChanged(event: DocumentEvent) { cancel() } }, active.disposable)
        EditorFactory.getInstance().addEditorFactoryListener(object : com.intellij.openapi.editor.event.EditorFactoryListener {
            override fun editorReleased(event: com.intellij.openapi.editor.event.EditorFactoryEvent) {
                if (event.editor === active.editor) cancel()
            }
        }, active.disposable)
    }

    private class ProposalPreview(
        val editor: Editor, val document: Document, val commentEnd: Int, val commentLine: Int,
        val stamp: Long, val proposal: String, val disposable: Disposable,
    ) { var inlay: com.intellij.openapi.editor.Inlay<*>? = null }

    companion object {
        const val MAX_LINES = 80
        const val MAX_CHARACTERS = 16_000
        const val DEVELOPMENT_FIXTURE = "// Codex local preview (development fixture)\nval generated = true"
    }
}
