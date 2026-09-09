package com.leandro.codexghosttext.generation

import com.intellij.openapi.util.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceLanguageTest {
    @Test
    fun `names the language and the file so a provider does not infer one from context`() {
        assertEquals(
            "El archivo es Kotlin (Arbol.kt). El código debe ser Kotlin válido.",
            SourceLanguage.instruction(request(language = "Kotlin", fileName = "Arbol.kt")),
        )
        assertEquals("El código debe ser Kotlin válido.", SourceLanguage.instruction(request(language = "Kotlin")))
        assertEquals("", SourceLanguage.instruction(request()))
    }

    @Test
    fun `names the resolved project files and their declarations`() {
        val section = SourceLanguage.projectSection(
            request().copy(
                dependencyPaths = listOf("src/main/java/Nodo.java"),
                dependencySkeletons = listOf("// src/main/java/Nodo.java\nNodo(int)"),
            ),
        )

        assertTrue(section.contains("- src/main/java/Nodo.java"))
        assertTrue(section.contains("Nodo(int)"))
    }

    @Test
    fun `says nothing when nothing was resolved`() {
        assertEquals("", SourceLanguage.projectSection(request()))
    }

    @Test
    fun `rejects a fence that announces a different language than the edited file`() {
        assertTrue(SourceLanguage.fenceConflicts("javascript", "Kotlin", "Arbol.kt"))
        assertTrue(SourceLanguage.fenceConflicts("python", "JAVA", "Tree.java"))
        // The file name alone is enough when the IDE language name is not a spelling we know.
        assertTrue(SourceLanguage.fenceConflicts("java", "TypeScript JSX", "widget.tsx"))
    }

    @Test
    fun `accepts every spelling of the file's own language`() {
        assertFalse(SourceLanguage.fenceConflicts("kotlin", "Kotlin", "Arbol.kt"))
        assertFalse(SourceLanguage.fenceConflicts("kt", "Kotlin", "Arbol.kt"))
        assertFalse(SourceLanguage.fenceConflicts("js", "JavaScript", "tree.js"))
        assertFalse(SourceLanguage.fenceConflicts("ts", "TypeScript JSX", "widget.tsx"))
    }

    @Test
    fun `an unknown tag or an unknown file language is never a conflict`() {
        assertFalse(SourceLanguage.fenceConflicts("brainfuck", "Kotlin", "Arbol.kt"))
        assertFalse(SourceLanguage.fenceConflicts("kotlin", "", ""))
        assertFalse(SourceLanguage.fenceConflicts("", "Kotlin", "Arbol.kt"))
    }

    private fun request(language: String = "", fileName: String = "") = GenerationRequest(
        comment = "// x",
        documentText = "// x",
        range = TextRange(0, 4),
        projectRoot = "C:/work/demo",
        language = language,
        fileName = fileName,
    )
}
