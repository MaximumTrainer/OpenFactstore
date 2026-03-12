package com.factstore.application

import com.factstore.core.domain.ApiKey
import com.factstore.core.domain.ApiKeyType
import com.factstore.core.port.inbound.IApiKeyService
import com.factstore.core.port.outbound.IApiKeyRepository
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.ApiKeyCreatedResponse
import com.factstore.dto.ApiKeyResponse
import com.factstore.dto.CreateApiKeyRequest
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class ApiKeyService(
    private val apiKeyRepository: IApiKeyRepository,
    private val userRepository: IUserRepository,
    private val passwordEncoder: BCryptPasswordEncoder
) : IApiKeyService {

    private val log = LoggerFactory.getLogger(ApiKeyService::class.java)
    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically secure API key, hashes it with BCrypt, persists the
     * hash, and returns the plain-text key exactly once to the caller.
     *
     * Key format: `fsp_<64 hex chars>` (personal) or `fss_<64 hex chars>` (service).
     * The prefix stored in the database is the first 12 characters of the full key,
     * which is used for an efficient indexed lookup before the BCrypt comparison.
     */
    override fun createApiKey(request: CreateApiKeyRequest): ApiKeyCreatedResponse {
        if (!userRepository.existsById(request.userId)) {
            throw NotFoundException("User not found: ${request.userId}")
        }

        val randomBytes = ByteArray(32)
        secureRandom.nextBytes(randomBytes)
        val randomHex = randomBytes.joinToString("") { "%02x".format(it) }

        val typePrefix = when (request.type) {
            ApiKeyType.PERSONAL -> "fsp"
            ApiKeyType.SERVICE -> "fss"
        }
        val plainTextKey = "${typePrefix}_$randomHex"
        val keyPrefix = plainTextKey.take(12)
        val hashedKey = passwordEncoder.encode(plainTextKey)

        val apiKey = ApiKey(
            userId = request.userId,
            type = request.type,
            name = request.name,
            keyPrefix = keyPrefix,
            hashedKey = hashedKey
        )
        val saved = apiKeyRepository.save(apiKey)
        log.info("Created API key: ${saved.id} type=${saved.type} prefix=${saved.keyPrefix}")

        return ApiKeyCreatedResponse(
            id = saved.id,
            userId = saved.userId,
            name = saved.name,
            type = saved.type,
            keyPrefix = saved.keyPrefix,
            isActive = saved.isActive,
            createdAt = saved.createdAt,
            plainTextKey = plainTextKey
        )
    }

    @Transactional(readOnly = true)
    override fun listApiKeysForUser(userId: UUID): List<ApiKeyResponse> {
        if (!userRepository.existsById(userId)) throw NotFoundException("User not found: $userId")
        return apiKeyRepository.findByUserId(userId).map { it.toResponse() }
    }

    override fun revokeApiKey(id: UUID) {
        val key = apiKeyRepository.findById(id) ?: throw NotFoundException("API key not found: $id")
        key.isActive = false
        apiKeyRepository.save(key)
        log.info("Revoked API key: $id")
    }

    /**
     * Validates a raw key from an incoming request:
     * 1. Extracts the prefix (first 12 chars) for indexed database lookup.
     * 2. Iterates matching active keys and verifies the BCrypt hash.
     * 3. Updates [ApiKey.lastUsedAt] on success.
     */
    override fun validateApiKey(rawKey: String): ApiKeyResponse? {
        if (rawKey.length < 12) return null
        val prefix = rawKey.take(12)
        val candidates = apiKeyRepository.findByKeyPrefix(prefix).filter { it.isActive }
        for (candidate in candidates) {
            if (passwordEncoder.matches(rawKey, candidate.hashedKey)) {
                candidate.lastUsedAt = Instant.now()
                apiKeyRepository.save(candidate)
                return candidate.toResponse()
            }
        }
        return null
    }
}

fun ApiKey.toResponse() = ApiKeyResponse(
    id = id,
    userId = userId,
    name = name,
    type = type,
    keyPrefix = keyPrefix,
    isActive = isActive,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt
)
