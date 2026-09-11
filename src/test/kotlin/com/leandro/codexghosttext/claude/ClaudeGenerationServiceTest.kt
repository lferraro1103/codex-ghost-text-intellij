package com.leandro.codexghosttext.claude

import com.intellij.openapi.util.TextRange
import com.leandro.codexghosttext.generation.GenerationRequest
import com.leandro.codexghosttext.generation.GenerationResult
import com.leandro.codexghosttext.generation.ProviderDiagnostic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path
import java.util.ArrayDeque

class ClaudeGenerationServiceTest {
    @Test
    fun `accepts code from Claude documented json print envelope`() {
        val response = ClaudeProcessResult(
            stdout = "{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":false,\"session_id\":\"claude-session-json\",\"result\":\"fun nueva() = Unit\"}",
            exitCode = 0,
        )
        val state = ClaudeProjectConversationState()

        val result = ClaudeGenerationService(ScriptedClaudeRunner(response), state).generate(request())

        assertEquals(GenerationResult.Success("fun nueva() = Unit"), result)
        assertEquals("claude-session-json", state.sessionFor("C:/workspace/arbol"))
    }

    @Test
    fun `unwraps the single Markdown fence Claude wraps a code-only answer in`() {
        val state = ClaudeProjectConversationState()
        val runner = ScriptedClaudeRunner(success("```javascript\nfunction sumar(a, b) {\n  return a + b;\n}\n```"))

        val result = ClaudeGenerationService(runner, state).generate(request())

        assertEquals(GenerationResult.Success("function sumar(a, b) {\n  return a + b;\n}"), result)
    }

    @Test
    fun `sends the edited file's language in the prompt`() {
        val runner = ScriptedClaudeRunner(success("fun nueva() = Unit"))

        ClaudeGenerationService(runner, ClaudeProjectConversationState()).generate(
            request().copy(language = "Kotlin", fileName = "Arbol.kt"),
        )

        assertTrue(runner.requests.single().prompt.contains("El archivo es Kotlin (Arbol.kt)"))
    }

    @Test
    fun `rejects a proposal fenced as a language the edited file is not`() {
        val runner = ScriptedClaudeRunner(success("```javascript\nfunction sumar(a, b) {\n  return a + b;\n}\n```"))

        val result = ClaudeGenerationService(runner, ClaudeProjectConversationState()).generate(
            request().copy(language = "Kotlin", fileName = "Arbol.kt"),
        )

        assertTrue(result is GenerationResult.Failure)
    }

    @Test
    fun `accepts a proposal written in a language other than Kotlin or Java`() {
        val accepted = listOf(
            "const total = a + b;",
            "def sumar(a, b):\n    return a + b",
            "raiz = null;",
        )

        accepted.forEach { code ->
            val result = ClaudeGenerationService(ScriptedClaudeRunner(success(code)), ClaudeProjectConversationState())
                .generate(request())

            assertEquals(GenerationResult.Success(code), result)
        }
    }

    @Test
    fun `returns only validated code and persists its Claude session for the same canonical root`() {
        val runner = ScriptedClaudeRunner(success("public void sacarRaiz() {\n  raiz = null;\n}"))
        val state = ClaudeProjectConversationState()
        val service = ClaudeGenerationService(runner, state)

        val result = service.generate(request("C:/workspace/../workspace/arbol"))

        assertEquals(GenerationResult.Success("public void sacarRaiz() {\n  raiz = null;\n}"), result)
        assertEquals("claude-session-1", state.sessionFor("C:/workspace/arbol"))
        assertNull(state.sessionFor("C:/workspace/other"))
        val prompt = runner.requests.single().prompt
        assertTrue(prompt.contains("// sacar la raíz"))
        assertTrue(prompt.contains("before-context"))
        assertTrue(prompt.contains("after-context"))
        assertFalse(prompt.contains("C:/workspace", ignoreCase = true))
        assertTrue(prompt.contains("herramientas de lectura"))
        assertEquals(Path.of("C:/workspace/../workspace/arbol"), runner.requests.single().projectRoot)
    }

    @Test
    fun `uses the exact bounded editor window without accepting a document`() {
        val runner = ScriptedClaudeRunner(success("fun nueva() = Unit"))
        val service = ClaudeGenerationService(runner, ClaudeProjectConversationState())
        val before = "a".repeat(2_100)
        val selected = "// nueva"
        val after = "b".repeat(4_100)
        val text = before + selected + after

        service.generate(
            GenerationRequest(selected, text, TextRange(2_100, 2_100 + selected.length), "C:/workspace/arbol"),
        )

        val prompt = runner.requests.single().prompt
        assertTrue(prompt.contains("a".repeat(2_000)))
        assertFalse(prompt.contains("a".repeat(2_001)))
        assertTrue(prompt.contains("b".repeat(4_000)))
        assertFalse(prompt.contains("b".repeat(4_001)))
    }

    @Test
    fun `rejects malformed non-schema prose markdown duplicate unknown and oversized envelopes without session`() {
        val rejected = listOf(
            "not json",
            "{\"session_id\":\"claude-session-1\"}",
            "{\"type\":\"error\",\"session_id\":\"claude-session-1\",\"structured_output\":{\"code\":\"fun x() = Unit\"}}",
            "{\"session_id\":\"claude-session-1\",\"structured_output\":{\"code\":\"   \"}}",
            "{\"session_id\":\"claude-session-1\",\"structured_output\":{\"code\":\"Voy a implementar esto.\"}}",
            "{\"session_id\":\"claude-session-1\",\"structured_output\":{\"code\":\"Esto genera:\\n```kotlin\\nfun x() = Unit\\n```\"}}",
            "{\"session_id\":\"claude-session-1\",\"session_id\":\"other\",\"structured_output\":{\"code\":\"fun x() = Unit\"}}",
            "{\"session_id\":\"claude-session-1\",\"structured_output\":{\"code\":\"fun x() = Unit\",\"extra\":true}}",
            "{\"session_id\":\"claude-session-1\",\"structured_output\":{\"code\":\"fun x() = Unit\"},\"tool_use\":true}",
            success("fun x() = Unit".repeat(1_500)).stdout,
        ).map { ClaudeProcessResult(stdout = it, exitCode = 0) }

        rejected.forEach { result ->
            val state = ClaudeProjectConversationState()
            val proposal = ClaudeGenerationService(ScriptedClaudeRunner(result), state).generate(request())
            assertTrue("Expected failure for ${result.stdout}", proposal is GenerationResult.Failure)
            assertNull(state.sessionFor("C:/workspace/arbol"))
        }
    }

    @Test
    fun `never creates a fresh Claude chat automatically when resume fails`() {
        val state = ClaudeProjectConversationState().also { it.remember("C:/workspace/arbol", "claude-session-saved") }
        val runner = ScriptedClaudeRunner(
            ClaudeProcessResult(exitCode = 1, diagnostic = ProviderDiagnostic.PROCESS_FAILED),
            success("fun fresca() = Unit", "claude-session-fresh"),
        )
        val service = ClaudeGenerationService(runner, state)

        assertTrue(service.generate(request()) is GenerationResult.Failure)
        assertEquals(listOf("claude-session-saved"), runner.requests.map { it.resumeSessionId })
        assertEquals("claude-session-saved", state.sessionFor("C:/workspace/arbol"))
    }

    @Test
    fun `cancellation timeout and a failed fresh retry never save or leak a session`() {
        listOf(
            ClaudeProcessResult(diagnostic = ProviderDiagnostic.CANCELLED),
            ClaudeProcessResult(diagnostic = ProviderDiagnostic.PROCESS_TIMEOUT),
        ).forEach { processResult ->
            val state = ClaudeProjectConversationState()
            val service = ClaudeGenerationService(ScriptedClaudeRunner(processResult), state)
            assertTrue(service.generate(request()) is GenerationResult.Failure)
            assertNull(state.sessionFor("C:/workspace/arbol"))
            service.cancel()
        }

        val state = ClaudeProjectConversationState().also { it.remember("C:/workspace/arbol", "claude-session-saved") }
        val runner = ScriptedClaudeRunner(
            ClaudeProcessResult(exitCode = 1, diagnostic = ProviderDiagnostic.PROCESS_FAILED),
            ClaudeProcessResult(exitCode = 1, diagnostic = ProviderDiagnostic.PROCESS_FAILED),
        )
        assertTrue(ClaudeGenerationService(runner, state).generate(request()) is GenerationResult.Failure)
        assertEquals(1, runner.requests.size)
        assertEquals("claude-session-saved", state.sessionFor("C:/workspace/arbol"))
    }

    @Test
    fun `a cancelled or timed out resume preserves Claude state and never retries fresh`() {
        listOf(ProviderDiagnostic.CANCELLED, ProviderDiagnostic.PROCESS_TIMEOUT).forEach { diagnostic ->
            val state = ClaudeProjectConversationState().also { it.remember("C:/workspace/arbol", "claude-session-saved") }
            val runner = ScriptedClaudeRunner(
                ClaudeProcessResult(diagnostic = diagnostic),
                success("fun mustNotRun() = Unit", "claude-session-fresh"),
            )

            assertTrue(ClaudeGenerationService(runner, state).generate(request()) is GenerationResult.Failure)
            assertEquals(listOf("claude-session-saved"), runner.requests.map { it.resumeSessionId })
            assertEquals("claude-session-saved", state.sessionFor("C:/workspace/arbol"))
        }
    }

    private fun request(root: String = "C:/workspace/arbol") = GenerationRequest(
        comment = "// sacar la raíz",
        documentText = "before-context\n// sacar la raíz\nafter-context",
        range = TextRange("before-context\n".length, "before-context\n// sacar la raíz".length),
        projectRoot = root,
    )

    private fun success(code: String, sessionId: String = "claude-session-1") = ClaudeProcessResult(
        stdout = "{\"session_id\":\"$sessionId\",\"structured_output\":{\"code\":${json(code)}}}",
        exitCode = 0,
    )

    private fun json(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + "\""
}

private class ScriptedClaudeRunner(vararg results: ClaudeProcessResult) : ClaudeProcessRunner {
    private val scripted = ArrayDeque(results.toList())
    val requests = mutableListOf<ClaudeProcessRequest>()
    var cancelCalls = 0
        private set

    override fun isInstalled(): Boolean = true

    override fun probe() = ClaudeCapabilityProfile(Path.of("C:/tools/claude.exe"), ProviderDiagnostic.READY, ClaudeProcessRunner.requiredCapabilityFlags)

    override fun run(profile: ClaudeCapabilityProfile, request: ClaudeProcessRequest): ClaudeProcessResult {
        requests += request
        return if (scripted.isEmpty()) ClaudeProcessResult(diagnostic = ProviderDiagnostic.PROCESS_FAILED) else scripted.removeFirst()
    }

    override fun cancel() {
        cancelCalls += 1
    }

    override fun dispose() = Unit
}
