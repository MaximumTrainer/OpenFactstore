package com.factstore

import com.factstore.application.ApiKeyService
import com.factstore.application.UserService
import com.factstore.core.domain.ApiKeyType
import com.factstore.dto.CreateApiKeyRequest
import com.factstore.dto.CreateUserRequest
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class ApiKeyServiceTest {

    @Autowired
    lateinit var apiKeyService: ApiKeyService

    @Autowired
    lateinit var userService: UserService

    private fun createUser(email: String = "apitest-${System.nanoTime()}@example.com"): UUID {
        return userService.createUser(CreateUserRequest(email = email, name = "API Test User")).id
    }

    @Test
    fun `create personal API key returns plain text key once`() {
        val userId = createUser()
        val resp = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "CI Key", ApiKeyType.PERSONAL))

        assertTrue(resp.plainTextKey.startsWith("fsp_"), "Personal key should start with 'fsp_'")
        assertEquals(68, resp.plainTextKey.length, "Key should be 'fsp_' + 64 hex chars")
        assertEquals(ApiKeyType.PERSONAL, resp.type)
        assertTrue(resp.isActive)
        assertEquals(userId, resp.userId)
    }

    @Test
    fun `create service account API key has correct prefix`() {
        val userId = createUser()
        val resp = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "Deploy Bot", ApiKeyType.SERVICE))

        assertTrue(resp.plainTextKey.startsWith("fss_"), "Service key should start with 'fss_'")
        assertEquals(ApiKeyType.SERVICE, resp.type)
    }

    @Test
    fun `create API key for unknown user throws NotFoundException`() {
        assertThrows<NotFoundException> {
            apiKeyService.createApiKey(CreateApiKeyRequest(UUID.randomUUID(), "Key", ApiKeyType.PERSONAL))
        }
    }

    @Test
    fun `plain text key prefix stored in database`() {
        val userId = createUser()
        val resp = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "Prefix Test", ApiKeyType.PERSONAL))

        val stored = apiKeyService.listApiKeysForUser(userId).first()
        assertEquals(resp.plainTextKey.take(12), stored.keyPrefix)
    }

    @Test
    fun `validateApiKey returns key response for valid key`() {
        val userId = createUser()
        val created = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "Valid", ApiKeyType.PERSONAL))

        val validated = apiKeyService.validateApiKey(created.plainTextKey)
        assertNotNull(validated)
        assertEquals(created.id, validated!!.id)
        assertEquals(userId, validated.userId)
    }

    @Test
    fun `validateApiKey returns null for wrong key`() {
        val result = apiKeyService.validateApiKey("fsp_wrongkeyxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
        assertNull(result)
    }

    @Test
    fun `validateApiKey returns null for too-short key`() {
        val result = apiKeyService.validateApiKey("short")
        assertNull(result)
    }

    @Test
    fun `revokeApiKey deactivates the key`() {
        val userId = createUser()
        val created = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "To Revoke", ApiKeyType.PERSONAL))

        apiKeyService.revokeApiKey(created.id)

        // After revocation, the key must no longer validate
        val validated = apiKeyService.validateApiKey(created.plainTextKey)
        assertNull(validated, "Revoked key should not validate")
    }

    @Test
    fun `revoke non-existent key throws NotFoundException`() {
        assertThrows<NotFoundException> {
            apiKeyService.revokeApiKey(UUID.randomUUID())
        }
    }

    @Test
    fun `listApiKeysForUser returns keys for user`() {
        val userId = createUser()
        apiKeyService.createApiKey(CreateApiKeyRequest(userId, "Key A", ApiKeyType.PERSONAL))
        apiKeyService.createApiKey(CreateApiKeyRequest(userId, "Key B", ApiKeyType.SERVICE))

        val keys = apiKeyService.listApiKeysForUser(userId)
        assertEquals(2, keys.size)
        assertTrue(keys.any { it.name == "Key A" })
        assertTrue(keys.any { it.name == "Key B" })
    }

    @Test
    fun `listApiKeysForUser throws NotFoundException for unknown user`() {
        assertThrows<NotFoundException> {
            apiKeyService.listApiKeysForUser(UUID.randomUUID())
        }
    }

    @Test
    fun `each generated key is unique`() {
        val userId = createUser()
        val key1 = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "K1", ApiKeyType.PERSONAL)).plainTextKey
        val key2 = apiKeyService.createApiKey(CreateApiKeyRequest(userId, "K2", ApiKeyType.PERSONAL)).plainTextKey
        assertNotEquals(key1, key2)
    }
}
