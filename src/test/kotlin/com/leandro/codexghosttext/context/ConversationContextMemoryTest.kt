package com.leandro.codexghosttext.context

import com.leandro.codexghosttext.generation.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationContextMemoryTest {
    @Test
    fun `a declaration is sent once per provider conversation`() {
        val memory = ConversationContextMemory()
        val skeletons = listOf("// Nodo.java\nNodo", "// Arbol.java\nArbol")

        assertEquals(skeletons, memory.unsent(ProviderId.CLAUDE, skeletons))
        memory.remember(ProviderId.CLAUDE, skeletons)

        assertEquals(emptyList<String>(), memory.unsent(ProviderId.CLAUDE, skeletons))
        // The other provider has its own conversation and has not been told anything.
        assertEquals(skeletons, memory.unsent(ProviderId.CODEX, skeletons))
    }

    @Test
    fun `only the declarations already sent are filtered out`() {
        val memory = ConversationContextMemory()
        memory.remember(ProviderId.CODEX, listOf("// Nodo.java\nNodo"))

        assertEquals(
            listOf("// Arbol.java\nArbol"),
            memory.unsent(ProviderId.CODEX, listOf("// Nodo.java\nNodo", "// Arbol.java\nArbol")),
        )
    }

    @Test
    fun `a reset conversation is offered every declaration again`() {
        val memory = ConversationContextMemory()
        val skeletons = listOf("// Nodo.java\nNodo")
        memory.remember(ProviderId.CLAUDE, skeletons)

        memory.forget(ProviderId.CLAUDE)

        assertEquals(skeletons, memory.unsent(ProviderId.CLAUDE, skeletons))
    }
}
