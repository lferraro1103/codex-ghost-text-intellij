package com.leandro.codexghosttext.context

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil

/**
 * The project files the code around the selected comment depends on, and the declarations those
 * files expose.
 *
 * A model that cannot see the rest of the project invents constructors and method names. Both CLIs
 * can read the project themselves, but they do not know where to look: naming the files turns an
 * open-ended search into a direct read, and the declarations remove the read altogether.
 *
 * Everything here needs a read action and a committed PSI tree, and is budgeted: a ghost-text
 * request degrades to fewer files, or to none, rather than making the user wait.
 */
object ProjectDependencyCollector {
    /** Project files as paths relative to the project root, and their declaration-only renderings. */
    data class Dependencies(val paths: List<String> = emptyList(), val skeletons: List<String> = emptyList()) {
        val isEmpty: Boolean get() = paths.isEmpty() && skeletons.isEmpty()
    }

    const val MAX_FILES = 8
    const val MAX_SKELETON_LINES = 40
    private const val MAX_SKELETON_CHARS = 4_000
    private const val MAX_SKELETON_DEPTH = 3
    private const val SEARCH_BUDGET_MILLIS = 150L

    /**
     * Must run inside a read action in smart mode, with [psiFile] committed against the document
     * the caller snapshotted.
     */
    fun collect(project: Project, psiFile: PsiFile, range: TextRange, withSkeletons: Boolean): Dependencies {
        val deadline = System.nanoTime() + SEARCH_BUDGET_MILLIS * 1_000_000
        val ownFile = psiFile.virtualFile
        val index = ProjectFileIndex.getInstance(project)
        val files = LinkedHashSet<VirtualFile>()

        for (target in resolvedFiles(psiFile, range)) {
            if (System.nanoTime() > deadline || files.size >= MAX_FILES) break
            ProgressManager.checkCanceled()
            // Only this project's own sources: the model already knows the JDK and third-party
            // libraries, and their sources are not the user's to disclose.
            if (target == ownFile || !index.isInSourceContent(target)) continue
            files += target
        }

        val paths = files.mapNotNull { relativePath(project, it) }
        if (!withSkeletons) return Dependencies(paths)
        return Dependencies(paths, files.mapNotNull { skeleton(project, it) })
    }

    private fun resolvedFiles(psiFile: PsiFile, range: TextRange): Sequence<VirtualFile> {
        val anchor = psiFile.findElementAt(range.startOffset) ?: return emptySequence()
        return PsiTreeUtil.findChildrenOfType(enclosingScope(anchor), PsiElement::class.java)
            .asSequence()
            .flatMap { element -> runCatching { element.references.toList() }.getOrDefault(emptyList()).asSequence() }
            .mapNotNull { reference -> runCatching { reference.resolve() }.getOrNull() }
            .mapNotNull { resolved -> runCatching { resolved.containingFile?.virtualFile }.getOrNull() }
    }

    /**
     * The top-level declaration containing the comment, or the file when there is none. Resolving
     * every reference in the file would be slower and much less relevant.
     */
    private fun enclosingScope(anchor: PsiElement): PsiElement {
        var current = anchor
        var depth = 0
        while (depth++ < MAX_SCOPE_DEPTH) {
            val parent = current.parent ?: return current
            if (parent is PsiFile) return current
            current = parent
        }
        return current
    }

    private fun relativePath(project: Project, file: VirtualFile): String? {
        val base = project.basePath ?: return file.name
        val root = LocalFileSystem.getInstance().findFileByPath(base) ?: return file.name
        return VfsUtilCore.getRelativePath(file, root) ?: file.name
    }

    /**
     * A declaration-only rendering built from the structure view, which every language registering
     * `psiStructureViewFactory` provides. Bodies, comments, and literals never appear.
     */
    private fun skeleton(project: Project, file: VirtualFile): String? {
        val psi = PsiManager.getInstance(project).findFile(file) ?: return null
        val builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(psi)
        if (builder !is TreeBasedStructureViewBuilder) return null
        // Some language plugins assume a non-null editor even though the parameter is nullable.
        val model = runCatching { builder.createStructureViewModel(null) }.getOrNull() ?: return null
        return try {
            val lines = mutableListOf<String>()
            appendElement(model.root, 0, lines)
            if (lines.isEmpty()) {
                null
            } else {
                "// ${relativePath(project, file) ?: file.name}\n" + lines.joinToString("\n").take(MAX_SKELETON_CHARS)
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { Disposer.dispose(model) }
        }
    }

    private fun appendElement(element: StructureViewTreeElement, depth: Int, lines: MutableList<String>) {
        if (depth > MAX_SKELETON_DEPTH || lines.size >= MAX_SKELETON_LINES) return
        ProgressManager.checkCanceled()
        val text = runCatching { element.presentation.presentableText }.getOrNull()?.trim()
        if (depth > 0 && !text.isNullOrEmpty()) lines += "  ".repeat(depth - 1) + text
        runCatching { element.children }.getOrDefault(emptyArray())
            .filterIsInstance<StructureViewTreeElement>()
            .forEach { appendElement(it, depth + 1, lines) }
    }

    private const val MAX_SCOPE_DEPTH = 32
}
