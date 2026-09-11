package com.leandro.codexghosttext.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader
import java.nio.file.Files

class CodexAvailabilityServiceTest {
    @Test
    fun `accepts a ChatGPT account without retaining its payload`() {
        val result = CodexProtocol.classifyAccount(
            """{"jsonrpc":"2.0","id":2,"result":{"account":{"type":"chatgpt"},"requiresOpenaiAuth":false}}""",
        )

        assertEquals(CodexDiagnostic.CHATGPT_READY, result)
    }

    @Test
    fun `accepts a ChatGPT account that also advertises required OpenAI auth`() {
        val result = CodexProtocol.classifyAccount(
            """{"id":2,"result":{"account":{"type":"chatgpt","planType":"plus"},"requiresOpenaiAuth":true}}""",
        )

        assertEquals(CodexDiagnostic.CHATGPT_READY, result)
    }

    @Test
    fun `requires login when the account is null`() {
        val result = CodexProtocol.classifyAccount("""{"id":2,"result":{"account":null,"requiresOpenaiAuth":true}}""")

        assertEquals(CodexDiagnostic.LOGIN_REQUIRED, result)
    }

    @Test
    fun `requires login when the account endpoint says authentication is needed`() {
        val result = CodexProtocol.classifyAccount(
            """{"jsonrpc":"2.0","id":2,"result":{"requiresOpenaiAuth":true}}""",
        )

        assertEquals(CodexDiagnostic.LOGIN_REQUIRED, result)
    }

    @Test
    fun `rejects non ChatGPT account types`() {
        val result = CodexProtocol.classifyAccount(
            """{"jsonrpc":"2.0","id":2,"result":{"account":{"type":"api"}}}""",
        )

        assertEquals(CodexDiagnostic.UNSUPPORTED_AUTH, result)
    }

    @Test
    fun `treats an exhausted rate-limit window as unavailable`() {
        assertTrue(CodexProtocol.isQuotaExhausted("""{"result":{"rateLimits":[{"usedPercent":100}]}}"""))
        assertFalse(CodexProtocol.isQuotaExhausted("""{"result":{"rateLimits":[{"usedPercent":42.5}]}}"""))
    }

    @Test
    fun `skips a notification before the correlated response`() {
        val reader = BufferedReader(
            StringReader(
                """
                {"jsonrpc":"2.0","method":"item/started","params":{}}
                {"jsonrpc":"2.0","id":2,"result":{}}
                """.trimIndent(),
            ),
        )

        assertEquals("""{"jsonrpc":"2.0","id":2,"result":{}}""", CodexProtocol.responseForId(reader, 2))
    }

    @Test
    fun `skips a server request and continues to the correlated response`() {
        val reader = BufferedReader(
            StringReader(
                """
                {"jsonrpc":"2.0","id":91,"method":"client/request","params":{}}
                {"jsonrpc":"2.0","id":2,"result":{}}
                """.trimIndent(),
            ),
        )

        assertEquals("""{"jsonrpc":"2.0","id":2,"result":{}}""", CodexProtocol.responseForId(reader, 2))
    }

    @Test
    fun `fails closed for malformed oversized or disconnected output`() {
        assertNull(CodexProtocol.responseForId(BufferedReader(StringReader("not-json")), 2))
        assertNull(CodexProtocol.responseForId(BufferedReader(StringReader("{" + "x".repeat(64 * 1024) + "}")), 2))
        assertNull(CodexProtocol.responseForId(BufferedReader(StringReader("")), 2))
    }

    @Test
    fun `does not invent an executable when no supported location contains one`() {
        // An empty home keeps the search off this machine's own Codex install.
        val home = Files.createTempDirectory("codex-ghost-text-home").toString()

        assertNull(
            CodexExecutableLocator.find(
                path = "C:\\missing-codex-bin",
                localAppData = null,
                userHome = home,
                osName = "Windows 11",
            ),
        )
    }
}
