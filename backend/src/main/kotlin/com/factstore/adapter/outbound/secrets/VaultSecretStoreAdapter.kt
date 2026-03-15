package com.factstore.adapter.outbound.secrets

import com.factstore.core.port.outbound.ISecretStore
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.vault.core.VaultTemplate
import org.springframework.vault.support.VaultResponse

@Component
@ConditionalOnProperty(name = ["vault.enabled"], havingValue = "true")
class VaultSecretStoreAdapter(private val vaultTemplate: VaultTemplate) : ISecretStore {
    private val log = LoggerFactory.getLogger(VaultSecretStoreAdapter::class.java)

    override fun get(path: String): String? {
        return try {
            val response: VaultResponse? = vaultTemplate.read("secret/data/$path")
            @Suppress("UNCHECKED_CAST")
            val data = response?.data?.get("data") as? Map<String, String>
            data?.get("value")
        } catch (e: Exception) {
            log.warn("Failed to read secret at path {}: {}", path, e.message)
            null
        }
    }

    override fun put(path: String, value: String) {
        try {
            vaultTemplate.write("secret/data/$path", mapOf("data" to mapOf("value" to value)))
        } catch (e: Exception) {
            log.error("Failed to write secret at path {}: {}", path, e.message)
            throw e
        }
    }

    override fun delete(path: String) {
        try {
            vaultTemplate.delete("secret/data/$path")
        } catch (e: Exception) {
            log.warn("Failed to delete secret at path {}: {}", path, e.message)
        }
    }
}
