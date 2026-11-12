package com.factstore.application.gates

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Called whenever this instance's read model changes, so cached read results are dropped (#194). */
fun interface ReadModelChangeNotifier {
    fun changed()
}

/**
 * Cached gate decisions (#194). Deployment gates are read far more often than evidence is
 * written, so a decision is reused until the read model changes (any entity insert, update or
 * delete clears the cache, see [ReadModelChangeListener]) or `factstore.gates.cache-ttl-seconds` (default 30) passes. Only decisions
 * made from the evidence are cached; a risk-tier fallback (#193) is always recomputed.
 *
 * The cache is in-process, so each query instance (and each region, #192) keeps its own, cleared
 * by the events it projects.
 */
@Component
class GateDecisionCache(
    @Value("\${factstore.gates.cache-ttl-seconds:30}") private val ttlSeconds: Long
) : ReadModelChangeNotifier {

    data class Key(val artifactSha256: String, val environment: String, val flowId: UUID, val requiredPolicies: List<String>)

    private data class Entry(val decision: GateDecision, val expiresAt: Instant)

    private val entries = ConcurrentHashMap<Key, Entry>()

    init {
        ReadModelChangeListener.subscribe(this)
    }

    @jakarta.annotation.PreDestroy
    fun close() = ReadModelChangeListener.unsubscribe(this)

    fun get(key: Key, now: Instant): GateDecision? {
        val entry = entries[key] ?: return null
        if (!now.isBefore(entry.expiresAt)) {
            entries.remove(key, entry)
            return null
        }
        return entry.decision
    }

    fun put(key: Key, decision: GateDecision, now: Instant) {
        if (ttlSeconds <= 0) return
        if (entries.size >= MAX_ENTRIES) entries.clear()
        entries[key] = Entry(decision, now.plusSeconds(ttlSeconds))
    }

    override fun changed() = entries.clear()

    companion object {
        private const val MAX_ENTRIES = 10_000
    }
}
