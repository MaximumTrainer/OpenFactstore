package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.ApiKey
import com.factstore.core.port.outbound.IApiKeyRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ApiKeyRepositoryJpa : JpaRepository<ApiKey, UUID> {
    fun findByKeyPrefix(keyPrefix: String): List<ApiKey>
    fun findByUserId(userId: UUID): List<ApiKey>
}

@Component
class ApiKeyRepositoryAdapter(private val jpa: ApiKeyRepositoryJpa) : IApiKeyRepository {
    override fun save(apiKey: ApiKey): ApiKey = jpa.save(apiKey)
    override fun findById(id: UUID): ApiKey? = jpa.findById(id).orElse(null)
    override fun findByKeyPrefix(keyPrefix: String): List<ApiKey> = jpa.findByKeyPrefix(keyPrefix)
    override fun findByUserId(userId: UUID): List<ApiKey> = jpa.findByUserId(userId)
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
    override fun deleteById(id: UUID) = jpa.deleteById(id)
}
