package com.leandro.codexghosttext.status

import com.leandro.codexghosttext.generation.GenerationRequest
import com.leandro.codexghosttext.generation.GenerationResult
import com.leandro.codexghosttext.generation.LocalGenerationProvider
import com.leandro.codexghosttext.generation.ProviderDiagnostic
import com.leandro.codexghosttext.generation.ProviderId
import com.leandro.codexghosttext.provider.ProviderProjectState
import com.leandro.codexghosttext.provider.ProviderRouterService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSelectorWidgetTest {
    @Test
    fun `selector exposes exactly Codex then Claude with Codex selected by default`() {
        val model = model(ProviderProjectState())

        assertEquals(listOf("Codex", "Claude"), model.choiceLabels())
        assertEquals("Codex", model.selectedValue())
        assertEquals(ProviderSelectorWidgetFactory.WIDGET_ID, ProviderSelectorWidgetFactory().id)
        assertTrue(ProviderSelectorWidgetFactory().isEnabledByDefault)
    }

    @Test
    fun `selecting Claude changes only this router and never starts a generation`() {
        val firstState = ProviderProjectState()
        val firstCodex = FakeProvider(ProviderId.CODEX)
        val firstClaude = FakeProvider(ProviderId.CLAUDE)
        val first = model(firstState, firstCodex, firstClaude)
        val secondState = ProviderProjectState()
        val second = model(secondState)

        assertTrue(first.choose(ProviderId.CLAUDE))

        assertEquals(ProviderId.CLAUDE, firstState.selectedProvider())
        assertEquals(ProviderId.CODEX, secondState.selectedProvider())
        assertEquals("Claude", first.selectedValue())
        assertEquals(0, firstCodex.generationCalls + firstClaude.generationCalls)
        assertFalse(first.choose(ProviderId.CLAUDE))
    }

    private fun model(
        state: ProviderProjectState,
        codex: FakeProvider = FakeProvider(ProviderId.CODEX),
        claude: FakeProvider = FakeProvider(ProviderId.CLAUDE),
    ): ProviderSelectorModel = ProviderSelectorModel(
        ProviderRouterService(
            state,
            mapOf(ProviderId.CODEX to codex, ProviderId.CLAUDE to claude),
            {},
            {},
        ),
    )

    private class FakeProvider(override val providerId: ProviderId) : LocalGenerationProvider {
        var generationCalls = 0

        override fun checkAvailability(): ProviderDiagnostic = ProviderDiagnostic.READY

        override fun generate(request: GenerationRequest): GenerationResult {
            generationCalls++
            return GenerationResult.Success("fun generated() = Unit")
        }

        override fun cancel() = Unit

        override fun isGenerating(): Boolean = false

        override fun resetConversation() = Unit
    }
}
