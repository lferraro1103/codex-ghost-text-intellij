package com.leandro.codexghosttext.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodexProjectConversationStateTest {
    @Test
    fun `returns a saved conversation only for its canonical project root`() {
        val state = CodexProjectConversationState()
        state.remember("C:/work/example", "thread_example")

        assertEquals("thread_example", state.threadFor("C:/work/example"))
        assertNull(state.threadFor("C:/work/copied-example"))
    }

    @Test
    fun `forget clears only the local association`() {
        val state = CodexProjectConversationState()
        state.remember("C:/work/example", "thread_example")
        state.forget()

        assertNull(state.threadFor("C:/work/example"))
    }
}
