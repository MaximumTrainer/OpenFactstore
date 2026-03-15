package com.factstore.adapter.outbound.secrets

import com.factstore.core.port.outbound.ISecretStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
@ConditionalOnProperty(name = ["vault.enabled"], havingValue = "false", matchIfMissing = true)
class InMemorySecretStore : ISecretStore {
    private val store = ConcurrentHashMap<String, String>()
    override fun get(path: String): String? = store[path]
    override fun put(path: String, value: String) { store[path] = value }
    override fun delete(path: String) { store.remove(path) }
}
