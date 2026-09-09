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
    fun `descriptor keeps existing action identities while exposing neutral provider labels`() {
        val descriptor = requireNotNull(javaClass.classLoader.getResource("META-INF/plugin.xml"))
            .readText()
        assertActionDescriptor(
            descriptor,
            CheckCodexConnectionAction.ACTION_ID,
            CheckCodexConnectionAction::class.java,
            "Check Selected AI Provider Connection",
            "Check the selected local AI provider without generating code",
        )
        assertActionDescriptor(
            descriptor,
            "com.leandro.codexghosttext.ResetCodexProjectConversation",
            ResetCodexProjectConversationAction::class.java,
            "Reset Selected Provider Conversation for This Project",
            "Forget this project's selected provider conversation and create a new one on the next generation",
        )
        assertActionDescriptor(
            descriptor,
            GenerateCodexGhostTextAction.ACTION_ID,
            GenerateCodexGhostTextAction::class.java,
            "Generate AI Ghost Text",
            "Generate an AI code proposal from the selected comment",
        )

        assertEquals(1, Regex("<statusBarWidgetFactory\\b").findAll(descriptor).count())
        assertTrue(descriptor.contains("group-id=\"ToolsMenu\""))
        assertTrue(descriptor.contains("group-id=\"EditorPopupMenu\""))
        assertFalse(descriptor.contains("keyboard-shortcut"))
    }

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
        assertTrue(ProviderActionFeedback.messageFor(claudeCheck).contains("lectura segura"))
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
                .contains("claude auth login"),
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

    private fun assertActionDescriptor(
        descriptor: String,
        actionId: String,
        actionClass: Class<*>,
        text: String,
        description: String,
    ) {
        assertTrue(descriptor.contains("id=\"$actionId\""))
        assertTrue(descriptor.contains("class=\"${actionClass.name}\""))
        assertTrue(descriptor.contains("text=\"$text\""))
        assertTrue(descriptor.contains("description=\"$description\""))
    }

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
