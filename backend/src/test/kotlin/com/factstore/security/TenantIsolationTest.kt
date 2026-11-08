package com.factstore.security

import com.factstore.application.ApiKeyRateLimiter
import com.factstore.application.ApiKeyService
import com.factstore.application.ApiKeyValidationCache
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.User
import com.factstore.core.domain.security.Permission
import com.factstore.core.port.inbound.IAttestationService
import com.factstore.core.port.inbound.IEvidenceVaultService
import com.factstore.core.port.inbound.IFlowService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.CreateApiKeyRequest
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * #155 FR-5.2, FR-5.3 and acceptance criterion 4 — a credential bound to organisation A sees only
 * A's resources, and another organisation's resource is a 404, not a 403, so probing cannot
 * enumerate it.
 */
@SpringBootTest(properties = ["security.enforce-auth=true"])
@AutoConfigureMockMvc
@Transactional
class TenantIsolationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var apiKeyService: ApiKeyService
    @Autowired lateinit var users: IUserRepository
    @Autowired lateinit var flows: IFlowService
    @Autowired lateinit var trails: ITrailService
    @Autowired lateinit var attestations: IAttestationService
    @Autowired lateinit var evidence: IEvidenceVaultService
    @Autowired lateinit var rateLimiter: ApiKeyRateLimiter
    @Autowired lateinit var validationCache: ApiKeyValidationCache

    private val orgA = "tenant-a-${System.nanoTime()}"
    private val orgB = "tenant-b-${System.nanoTime()}"

    private data class Tenant(val flowId: UUID, val trailId: UUID, val evidenceHash: String, val flowName: String)

    private lateinit var a: Tenant
    private lateinit var b: Tenant
    private lateinit var keyA: String
    private lateinit var unboundKey: String

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

    private fun seed(org: String): Tenant = asAdmin {
        val name = "flow-$org"
        val flow = flows.createFlow(CreateFlowRequest(name = name, orgSlug = org))
        val trail = trails.createTrail(
            CreateTrailRequest(flowId = flow.id, gitAuthor = "a", gitAuthorEmail = "a@example.com", orgSlug = org, gitCommitSha = "sha-$org", gitBranch = "main")
        )
        val attestation = attestations.recordAttestation(
            trail.id, CreateAttestationRequest(type = "junit", status = AttestationStatus.PASSED, orgSlug = org)
        )
        val file = evidence.store(attestation.id, "report-$org.txt", "text/plain", "evidence for $org".toByteArray())
        Tenant(flow.id, trail.id, file.sha256Hash, name)
    }

    private fun key(orgSlug: String?): String = asAdmin {
        val owner = users.save(User(email = "tenant-${System.nanoTime()}@example.com", name = "Tenant"))
        apiKeyService.createApiKey(
            CreateApiKeyRequest(
                ownerId = owner.id, label = "tenant", ownerType = OwnerType.USER, orgSlug = orgSlug,
                scopes = listOf("flows:read", "trails:read", "evidence:read")
            )
        ).plainTextKey
    }

    @BeforeEach
    fun setUp() {
        clearSecurity(); rateLimiter.reset(); validationCache.clear()
        a = seed(orgA)
        b = seed(orgB)
        keyA = key(orgA)
        unboundKey = key(null)
    }

    @AfterEach
    fun tearDown() {
        clearSecurity(); rateLimiter.reset(); validationCache.clear()
    }

    private fun getAs(key: String, path: String) =
        mockMvc.get(path) { header("Authorization", "Bearer $key") }

    @Test
    fun `another organisation's flow, trail and evidence are 404 to a key bound to A`() {
        getAs(keyA, "/api/v1/flows/${b.flowId}").andExpect { status { isNotFound() } }
        getAs(keyA, "/api/v1/trails/${b.trailId}").andExpect { status { isNotFound() } }
        getAs(keyA, "/api/v1/evidence/${b.evidenceHash}").andExpect { status { isNotFound() } }
    }

    @Test
    fun `the key's own organisation's resources are visible`() {
        getAs(keyA, "/api/v1/flows/${a.flowId}").andExpect { status { isOk() } }
        getAs(keyA, "/api/v1/trails/${a.trailId}").andExpect { status { isOk() } }
        getAs(keyA, "/api/v1/evidence/${a.evidenceHash}").andExpect { status { isOk() } }
    }

    @Test
    fun `lists show only the key's organisation`() {
        val flowsBody = getAs(keyA, "/api/v1/flows").andReturn().response.contentAsString
        assertThat(flowsBody).contains(a.flowId.toString()).doesNotContain(b.flowId.toString())

        val trailsBody = getAs(keyA, "/api/v1/trails").andReturn().response.contentAsString
        assertThat(trailsBody).contains(a.trailId.toString()).doesNotContain(b.trailId.toString())
    }

    @Test
    fun `search shows only the key's organisation`() {
        val body = mockMvc.post("/api/v1/search") {
            header("Authorization", "Bearer $keyA")
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"flow-tenant"}"""
        }.andReturn().response.contentAsString

        assertThat(body).contains(a.flowName).doesNotContain(b.flowName)

        val simple = getAs(keyA, "/api/v1/search?q=sha-tenant").andReturn().response.contentAsString
        assertThat(simple).contains(a.trailId.toString()).doesNotContain(b.trailId.toString())
    }

    @Test
    fun `dashboard counts cover only the key's organisation`() {
        getAs(keyA, "/api/v1/dashboard/stats").andExpect {
            status { isOk() }
            jsonPath("$.totalFlows") { value(1) }
            jsonPath("$.totalTrails") { value(1) }
        }
    }

    @Test
    fun `the v2 query API is scoped the same way`() {
        getAs(keyA, "/api/v2/flows/${b.flowId}").andExpect { status { isNotFound() } }
        getAs(keyA, "/api/v2/trails/${b.trailId}").andExpect { status { isNotFound() } }
        val flowsBody = getAs(keyA, "/api/v2/flows").andReturn().response.contentAsString
        assertThat(flowsBody).contains(a.flowId.toString()).doesNotContain(b.flowId.toString())
        val trailsBody = getAs(keyA, "/api/v2/trails").andReturn().response.contentAsString
        assertThat(trailsBody).contains(a.trailId.toString()).doesNotContain(b.trailId.toString())
    }

    @Test
    fun `an unbound credential still sees every organisation, as before`() {
        getAs(unboundKey, "/api/v1/flows/${b.flowId}").andExpect { status { isOk() } }
        val body = getAs(unboundKey, "/api/v1/flows").andReturn().response.contentAsString
        assertThat(body).contains(a.flowId.toString()).contains(b.flowId.toString())
    }
}
