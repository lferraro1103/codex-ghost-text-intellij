package com.leandro.codexghosttext.actions

import com.leandro.codexghosttext.generation.GenerationRequest
import com.leandro.codexghosttext.generation.GenerationResult
import com.leandro.codexghosttext.generation.LocalGenerationProvider
import com.leandro.codexghosttext.generation.ProviderDiagnostic
import com.leandro.codexghosttext.generation.ProviderId
import com.leandro.codexghosttext.provider.ProviderProjectState
import com.leandro.codexghosttext.provider.ProviderRouterService
import com.leandro.codexghosttext.provider.SelectedProviderAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAwareActionsTest {
    @Test
    fun `check uses only the selected provider and keeps its diagnostic semantics`() {
        val codex = FakeProvider(ProviderId.CODEX, ProviderDiagnostic.QUOTA_EXHAUSTED)
        val claude = FakeProvider(ProviderId.CLAUDE, ProviderDiagnostic.UNSAFE_CAPABILITIES)
        val state = ProviderProjectState()
        val router = router(state, codex, claude)
        val operations = SelectedProviderActions(router)

        val codexCheck = operations.checkSelectedAvailability()

        assertEquals(ProviderId.CODEX, codexCheck.providerId)
        assertEquals(ProviderDiagnostic.QUOTA_EXHAUSTED, codexCheck.diagnostic)
        assertEquals(1, codex.availabilityChecks)
        assertEquals(0, claude.availabilityChecks)
        assertEquals(ProviderId.CODEX, state.selectedProvider())
        assertTrue(ProviderActionFeedback.messageFor(codexCheck).contains("cuota de Codex"))

        router.select(ProviderId.CLAUDE)
        val claudeCheck = operations.checkSelectedAvailability()

        assertEquals(ProviderId.CLAUDE, claudeCheck.providerId)
        assertEquals(ProviderDiagnostic.UNSAFE_CAPABILITIES, claudeCheck.diagnostic)
        assertEquals(1, codex.availabilityChecks)
        assertEquals(1, claude.availabilityChecks)
        assertEquals(ProviderId.CLAUDE, state.selectedProvider())
        assertTrue(ProviderActionFeedback.messageFor(claudeCheck).contains("modo seguro"))
    }

    @Test
    fun `reset captures and clears only the selected provider conversation`() {
        val codex = FakeProvider(ProviderId.CODEX, ProviderDiagnostic.READY, session = "codex-thread")
        val claude = FakeProvider(ProviderId.CLAUDE, ProviderDiagnostic.READY, session = "claude-session")
        var previewCancels = 0
        val state = ProviderProjectState()
        val router = ProviderRouterService(
            state,
            mapOf(ProviderId.CODEX to codex, ProviderId.CLAUDE to claude),
            cancelPreview = { previewCancels++ },
            notifySelection = {},
        )
        val operations = SelectedProviderActions(router)

        router.select(ProviderId.CLAUDE)
        val resetProvider = operations.resetSelectedConversation()

        assertEquals(ProviderId.CLAUDE, resetProvider)
        assertEquals("codex-thread", codex.session)
        assertEquals(null, claude.session)
        assertEquals(0, codex.resetCalls)
        assertEquals(1, claude.resetCalls)
        assertTrue(claude.cancelCalls >= 1)
        assertFalse(codex.cancelCalls == 0) // The explicit switch, not reset, cancels old Codex work.
        assertEquals(2, previewCancels) // One switch and one reset, both dismiss ghost preview.
        assertTrue(ProviderActionFeedback.resetMessage(resetProvider).contains("Claude"))
    }

    @Test
    fun `provider specific feedback stays actionable without credential access`() {
        assertTrue(
            ProviderActionFeedback.messageFor(SelectedProviderAvailability(ProviderId.CODEX, ProviderDiagnostic.LOGIN_REQUIRED))
                .contains("codex login"),
        )
        assertTrue(
            ProviderActionFeedback.messageFor(SelectedProviderAvailability(ProviderId.CLAUDE, ProviderDiagnostic.VERSION_UNSUPPORTED))
                .contains("Actualizá Claude"),
        )
        assertTrue(
            ProviderActionFeedback.messageFor(SelectedProviderAvailability(ProviderId.CLAUDE, ProviderDiagnostic.LOGIN_REQUIRED))
                .contains("claude login"),
        )
    }

    private fun router(
        state: ProviderProjectState,
        codex: FakeProvider,
        claude: FakeProvider,
    ): ProviderRouterService = ProviderRouterService(
        state,
        mapOf(ProviderId.CODEX to codex, ProviderId.CLAUDE to claude),
        {},
        {},
    )

    private class FakeProvider(
        override val providerId: ProviderId,
        private val diagnostic: ProviderDiagnostic,
        var session: String? = null,
    ) : LocalGenerationProvider {
        var availabilityChecks = 0
        var cancelCalls = 0
        var resetCalls = 0

        override fun checkAvailability(): ProviderDiagnostic {
            availabilityChecks++
            return diagnostic
        }

        override fun generate(request: GenerationRequest): GenerationResult = GenerationResult.Success("fun fake() = Unit")

        override fun cancel() {
            cancelCalls++
        }

        override fun isGenerating(): Boolean = false

        override fun resetConversation() {
            resetCalls++
            session = null
        }
    }
}
