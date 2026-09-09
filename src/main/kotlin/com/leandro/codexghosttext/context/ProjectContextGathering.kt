package com.leandro.codexghosttext.context

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.TimeUnit

/**
 * Runs [ProjectDependencyCollector] under the platform's threading rules and never lets it delay a
 * request: while the IDE is indexing, or if the read action is slow or fails, the caller gets no
 * project context and the request proceeds exactly as it did before.
 */
object ProjectContextGathering {
    private const val TIMEOUT_MILLIS = 400L

    fun gather(project: Project, psiFile: PsiFile?, range: TextRange): ProjectDependencyCollector.Dependencies {
        val empty = ProjectDependencyCollector.Dependencies()
        if (psiFile == null || project.isDisposed) return empty
        // Waiting for indexing would make the action feel broken on a cold start, and a proposal
        // without resolved context is still useful.
        if (DumbService.getInstance(project).isDumb) return empty
        return runCatching {
            ReadAction.nonBlocking<ProjectDependencyCollector.Dependencies> {
                if (!psiFile.isValid) empty else ProjectDependencyCollector.collect(project, psiFile, range, withSkeletons = true)
            }
                .inSmartMode(project)
                .expireWhen { project.isDisposed || !psiFile.isValid }
                .submit(AppExecutorUtil.getAppExecutorService())
                .get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        }.getOrDefault(empty)
    }
}
