package com.leandro.codexghosttext.claude

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.io.File

/** Stores only the opaque Claude session id for one canonical IntelliJ project root. */
@Service(Service.Level.PROJECT)
@State(name = "ClaudeGhostTextProjectConversation", storages = [Storage("claudeGhostText.xml")])
class ClaudeProjectConversationState : PersistentStateComponent<ClaudeProjectConversationState.Data> {
    data class Data(
        var projectRoot: String = "",
        var sessionId: String = "",
    )

    private var data = Data()

    override fun getState(): Data = data

    override fun loadState(state: Data) {
        data = state
    }

    fun sessionFor(projectRoot: String): String? =
        data.sessionId.takeIf { it.isNotBlank() && data.projectRoot == projectRoot.canonicalRoot() }

    fun remember(projectRoot: String, sessionId: String) {
        data = Data(projectRoot.canonicalRoot(), sessionId)
    }

    fun forget() {
        data = Data()
    }
}

private fun String.canonicalRoot(): String = runCatching { File(this).canonicalPath }.getOrDefault(this)
