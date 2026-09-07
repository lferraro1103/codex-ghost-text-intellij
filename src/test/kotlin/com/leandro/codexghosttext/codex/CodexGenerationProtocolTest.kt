package com.leandro.codexghosttext.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodexGenerationProtocolTest {
    @Test
    fun `extracts the nested thread id instead of the JSON RPC response id`() {
        val response = """{"jsonrpc":"2.0","id":2,"result":{"thread":{"id":"thr_local_123"}}}"""
        assertEquals("thr_local_123", response.threadId())
    }

    @Test
    fun `decodes streamed JSON string text`() {
        assertEquals("fun hello() {\n}", """{"delta":"fun hello() {\n}"}""".jsonField("delta"))
    }

    @Test
    fun `rejects a response without a thread`() {
        assertNull("""{"jsonrpc":"2.0","id":2,"result":{}}""".threadId())
    }
}
