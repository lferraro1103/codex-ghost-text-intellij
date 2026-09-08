package com.leandro.codexghosttext.codex

/**
 * Small, side-effect-free policy for deciding whether to reuse, resume, or create a conversation.
 * Keeping it separate makes the App Server sequence testable without touching the user's history.
 */
internal class CodexConversationReusePolicy {
    private var active: ActiveConversation? = null

    fun liveThreadFor(projectRoot: String, sessionIsLive: Boolean): String? =
        active?.takeIf { it.projectRoot == projectRoot && sessionIsLive }?.threadId

    fun establish(
        projectRoot: String,
        persistedThreadId: String?,
        resume: (String) -> String?,
        start: () -> String?,
    ): String? {
        // A saved project chat is authoritative. Never silently replace it with a new chat:
        // doing so fragments the user's provider history and loses the project context.
        // The explicit Reset action is the only route allowed to create a replacement.
        val savedThreadId = persistedThreadId
        val threadId = (if (savedThreadId != null) resume(savedThreadId) else start()) ?: return null
        active = ActiveConversation(projectRoot, threadId)
        return threadId
    }

    fun clearActive() {
        active = null
    }

    private data class ActiveConversation(val projectRoot: String, val threadId: String)
}
