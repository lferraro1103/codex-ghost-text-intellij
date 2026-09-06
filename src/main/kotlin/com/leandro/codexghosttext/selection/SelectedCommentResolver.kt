package com.leandro.codexghosttext.selection

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil

/** Validates a bounded editor selection without retaining editor or PSI state. */
object SelectedCommentResolver {
    fun from(event: AnActionEvent): SelectedComment? {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
        val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return null
        return resolve(editor, psiFile)
    }

    fun resolve(editor: Editor, psiFile: PsiFile): SelectedComment? {
        return resolve(editor.document, psiFile, editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd)
    }

    internal fun resolve(document: Document, psiFile: PsiFile, start: Int, end: Int): SelectedComment? {
        if (!psiFile.isValid) return null
        val manager = PsiDocumentManager.getInstance(psiFile.project)
        // Never resolve current offsets against an old or unrelated PSI snapshot.
        // In particular, do not force a commit from the BGT presentation path.
        if (manager.getCachedDocument(psiFile) !== document || !manager.isCommitted(document)) return null
        val text = document.charsSequence
        if (start < 0 || end < start || end > text.length || start == end) return null

        val first = firstNonWhitespace(text, start, end) ?: return null
        val last = lastNonWhitespace(text, start, end) ?: return null
        val firstComment = commentAt(psiFile, first) ?: return null
        val lastComment = commentAt(psiFile, last) ?: return null
        if (firstComment != lastComment) return null

        val commentRange = firstComment.textRange
        if (start > commentRange.startOffset || end < commentRange.endOffset) return null
        if (!isWhitespaceOnly(text, start, commentRange.startOffset)) return null
        if (!isWhitespaceOnly(text, commentRange.endOffset, end)) return null

        return SelectedComment(commentRange)
    }

    private fun commentAt(psiFile: PsiFile, offset: Int): PsiComment? {
        val leaf = psiFile.findElementAt(offset) ?: return null
        return PsiTreeUtil.getParentOfType(leaf, PsiComment::class.java, false)
    }

    private fun firstNonWhitespace(text: CharSequence, start: Int, end: Int): Int? {
        for (index in start until end) {
            if (!text[index].isWhitespace()) return index
        }
        return null
    }

    private fun lastNonWhitespace(text: CharSequence, start: Int, end: Int): Int? {
        for (index in end - 1 downTo start) {
            if (!text[index].isWhitespace()) return index
        }
        return null
    }

    private fun isWhitespaceOnly(text: CharSequence, start: Int, end: Int): Boolean {
        for (index in start until end) {
            if (!text[index].isWhitespace()) return false
        }
        return true
    }
}

data class SelectedComment(val range: TextRange)
