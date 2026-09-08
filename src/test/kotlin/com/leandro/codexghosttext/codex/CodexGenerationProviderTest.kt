package com.leandro.codexghosttext.codex

import com.intellij.openapi.util.TextRange
import com.leandro.codexghosttext.generation.GenerationRequest
import com.leandro.codexghosttext.generation.GenerationResult
import com.leandro.codexghosttext.generation.ProviderDiagnostic
import com.leandro.codexghosttext.generation.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexGenerationProviderTest {
    @Test
    fun `delegates the neutral request unchanged to the existing Codex path`() {
        var delegated: Triple<String, String, TextRange>? = null
        var cancelled = false
        var reset = false
        val provider = CodexGenerationProvider(
            generate = { comment, document, range ->
                delegated = Triple(comment, document, range)
                GenerationResult.Success("fun generated() = Unit")
            },
            availability = { CodexDiagnostic.CHATGPT_READY },
            cancel = { cancelled = true },
            isGenerating = { true },
            reset = { reset = true },
        )
        val request = GenerationRequest("// add method", "class Demo", TextRange(0, 13), "C:/work/demo")

        assertEquals(ProviderId.CODEX, provider.providerId)
        assertEquals(GenerationResult.Success("fun generated() = Unit"), provider.generate(request))
        assertEquals(Triple(request.comment, request.documentText, request.range), delegated)
        assertEquals(ProviderDiagnostic.READY, provider.checkAvailability())
        assertTrue(provider.isGenerating())

        provider.cancel()
        provider.resetConversation()

        assertTrue(cancelled)
        assertTrue(reset)
    }

    @Test
    fun `keeps actionable Codex login quota and connection diagnostics`() {
        fun diagnostic(source: CodexDiagnostic) = CodexGenerationProvider(
            generate = { _, _, _ -> GenerationResult.Failure("unused") },
            availability = { source },
            cancel = {},
            isGenerating = { false },
            reset = {},
        ).checkAvailability()

        assertEquals(ProviderDiagnostic.LOGIN_REQUIRED, diagnostic(CodexDiagnostic.LOGIN_REQUIRED))
        assertEquals(ProviderDiagnostic.LOGIN_REQUIRED, diagnostic(CodexDiagnostic.UNSUPPORTED_AUTH))
        assertEquals(ProviderDiagnostic.QUOTA_EXHAUSTED, diagnostic(CodexDiagnostic.QUOTA_EXHAUSTED))
        assertEquals(ProviderDiagnostic.PROCESS_FAILED, diagnostic(CodexDiagnostic.CONNECTION_FAILED))
        assertFalse(diagnostic(CodexDiagnostic.MISSING_EXECUTABLE).isReady)
    }
}
