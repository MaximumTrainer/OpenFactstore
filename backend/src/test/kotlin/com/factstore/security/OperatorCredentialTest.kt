package com.factstore.security

import com.factstore.application.ApiKeyRateLimiter
import com.factstore.application.ApiKeyService
import com.factstore.application.ApiKeyValidationCache
import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.User
import com.factstore.core.domain.security.Permission
import com.factstore.core.port.inbound.IFlowService
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.CreateApiKeyRequest
import com.factstore.dto.CreateFlowRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

/**
 * #207 (#155 FR-5.4) — once `security.tenancy.unbound-requires-operator=true`, reading across
 * organisations takes an explicit `operator` credential; an unbound key without it sees only
 * resources that belong to no organisation.
 */
@SpringBootTest(properties = ["security.enforce-auth=true", "security.tenancy.unbound-requires-operator=true"])
@AutoConfigureMockMvc
@Transactional
class OperatorCredentialTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var apiKeyService: ApiKeyService
    @Autowired lateinit var users: IUserRepository
    @Autowired lateinit var flows: IFlowService
    @Autowired lateinit var rateLimiter: ApiKeyRateLimiter
    @Autowired lateinit var validationCache: ApiKeyValidationCache

    private fun clearSecurity() {
        SecurityContextHolder.clearContext()
        TestSecurityContextHolder.clearContext()
    }

    private fun <T> asAdmin(block: () -> T): T {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "admin", null, listOf(SimpleGrantedAuthority(Permission.ADMIN.authority))
        )
        return try { block() } finally { clearSecurity() }
    }

    private fun key(vararg scopes: String): String = asAdmin {
        val owner = users.save(User(email = "op-${System.nanoTime()}@example.com", name = "Op"))
        apiKeyService.createApiKey(
            CreateApiKeyRequest(ownerId = owner.id, label = "op", ownerType = OwnerType.USER, scopes = scopes.toList())
        ).plainTextKey
    }

    private val org = "op-org-${System.nanoTime()}"
    private lateinit var orgFlowId: String
    private lateinit var sharedFlowId: String

    @BeforeEach
    fun setUp() {
        clearSecurity(); rateLimiter.reset(); validationCache.clear()
        orgFlowId = asAdmin { flows.createFlow(CreateFlowRequest(name = "op-owned-${System.nanoTime()}", orgSlug = org)).id.toString() }
        sharedFlowId = asAdmin { flows.createFlow(CreateFlowRequest(name = "op-shared-${System.nanoTime()}")).id.toString() }
    }

    @AfterEach
    fun tearDown() { clearSecurity(); rateLimiter.reset(); validationCache.clear() }

    private fun get(key: String, path: String) = mockMvc.get(path) { header("Authorization", "Bearer $key") }.andReturn().response

    @Test
    fun `an unbound key without operator sees only resources with no organisation`() {
        val plain = key("flows:read")

        assertThat(get(plain, "/api/v1/flows/$orgFlowId").status).isEqualTo(404)
        assertThat(get(plain, "/api/v1/flows/$sharedFlowId").status).isEqualTo(200)
        assertThat(get(plain, "/api/v1/flows").contentAsString).doesNotContain(orgFlowId).contains(sharedFlowId)
    }

    @Test
    fun `an unbound key with operator reads across organisations`() {
        val operator = key("flows:read", "operator")

        assertThat(get(operator, "/api/v1/flows/$orgFlowId").status).isEqualTo(200)
    }
}
