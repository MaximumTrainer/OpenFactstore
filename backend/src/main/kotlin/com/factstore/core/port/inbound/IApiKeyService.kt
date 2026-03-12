package com.factstore.core.port.inbound

import com.factstore.dto.ApiKeyCreatedResponse
import com.factstore.dto.ApiKeyResponse
import com.factstore.dto.CreateApiKeyRequest
import java.util.UUID

interface IApiKeyService {
    /** Generates a new API key, hashes it, stores it, and returns the plain-text key once. */
    fun createApiKey(request: CreateApiKeyRequest): ApiKeyCreatedResponse
    fun listApiKeysForUser(userId: UUID): List<ApiKeyResponse>
    fun revokeApiKey(id: UUID)
    /**
     * Validates an incoming raw API key against stored hashed keys.
     * Returns the matching [ApiKeyResponse] (and updates lastUsedAt) or null if invalid.
     */
    fun validateApiKey(rawKey: String): ApiKeyResponse?
}
