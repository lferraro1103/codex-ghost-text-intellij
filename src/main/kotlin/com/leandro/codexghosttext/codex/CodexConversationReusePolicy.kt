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
        val threadId = persistedThreadId?.let(resume) ?: start() ?: return null
        active = ActiveConversation(projectRoot, threadId)
        return threadId
    }

    fun clearActive() {
        active = null
    }

    private data class ActiveConversation(val projectRoot: String, val threadId: String)
}
