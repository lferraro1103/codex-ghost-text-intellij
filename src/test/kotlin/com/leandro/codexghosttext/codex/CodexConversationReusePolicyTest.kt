package com.leandro.codexghosttext.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodexConversationReusePolicyTest {
    @Test
    fun `reuses one live thread for consecutive requests in the same project`() {
        val policy = CodexConversationReusePolicy()
        val calls = mutableListOf<String>()
        val thread = policy.establish(
            "C:/work/example",
            null,
            resume = { error("No stored chat should be resumed") },
            start = { calls += "thread/start"; "thread_new" },
        )

        assertEquals("thread_new", thread)
        assertEquals("thread_new", policy.liveThreadFor("C:/work/example", sessionIsLive = true))
        assertEquals(listOf("thread/start"), calls)
    }

    @Test
    fun `resumes the saved project chat before creating a replacement`() {
        val policy = CodexConversationReusePolicy()
        val calls = mutableListOf<String>()

        val thread = policy.establish(
            "C:/work/example",
            "thread_saved",
            resume = { saved -> calls += "thread/resume:$saved"; saved },
            start = { calls += "thread/start"; "thread_new" },
        )

        assertEquals("thread_saved", thread)
        assertEquals(listOf("thread/resume:thread_saved"), calls)
    }

    @Test
    fun `falls back to one new chat only when the saved chat cannot be resumed`() {
        val policy = CodexConversationReusePolicy()
        val calls = mutableListOf<String>()

        val thread = policy.establish(
            "C:/work/example",
            "thread_expired",
            resume = { saved -> calls += "thread/resume:$saved"; null },
            start = { calls += "thread/start"; "thread_replacement" },
        )

        assertEquals("thread_replacement", thread)
        assertEquals(listOf("thread/resume:thread_expired", "thread/start"), calls)
    }

    @Test
    fun `clearing the session prevents accidental reuse`() {
        val policy = CodexConversationReusePolicy()
        policy.establish("C:/work/example", null, resume = { null }, start = { "thread_new" })
        policy.clearActive()

        assertNull(policy.liveThreadFor("C:/work/example", sessionIsLive = true))
    }
}
