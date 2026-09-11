package com.leandro.codexghosttext.context

import com.intellij.openapi.components.Service
import com.leandro.codexghosttext.generation.ProviderId
import java.util.Collections

/**
 * What each provider's project conversation has already been told.
 *
 * Both providers reuse one conversation per project, so a declaration sent in an earlier request is
 * still in that conversation's history: resending it would pay for the same tokens on every
 * generation. Codex could also push context outside a turn with `thread/inject_items`, but Claude
 * has no equivalent, so both use the same rule — send a skeleton the first time and rely on the
 * resumed conversation afterwards.
 *
 * Only a hash of each skeleton is kept, and it is dropped when the conversation is reset, when the
 * project closes, or when a request that carried it fails.
 */
@Service(Service.Level.PROJECT)
class ConversationContextMemory {
    private val sent: MutableMap<ProviderId, MutableSet<Int>> = Collections.synchronizedMap(mutableMapOf())

    /** The skeletons this provider's conversation has not seen yet. */
    fun unsent(providerId: ProviderId, skeletons: List<String>): List<String> {
        val seen = seen(providerId)
        return synchronized(seen) { skeletons.filterNot { it.hashCode() in seen } }
    }

    /** Called after a proposal arrives: a failed request may not have reached the model at all. */
    fun remember(providerId: ProviderId, skeletons: List<String>) {
        val seen = seen(providerId)
        synchronized(seen) { skeletons.forEach { seen += it.hashCode() } }
    }

    fun forget(providerId: ProviderId) {
        val seen = seen(providerId)
        synchronized(seen) { seen.clear() }
    }

    private fun seen(providerId: ProviderId): MutableSet<Int> = sent.getOrPut(providerId) { mutableSetOf() }
}
