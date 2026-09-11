package com.leandro.codexghosttext.generation

/**
 * The single definition of "this answer is insertable code", shared by both providers.
 *
 * It used to be two regex sets that had drifted apart: Codex accepted almost any line while
 * Claude accepted only Kotlin and Java declarations, which rejected valid proposals. A model can
 * still answer with prose or Markdown, so the check stays, but only in one place.
 */
object CodeProposal {
    const val MAX_CHARS = 16_000
    const val MAX_LINES = 80

    /** Prose openings a model uses when it explains instead of answering with code. */
    private val PROSE_FIRST_LINE = Regex(
        "(?i)^(voy|claro|sure|here|i |the |this |aqui|aquí|ok|okay|perfecto|entiendo|generar|implement|para ).*$",
    )

    /** A declaration, annotation, comment, or statement in the languages an IDE edits. */
    private val CODE_FIRST_LINE = Regex(
        """(?:@\w+.*|#.*|//.*|/\*.*|<[A-Za-z/!].*|[}\])].*|""" +
            """(?:public|private|protected|internal|open|abstract|final|sealed|static|synchronized|""" +
            """suspend|inline|override|operator|companion|fun|func|def|lambda|class|interface|object|""" +
            """struct|enum|record|trait|impl|module|namespace|package|import|export|from|require|use|""" +
            """val|var|let|const|function|async|await|return|yield|throw|new|delete|if|for|while|do|""" +
            """switch|when|try|with|type|typedef|template|typename|auto|void|boolean|bool|byte|short|""" +
            """int|uint|long|float|double|char|str|String|Int|Long|Double|Boolean|Float|Char|Any|Unit)\b.*|""" +
            """[A-Za-z_$][\w$]*(?:[.<\[]|\s*\(|\s*[:=]|\s+[A-Za-z_$*&]).*)""",
    )
    private val COMMENT_OR_ANNOTATION = Regex("""^\s*(?://|/\*|#|@\w)""")
    private val FENCE_TAG = Regex("[A-Za-z0-9+#._-]{1,20}")
    private const val SYNTAX_CHARACTERS = "(){}[];=<>:,"

    /** The language tag of a wrapping fence, when the answer is exactly one fenced block. */
    fun fenceTag(answer: String): String? {
        val text = answer.trim()
        if (!text.startsWith("```") || !text.endsWith("```")) return null
        val opening = text.indexOf('\n').takeIf { it > 0 } ?: return null
        return text.substring(3, opening).trim().takeIf(FENCE_TAG::matches)
    }

    /**
     * Removes exactly one wrapping Markdown fence. A model wraps a code-only answer often enough
     * that rejecting it would waste a usable generation; any other fence still fails [isInsertable],
     * so no Markdown reaches the document.
     */
    fun withoutFence(answer: String): String {
        val text = answer.trim()
        if (!text.startsWith("```") || !text.endsWith("```")) return text
        val opening = text.indexOf('\n')
        if (opening < 0) return text
        if (!text.substring(3, opening).trim().let { it.isEmpty() || FENCE_TAG.matches(it) }) return text
        return text.substring(opening + 1, text.length - 3).trim()
    }

    /**
     * Drops any prose before the first line that reads like code. Codex opens with a sentence
     * often enough that discarding the whole answer would waste a usable generation; what remains
     * still has to pass [isInsertable].
     */
    fun trimToCodeStart(answer: String): String {
        val text = answer.trim()
        val start = CODE_START_PATTERNS.mapNotNull { it.find(text)?.range?.first }.minOrNull() ?: return ""
        return text.substring(start).trim()
    }

    private val CODE_START_PATTERNS = listOf(
        Regex("""(?m)^\s*(?=(?://|/\*|@\w|(?:public|private|protected|internal|fun|class|interface|object|data\s+class|sealed\s+class|enum\s+class|static|void|boolean|byte|short|int|long|float|double|char|String|val|var|const|let|function|def|async|import|package)\b))"""),
        Regex("""\b(?:public|private|protected)\s+(?:(?:static|final|abstract|synchronized)\s+)*(?:void|boolean|byte|short|int|long|float|double|char|String|[A-Z]\w*(?:<[^\n>]+>)?)\s+\w+\s*\("""),
        Regex("""(?m)^\s*(?=\w+(?:\.\w+)*\s*(?:=|\+\+|--))"""),
    )

    fun isInsertable(code: String): Boolean {
        if (code.isBlank() || code.length > MAX_CHARS || code.lines().size > MAX_LINES || code.contains("```")) return false
        val first = code.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (PROSE_FIRST_LINE.matches(first)) return false
        if (!CODE_FIRST_LINE.matches(first)) return false
        // A sentence can also open with a keyword ("Return the sum of ..."), so an accepted line
        // must carry syntax as well, unless it is a comment or an annotation.
        return COMMENT_OR_ANNOTATION.containsMatchIn(first) || first.any { it in SYNTAX_CHARACTERS }
    }
}
