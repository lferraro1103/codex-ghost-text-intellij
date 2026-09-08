package com.leandro.codexghosttext.provider

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.leandro.codexghosttext.generation.ProviderId

/** Stores only the user's provider choice for this IntelliJ project, never a session identifier. */
@Service(Service.Level.PROJECT)
@State(name = "CodexGhostTextProvider", storages = [Storage("codexGhostTextProvider.xml")])
class ProviderProjectState : PersistentStateComponent<ProviderProjectState.Data> {
    data class Data(var selectedProvider: String = ProviderId.CODEX.name)

    private var data = Data()

    override fun getState(): Data = data

    override fun loadState(state: Data) {
        data = Data(parse(state.selectedProvider).name)
    }

    fun selectedProvider(): ProviderId = parse(data.selectedProvider)

    fun select(providerId: ProviderId) {
        data = Data(providerId.name)
    }

    private fun parse(raw: String?): ProviderId =
        raw?.let { value -> ProviderId.entries.firstOrNull { it.name == value } } ?: ProviderId.CODEX
}
