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

    @Test
    fun `recognizes an error response without treating a successful response as an error`() {
        assertEquals(true, """{"jsonrpc":"2.0","id":3,"error":{"code":-32602}}""".isJsonRpcError())
        assertEquals(false, """{"jsonrpc":"2.0","id":3,"result":{}}""".isJsonRpcError())
    }

    @Test
    fun `keeps code and removes a leading agent explanation`() {
        val response = "Voy a revisar Nodo antes de implementarlo. public void sacarRaiz() {\n  raiz = null;\n}"
        assertEquals("public void sacarRaiz() {\n  raiz = null;\n}", response.codeOnlyProposal())
    }

    @Test
    fun `rejects a natural language response without code`() {
        assertEquals("", "Voy a revisar la clase primero.".codeOnlyProposal())
    }
}
