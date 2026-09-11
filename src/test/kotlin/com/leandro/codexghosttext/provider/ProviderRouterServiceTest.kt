package com.leandro.codexghosttext.provider

import com.intellij.openapi.util.TextRange
import com.leandro.codexghosttext.generation.GenerationRequest
import com.leandro.codexghosttext.generation.GenerationResult
import com.leandro.codexghosttext.generation.LocalGenerationProvider
import com.leandro.codexghosttext.generation.ProviderDiagnostic
import com.leandro.codexghosttext.generation.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRouterServiceTest {
    @Test
    fun `defaults unknown persisted selection to Codex and saves an explicit selection`() {
        val state = ProviderProjectState().apply { loadState(ProviderProjectState.Data("NOT_A_PROVIDER")) }
        assertEquals(ProviderId.CODEX, state.selectedProvider())

        state.select(ProviderId.CLAUDE)
        assertEquals("CLAUDE", state.state.selectedProvider)
        assertEquals(ProviderId.CLAUDE, state.selectedProvider())
    }

    @Test
    fun `routes only to the explicit selected provider even when it is unavailable`() {
        val codex = FakeProvider(ProviderId.CODEX, ProviderDiagnostic.READY)
        val claude = FakeProvider(ProviderId.CLAUDE, ProviderDiagnostic.LOGIN_REQUIRED)
        val router = router(ProviderProjectState(), codex, claude)

        assertEquals(ProviderDiagnostic.READY, router.checkAvailability())
        router.select(ProviderId.CLAUDE)

        assertEquals(ProviderId.CLAUDE, router.snapshot().providerId)
        assertEquals(ProviderDiagnostic.LOGIN_REQUIRED, router.checkAvailability())
        assertEquals(1, codex.availabilityChecks)
        assertEquals(1, claude.availabilityChecks)
    }

    @Test
    fun `switch cancels old provider and preview before persisting then invalidates late results`() {
        val events = mutableListOf<String>()
        val codex = FakeProvider(ProviderId.CODEX, ProviderDiagnostic.READY, events)
        val claude = FakeProvider(ProviderId.CLAUDE, ProviderDiagnostic.READY, events)
        val state = ProviderProjectState()
        val router = ProviderRouterService(
            state,
            mapOf(ProviderId.CODEX to codex, ProviderId.CLAUDE to claude),
            cancelPreview = { events += "preview.cancel" },
            notifySelection = { events += "notify:${it.providerId}" },
        )
        val before = router.snapshot()

        assertTrue(router.select(ProviderId.CLAUDE))
        val after = router.snapshot()

        assertEquals(listOf("CODEX.cancel", "preview.cancel", "notify:CLAUDE"), events)
        assertEquals(ProviderId.CLAUDE, state.selectedProvider())
        assertTrue(after.epoch > before.epoch)
        assertFalse(router.isCurrent(before))
        assertTrue(router.isCurrent(after))
        assertFalse(router.select(ProviderId.CLAUDE))
        assertEquals(3, events.size)
    }

    @Test
    fun `reset affects only the provider selected for this project`() {
        val codex = FakeProvider(ProviderId.CODEX, ProviderDiagnostic.READY)
        val claude = FakeProvider(ProviderId.CLAUDE, ProviderDiagnostic.READY)
        val router = router(ProviderProjectState(), codex, claude)

        router.resetSelectedConversation()
        router.select(ProviderId.CLAUDE)
        router.resetSelectedConversation()

        assertEquals(1, codex.resetCalls)
        assertEquals(1, claude.resetCalls)
    }

    @Test
    fun `switches to the other provider only when the selected CLI is not installed`() {
        val codex = FakeProvider(ProviderId.CODEX, ProviderDiagnostic.MISSING_EXECUTABLE, installed = false)
        val claude = FakeProvider(ProviderId.CLAUDE, ProviderDiagnostic.READY)
        val router = router(ProviderProjectState(), codex, claude)

        assertEquals(ProviderId.CLAUDE, router.switchToInstalledProvider())
        assertEquals(ProviderId.CLAUDE, router.snapshot().providerId)
        // Already on an installed provider: nothing to substitute.
        assertNull(router.switchToInstalledProvider())
    }

    @Test
    fun `keeps a selected provider that is installed but not usable`() {
        val codex = FakeProvider(ProviderId.CODEX, ProviderDiagnostic.LOGIN_REQUIRED)
        val claude = FakeProvider(ProviderId.CLAUDE, ProviderDiagnostic.READY)
        val router = router(ProviderProjectState(), codex, claude)

        assertNull(router.switchToInstalledProvider())
        assertEquals(ProviderId.CODEX, router.snapshot().providerId)
    }

    @Test
    fun `keeps the selection when neither CLI is installed`() {
        val codex = FakeProvider(ProviderId.CODEX, ProviderDiagnostic.MISSING_EXECUTABLE, installed = false)
        val claude = FakeProvider(ProviderId.CLAUDE, ProviderDiagnostic.MISSING_EXECUTABLE, installed = false)
        val router = router(ProviderProjectState(), codex, claude)

        assertNull(router.switchToInstalledProvider())
        assertEquals(ProviderId.CODEX, router.snapshot().providerId)
    }

    private fun router(state: ProviderProjectState, codex: FakeProvider, claude: FakeProvider) =
        ProviderRouterService(state, mapOf(ProviderId.CODEX to codex, ProviderId.CLAUDE to claude), {}, {})

    private class FakeProvider(
        override val providerId: ProviderId,
        private val diagnostic: ProviderDiagnostic,
        private val events: MutableList<String>? = null,
        private val installed: Boolean = true,
    ) : LocalGenerationProvider {
        var availabilityChecks = 0
        var resetCalls = 0

        override fun isInstalled(): Boolean = installed

        override fun checkAvailability(): ProviderDiagnostic {
            availabilityChecks++
            return diagnostic
        }

        override fun generate(request: GenerationRequest): GenerationResult = GenerationResult.Success("fun fake() = Unit")

        override fun cancel() {
            events?.add("${providerId.name}.cancel")
        }

        override fun isGenerating(): Boolean = false

        override fun resetConversation() {
            resetCalls++
        }
    }
}
