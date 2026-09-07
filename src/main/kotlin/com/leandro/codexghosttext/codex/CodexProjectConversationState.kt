package com.leandro.codexghosttext.codex

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** Stores only the opaque Codex chat id for this IntelliJ project. */
@Service(Service.Level.PROJECT)
@State(name = "CodexGhostTextProjectConversation", storages = [Storage("codexGhostText.xml")])
class CodexProjectConversationState : PersistentStateComponent<CodexProjectConversationState.Data> {
    data class Data(
        var projectRoot: String = "",
        var threadId: String = "",
    )

    private var data = Data()

    override fun getState(): Data = data

    override fun loadState(state: Data) {
        data = state
    }

    fun threadFor(projectRoot: String): String? =
        data.threadId.takeIf { it.isNotBlank() && data.projectRoot == projectRoot }

    fun remember(projectRoot: String, threadId: String) {
        data = Data(projectRoot, threadId)
    }

    fun forget() {
        data = Data()
    }
}
