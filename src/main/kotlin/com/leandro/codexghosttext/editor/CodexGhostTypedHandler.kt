package com.leandro.codexghosttext.editor

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.leandro.codexghosttext.preview.GhostPreviewService

/**
 * Runs inside IntelliJ's supported typing pipeline, before the character reaches
 * the document. This is essential: inserting `|` first makes a ghost proposal stale.
 */
class CodexGhostTypedHandler : TypedHandlerDelegate() {
    override fun beforeCharTyped(
        character: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
        fileType: FileType,
    ): Result {
        if (character != '|') return Result.CONTINUE
        val preview = project.getService(GhostPreviewService::class.java)
        if (!preview.owns(editor)) return Result.CONTINUE

        preview.acceptIfFresh(editor)
        return Result.STOP
    }
}
