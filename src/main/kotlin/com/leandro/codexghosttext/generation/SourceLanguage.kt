package com.leandro.codexghosttext.generation

/**
 * The language instruction both providers add to their prompt, and the check that rejects a
 * proposal whose Markdown fence announces a different language than the edited file.
 *
 * The nearby context alone is not a reliable hint: Claude answered a Kotlin file with JavaScript.
 */
object SourceLanguage {
    /**
     * The resolved project context of one request. Paths are cheap and repeated freely; the
     * declarations are only the ones this conversation has not been given yet.
     */
    fun projectSection(request: GenerationRequest): String = buildString {
        if (request.dependencyPaths.isNotEmpty()) {
            appendLine()
            appendLine("Archivos del proyecto de los que depende este código:")
            request.dependencyPaths.forEach { appendLine("- $it") }
        }
        if (request.dependencySkeletons.isNotEmpty()) {
            appendLine()
            appendLine("Declaraciones de esos archivos (sólo firmas, sin cuerpos):")
            request.dependencySkeletons.forEach {
                appendLine(it)
                appendLine()
            }
        }
    }.trimEnd()

    fun instruction(request: GenerationRequest): String = when {
        request.language.isBlank() -> ""
        request.fileName.isBlank() -> "El código debe ser ${request.language} válido."
        else -> "El archivo es ${request.language} (${request.fileName}). El código debe ser ${request.language} válido."
    }

    /**
     * True only when the fence tag is a language this plugin recognises and it is not the edited
     * file's language. An unknown tag is never treated as a conflict, so a new or uncommon
     * language cannot make a valid proposal unusable.
     */
    fun fenceConflicts(tag: String, language: String, fileName: String): Boolean {
        val fence = normalize(tag) ?: return false
        val expected = expected(language, fileName)
        return expected.isNotEmpty() && fence !in expected
    }

    private fun expected(language: String, fileName: String): Set<String> = buildSet {
        normalize(language)?.let(::add)
        normalize(fileName.substringAfterLast('.', ""))?.let(::add)
    }

    private fun normalize(value: String): String? = value.trim().lowercase().takeIf(String::isNotEmpty)?.let(ALIASES::get)

    /** Canonical name per accepted spelling: fence tag, IntelliJ language name, and extension. */
    private val ALIASES: Map<String, String> = buildMap {
        fun family(canonical: String, vararg spellings: String) = spellings.forEach { put(it, canonical) }
        family("kotlin", "kotlin", "kt", "kts")
        family("java", "java")
        family("javascript", "javascript", "js", "jsx", "mjs", "cjs", "ecmascript")
        family("typescript", "typescript", "ts", "tsx")
        family("python", "python", "py", "python3")
        family("go", "go", "golang")
        family("rust", "rust", "rs")
        family("ruby", "ruby", "rb")
        family("php", "php")
        family("csharp", "csharp", "c#", "cs")
        family("cpp", "cpp", "c++", "cc", "cxx", "hpp")
        family("c", "c", "h")
        family("scala", "scala", "sc")
        family("groovy", "groovy", "gradle")
        family("swift", "swift")
        family("dart", "dart")
        family("sql", "sql", "mysql", "postgresql")
        family("shell", "shell", "sh", "bash", "zsh")
        family("html", "html", "htm")
        family("css", "css", "scss", "sass", "less")
        family("xml", "xml")
        family("json", "json")
        family("yaml", "yaml", "yml")
    }
}
