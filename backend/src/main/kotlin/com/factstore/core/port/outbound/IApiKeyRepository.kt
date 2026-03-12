package com.factstore.core.port.outbound

import com.factstore.core.domain.ApiKey
import java.util.UUID

interface IApiKeyRepository {
    fun save(apiKey: ApiKey): ApiKey
    fun findById(id: UUID): ApiKey?
    fun findByKeyPrefix(keyPrefix: String): List<ApiKey>
    fun findByUserId(userId: UUID): List<ApiKey>
    fun existsById(id: UUID): Boolean
    fun deleteById(id: UUID)
}
