package com.leandro.codexghosttext.codex

import com.leandro.codexghosttext.generation.CodeProposal
import com.leandro.codexghosttext.json.JsonValue
import com.leandro.codexghosttext.json.string
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
        assertEquals("fun hello() {\n}", JsonValue.parseObject("""{"delta":"fun hello() {\n}"}""")?.string("delta"))
    }

    @Test
    fun `reads a record whose sibling fields contain arrays`() {
        val record = """{"method":"turn/completed","params":{"turn":{"items":[{"type":"agentMessage"}],"status":"completed"}}}"""
        val turn = JsonValue.parseObject(record)?.let { it.values["params"] as? JsonValue.ObjectValue }
            ?.let { it.values["turn"] as? JsonValue.ObjectValue }

        assertEquals("completed", turn?.string("status"))
    }

    @Test
    fun `rejects a response without a thread`() {
        assertNull("""{"jsonrpc":"2.0","id":2,"result":{}}""".threadId())
    }

    @Test
    fun `extracts the turn id for safe interruption`() {
        val response = """{"jsonrpc":"2.0","id":3,"result":{"turn":{"id":"turn_local_456"}}}"""
        assertEquals("turn_local_456", response.turnId())
    }

    @Test
    fun `does not confuse a thread id with a turn id`() {
        val response = """{"jsonrpc":"2.0","id":3,"result":{"thread":{"id":"thr_1"},"turn":{"id":"turn_2"}}}"""
        assertEquals("thr_1", response.threadId())
        assertEquals("turn_2", response.turnId())
    }

    @Test
    fun `recognizes an error response without treating a successful response as an error`() {
        assertEquals(true, """{"jsonrpc":"2.0","id":3,"error":{"code":-32602}}""".isJsonRpcError())
        assertEquals(false, """{"jsonrpc":"2.0","id":3,"result":{}}""".isJsonRpcError())
    }

    @Test
    fun `generated code that mentions a cancelled item type is not treated as one`() {
        val delta = """{"method":"item/agentMessage/delta","params":{"delta":"val payload = \"{\\\"type\\\":\\\"fileChange\\\"}\""}}"""

        val event = codexStreamEvent(delta)

        assertEquals(CodexStreamEvent.Delta("val payload = \"{\\\"type\\\":\\\"fileChange\\\"}\""), event)
    }

    @Test
    fun `a real file change or forbidden tool item still cancels the proposal`() {
        assertEquals(
            CodexStreamEvent.FileChangeAttempt,
            codexStreamEvent("""{"method":"item/started","params":{"item":{"type":"fileChange"}}}"""),
        )
        assertEquals(
            CodexStreamEvent.ForbiddenTool,
            codexStreamEvent("""{"method":"item/started","params":{"item":{"type":"mcpToolCall"}}}"""),
        )
        assertEquals(
            CodexStreamEvent.FileChangeAttempt,
            codexStreamEvent("""{"id":7,"method":"applyPatchApproval","params":{}}"""),
        )
    }

    @Test
    fun `turn completion is read from the turn status and not from any status in the line`() {
        val completed = """{"method":"turn/completed","params":{"turn":{"items":[{"type":"agentMessage","text":"status: completed"}],"status":"failed"}}}"""

        assertEquals(CodexStreamEvent.TurnFinished(completed = false), codexStreamEvent(completed))
        assertEquals(
            CodexStreamEvent.TurnFinished(completed = true),
            codexStreamEvent("""{"method":"turn/completed","params":{"turn":{"status":"completed"}}}"""),
        )
    }

    @Test
    fun `keeps code and removes a leading agent explanation`() {
        val response = "Voy a revisar Nodo antes de implementarlo. public void sacarRaiz() {\n  raiz = null;\n}"
        assertEquals("public void sacarRaiz() {\n  raiz = null;\n}", CodeProposal.trimToCodeStart(response))
    }

    @Test
    fun `rejects a natural language response without code`() {
        assertEquals("", CodeProposal.trimToCodeStart("Voy a revisar la clase primero."))
    }
}
