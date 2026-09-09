package com.leandro.codexghosttext.generation

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager

/**
 * The once-per-conversation brief: the project's own layout, plus the instruction to look project
 * symbols up instead of inventing them.
 *
 * Both CLIs already run with the project root as their working directory and can read project
 * files — Claude through `Read`, `Glob`, and `Grep`, Codex through its read-only sandbox — but
 * nothing used to tell them that, so they answered from the 2,000/4,000 character window alone and
 * invented constructors and method names for project classes.
 *
 * The text is deliberately stable for a given project: Codex receives it once as the thread's
 * developer instructions and Claude as a constant appended system prompt, so a per-request change
 * would invalidate the prompt cache of a conversation that is reused across requests.
 */
@Service(Service.Level.PROJECT)
class ProjectContextService(private val project: Project) {
    @Volatile
    private var cached: String? = null

    fun brief(): String = cached ?: build().also { cached = it }

    /** Dropped together with a provider conversation, so a reset also re-derives the layout. */
    fun invalidate() {
        cached = null
    }

    private fun build(): String = buildString {
        appendLine(LOOKUP_INSTRUCTIONS)
        val roots = contentRoots()
        if (roots.isNotEmpty()) {
            appendLine()
            appendLine("Carpetas del proyecto:")
            roots.forEach { appendLine("- $it") }
        }
    }.trimEnd()

    /**
     * Only the module layout, from paths the IDE already knows. No file contents are read, so this
     * needs no read action and no language plugin.
     */
    private fun contentRoots(): List<String> = runCatching {
        ProjectRootManager.getInstance(project).contentRoots
            .asSequence()
            .map { it.path }
            .distinct()
            .sorted()
            .take(MAX_ROOTS)
            .toList()
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_ROOTS = 12

        val LOOKUP_INSTRUCTIONS = """
            Antes de usar un tipo, una función o un archivo del proyecto, verificá su firma real con
            tus herramientas de lectura (Grep/Glob/Read o tu acceso de sólo lectura a la carpeta de
            trabajo): confirmá el nombre del paquete, el constructor y los nombres de los métodos.
            No inventes APIs y no supongas que existen. Limitá esa verificación a dos o tres
            búsquedas por consulta para no demorar la respuesta.
        """.trimIndent()
    }
}
