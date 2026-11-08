package com.factstore.security

import com.factstore.application.ApiKeyRateLimiter
import com.factstore.application.ApiKeyService
import com.factstore.application.ApiKeyValidationCache
import com.factstore.dto.ApiKeyPreset
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

/**
 * #206 (#155 FR-3, FR-4) — reads need the read scope for their resource, not just any credential.
 *
 * A CI key still does everything the CLI and the setup-factstore action read today, because the
 * CI-pipeline preset now carries `flows:read` and `trails:read`; a key scoped to writing
 * attestations no longer reads the audit log.
 */
@SpringBootTest(properties = ["security.enforce-auth=true"])
@AutoConfigureMockMvc
@Transactional
class ReadScopesTest {

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
    fun setUp() { clearSecurity(); rateLimiter.reset(); validationCache.clear() }

    @AfterEach
    fun tearDown() { clearSecurity(); rateLimiter.reset(); validationCache.clear() }

    private fun key(preset: ApiKeyPreset? = null, scopes: List<String>? = null): String {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "admin", null, listOf(SimpleGrantedAuthority(Permission.ADMIN.authority))
        )
        try {
            val owner = users.save(User(email = "reads-${System.nanoTime()}@example.com", name = "Reads"))
            return apiKeyService.createApiKey(
                CreateApiKeyRequest(ownerId = owner.id, label = "reads", ownerType = OwnerType.USER, preset = preset, scopes = scopes)
            ).plainTextKey
        } finally {
            clearSecurity()
        }
    }

    private fun status(key: String, path: String): Int =
        mockMvc.get(path) { header("Authorization", "Bearer $key") }.andReturn().response.status

    @Test
    fun `a CI pipeline key can make the reads the CLI and action make`() {
        val ci = key(preset = ApiKeyPreset.CI_PIPELINE)

        listOf("/api/v2/flows", "/api/v1/flows", "/api/v1/gate/results", "/api/v1/environments", "/api/v2/trails")
            .forEach { path -> org.assertj.core.api.Assertions.assertThat(status(ci, path)).describedAs(path).isEqualTo(200) }
    }

    @Test
    fun `a key that can only write attestations cannot read the audit log or flows`() {
        val writer = key(scopes = listOf("attestations:write"))

        org.assertj.core.api.Assertions.assertThat(status(writer, "/api/v1/audit")).isEqualTo(403)
        org.assertj.core.api.Assertions.assertThat(status(writer, "/api/v1/flows")).isEqualTo(403)
    }

    @Test
    fun `evidence needs evidence read, not just trails read`() {
        val trailsOnly = key(scopes = listOf("trails:read"))
        val evidenceReader = key(scopes = listOf("evidence:read"))
        val hash = "0".repeat(64)

        org.assertj.core.api.Assertions.assertThat(status(trailsOnly, "/api/v1/evidence/$hash")).isEqualTo(403)
        // Passes the rule; there is no such file, so 404.
        org.assertj.core.api.Assertions.assertThat(status(evidenceReader, "/api/v1/evidence/$hash")).isEqualTo(404)
    }

    @Test
    fun `policies need policies read`() {
        org.assertj.core.api.Assertions.assertThat(status(key(scopes = listOf("trails:read")), "/api/v1/policies")).isEqualTo(403)
        org.assertj.core.api.Assertions.assertThat(status(key(scopes = listOf("policies:read")), "/api/v1/policies")).isEqualTo(200)
    }

    @Test
    fun `the audit log is readable with trails read, as a viewer can`() {
        org.assertj.core.api.Assertions.assertThat(status(key(scopes = listOf("trails:read")), "/api/v1/audit")).isEqualTo(200)
    }

    @Test
    fun `service account listings need admin`() {
        org.assertj.core.api.Assertions.assertThat(status(key(scopes = listOf("flows:read", "trails:read")), "/api/v1/service-accounts")).isEqualTo(403)
    }
}
