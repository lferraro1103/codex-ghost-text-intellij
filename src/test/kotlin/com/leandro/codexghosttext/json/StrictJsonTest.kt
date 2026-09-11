package com.leandro.codexghosttext.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StrictJsonTest {
    @Test
    fun `reads an envelope whose fields contain populated arrays`() {
        // A CLI envelope carries arrays next to the fields this plugin reads. Treating `[` as a
        // literal used to work only while every array happened to be empty.
        val envelope = """
            {"session_id":"s-1","permission_denials":[{"tool_name":"Bash","input":{"command":"ls"}}],
             "usage":{"iterations":[1,2,3]},"result":"fun x() = Unit"}
        """.trimIndent().replace("\n", "")

        val root = JsonValue.parseObject(envelope)

        assertEquals("s-1", root?.string("session_id"))
        assertEquals("fun x() = Unit", root?.string("result"))
        assertEquals(1, root?.array("permission_denials")?.values?.size)
        assertEquals(3, root?.obj("usage")?.array("iterations")?.values?.size)
    }

    @Test
    fun `reads a nested array of objects without losing the sibling fields`() {
        val record = """{"params":{"turn":{"items":[{"type":"agentMessage","text":"a"}],"status":"completed"}}}"""

        val turn = JsonValue.parseObject(record)?.obj("params")?.obj("turn")

        assertEquals("completed", turn?.string("status"))
        assertEquals("agentMessage", (turn?.array("items")?.values?.first() as? JsonValue.ObjectValue)?.string("type"))
    }

    @Test
    fun `a JSON-RPC id is readable whether it is a number or a string`() {
        assertEquals("2", JsonValue.parseObject("""{"id":2,"result":{}}""")?.scalar("id"))
        assertEquals("2", JsonValue.parseObject("""{"id":"2","result":{}}""")?.scalar("id"))
    }

    @Test
    fun `still refuses duplicate keys trailing content and malformed input`() {
        assertNull(JsonValue.parse("""{"a":1,"a":2}"""))
        assertNull(JsonValue.parse("""{"a":1} {"b":2}"""))
        assertNull(JsonValue.parse("""{"a":[1,2}"""))
        assertNull(JsonValue.parse("not json"))
        assertNotNull(JsonValue.parse("""{"a":[]}"""))
    }
}
