package com.factstore.security

import com.factstore.application.ApiKeyRateLimiter
import com.factstore.application.ApiKeyService
import com.factstore.application.ApiKeyValidationCache
import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.User
import com.factstore.core.domain.security.Permission
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.CreateApiKeyRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * #155 FR-4 — endpoints that had no authorisation rule now carry one, and a scoped key is held to
 * it, while an instance that has not turned enforcement on keeps working for anonymous callers.
 *
 * Runs with the shipped default, `security.enforce-auth=false`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NewlyGatedEndpointsTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var apiKeyService: ApiKeyService
    @Autowired lateinit var users: IUserRepository
    @Autowired lateinit var rateLimiter: ApiKeyRateLimiter
    @Autowired lateinit var validationCache: ApiKeyValidationCache

    private fun clearSecurity() {
        SecurityContextHolder.clearContext()
        TestSecurityContextHolder.clearContext()
    }

    @BeforeEach
    fun setUp() {
        clearSecurity(); rateLimiter.reset(); validationCache.clear()
    }

    @AfterEach
    fun tearDown() {
        clearSecurity(); rateLimiter.reset(); validationCache.clear()
    }

    private fun ciKey(vararg scopes: String): String {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "admin", null, listOf(SimpleGrantedAuthority(Permission.ADMIN.authority))
        )
        try {
            val owner = users.save(User(email = "ci-${System.nanoTime()}@example.com", name = "CI"))
            return apiKeyService.createApiKey(
                CreateApiKeyRequest(ownerId = owner.id, label = "ci", ownerType = OwnerType.USER, scopes = scopes.toList())
            ).plainTextKey
        } finally {
            clearSecurity()
        }
    }

    @Test
    fun `a CI key with only attestations write cannot delete a trail`() {
        val key = ciKey("attestations:write")

        mockMvc.delete("/api/v1/trails/${UUID.randomUUID()}") { header("Authorization", "Bearer $key") }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `a CI key with only attestations write cannot change notification rules or secrets`() {
        val key = ciKey("attestations:write")

        mockMvc.post("/api/v1/notification-rules") {
            header("Authorization", "Bearer $key")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"x","triggerEvent":"GATE_BLOCKED","channelType":"IN_APP"}"""
        }.andExpect { status { isForbidden() } }
        mockMvc.delete("/api/v1/secrets/somepath") { header("Authorization", "Bearer $key") }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `a key carrying the scope passes the rule`() {
        val key = ciKey("trails:write")

        // Passes the authorisation rule; the unknown trail id then makes it a 404, not a 403.
        mockMvc.post("/api/v1/trails/${UUID.randomUUID()}/archive") { header("Authorization", "Bearer $key") }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `with enforcement off an anonymous caller is unaffected by the new rules`() {
        mockMvc.post("/api/v1/trails/${UUID.randomUUID()}/archive")
            .andExpect { status { isNotFound() } }
    }
}
