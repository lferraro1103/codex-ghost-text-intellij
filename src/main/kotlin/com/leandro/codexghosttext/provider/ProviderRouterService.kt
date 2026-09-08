package com.leandro.codexghosttext.provider

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.leandro.codexghosttext.claude.ClaudeGenerationService
import com.leandro.codexghosttext.codex.CodexGenerationProvider
import com.leandro.codexghosttext.generation.GenerationRequest
import com.leandro.codexghosttext.generation.GenerationResult
import com.leandro.codexghosttext.generation.LocalGenerationProvider
import com.leandro.codexghosttext.generation.ProviderDiagnostic
import com.leandro.codexghosttext.generation.ProviderId
import com.leandro.codexghosttext.preview.GhostPreviewService

/** Immutable routing token; it intentionally carries no provider conversation or session identifier. */
data class ProviderSelectionSnapshot(
    val providerId: ProviderId,
    val provider: LocalGenerationProvider,
    val epoch: Long,
)

fun interface ProviderSelectionListener {
    fun selectionChanged(snapshot: ProviderSelectionSnapshot)
}

/**
 * The single manual provider selection authority for one IntelliJ project.
 *
 * A provider is never substituted: missing or unavailable selected providers return their own
 * diagnostic. Switching first stops old work and removes the preview, then makes the new choice
 * visible to listeners with a new epoch so stale background results can be discarded.
 */
@Service(Service.Level.PROJECT)
class ProviderRouterService private constructor(
    private val state: ProviderProjectState,
    providers: Map<ProviderId, LocalGenerationProvider>,
    private val cancelPreview: () -> Unit,
    private val notifySelection: (ProviderSelectionSnapshot) -> Unit,
) {
    private val providers: Map<ProviderId, LocalGenerationProvider> = providers.toMap().also { registered ->
        require(registered.keys == ProviderId.entries.toSet()) { "Every supported provider must be registered explicitly." }
        require(registered.all { (id, provider) -> id == provider.providerId }) { "Provider registration must match its id." }
    }
    private val lock = Any()
    private var epoch = 0L

    constructor(project: Project) : this(
        state = project.getService(ProviderProjectState::class.java),
        providers = linkedMapOf(
            ProviderId.CODEX to project.getService(CodexGenerationProvider::class.java),
            ProviderId.CLAUDE to project.getService(ClaudeGenerationService::class.java),
        ),
        cancelPreview = { project.getService(GhostPreviewService::class.java).cancel() },
        notifySelection = { snapshot ->
            project.messageBus.syncPublisher(SELECTION_TOPIC).selectionChanged(snapshot)
        },
    )

    internal constructor(
        state: ProviderProjectState,
        providers: Map<ProviderId, LocalGenerationProvider>,
        cancelPreview: () -> Unit,
        notifySelection: (ProviderSelectionSnapshot) -> Unit,
        @Suppress("UNUSED_PARAMETER") testOnly: Boolean = true,
    ) : this(state, providers, cancelPreview, notifySelection)

    fun snapshot(): ProviderSelectionSnapshot = synchronized(lock) { currentSnapshot() }

    fun isCurrent(snapshot: ProviderSelectionSnapshot): Boolean = synchronized(lock) {
        snapshot.epoch == epoch && snapshot.providerId == state.selectedProvider() && snapshot.provider === providerFor(snapshot.providerId)
    }

    fun checkAvailability(): ProviderDiagnostic = snapshot().provider.checkAvailability()

    fun generate(request: GenerationRequest, snapshot: ProviderSelectionSnapshot = snapshot()): GenerationResult =
        snapshot.provider.generate(request)

    fun cancelSelectedGeneration() {
        snapshot().provider.cancel()
    }

    fun resetSelectedConversation() {
        snapshot().provider.resetConversation()
    }

    /** @return true when a different provider was actually selected. */
    fun select(providerId: ProviderId): Boolean {
        val changed = synchronized(lock) {
            if (state.selectedProvider() == providerId) return false
            providerFor(state.selectedProvider()).cancel()
            cancelPreview()
            epoch += 1
            state.select(providerId)
            currentSnapshot()
        }
        notifySelection(changed)
        return true
    }

    private fun currentSnapshot(): ProviderSelectionSnapshot {
        val providerId = state.selectedProvider()
        return ProviderSelectionSnapshot(providerId, providerFor(providerId), epoch)
    }

    /** No fallback branch: registrations are closed and an invalid map is rejected at construction. */
    private fun providerFor(providerId: ProviderId): LocalGenerationProvider =
        requireNotNull(providers[providerId]) { "No provider registered for $providerId." }

    companion object {
        val SELECTION_TOPIC: Topic<ProviderSelectionListener> = Topic.create(
            "Codex Ghost Text provider selection",
            ProviderSelectionListener::class.java,
        )
    }
}
