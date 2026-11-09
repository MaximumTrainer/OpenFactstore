package com.factstore.security

import com.factstore.application.ApiKeyRateLimiter
import com.factstore.application.ApiKeyService
import com.factstore.application.ApiKeyValidationCache
import com.factstore.core.domain.AssessmentStatus
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.ComplianceAssessment
import com.factstore.core.domain.DeploymentGateResult
import com.factstore.core.domain.EnvironmentType
import com.factstore.core.domain.GateDecision
import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.User
import com.factstore.core.domain.security.Permission
import com.factstore.core.port.inbound.IApprovalService
import com.factstore.core.port.inbound.IArtifactService
import com.factstore.core.port.inbound.IAttestationService
import com.factstore.core.port.inbound.IEnvironmentService
import com.factstore.core.port.inbound.IFlowService
import com.factstore.core.port.inbound.IPolicyService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.core.port.outbound.IAuditEventRepository
import com.factstore.core.port.outbound.IComplianceAssessmentRepository
import com.factstore.core.port.outbound.IDeploymentGateResultRepository
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.CreateApiKeyRequest
import com.factstore.dto.CreateApprovalRequest
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreatePolicyRequest
import com.factstore.dto.CreateTrailRequest
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
import java.util.UUID

/**
 * #207 (#155 FR-5.3, FR-5.4) — the surfaces #204 did not cover are scoped to the caller's
 * organisation too, and a cross-organisation read by an unbound credential is audited.
 */
@SpringBootTest(properties = ["security.enforce-auth=true"])
@AutoConfigureMockMvc
@Transactional
class TenantIsolationRemainingSurfacesTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var apiKeyService: ApiKeyService
    @Autowired lateinit var users: IUserRepository
    @Autowired lateinit var flows: IFlowService
    @Autowired lateinit var trails: ITrailService
    @Autowired lateinit var attestations: IAttestationService
    @Autowired lateinit var environments: IEnvironmentService
    @Autowired lateinit var policies: IPolicyService
    @Autowired lateinit var artifacts: IArtifactService
    @Autowired lateinit var approvals: IApprovalService
    @Autowired lateinit var gateResults: IDeploymentGateResultRepository
    @Autowired lateinit var assessments: IComplianceAssessmentRepository
    @Autowired lateinit var auditEvents: IAuditEventRepository
    @Autowired lateinit var rateLimiter: ApiKeyRateLimiter
    @Autowired lateinit var validationCache: ApiKeyValidationCache

    private val orgA = "rest-a-${System.nanoTime()}"
    private val orgB = "rest-b-${System.nanoTime()}"

    private data class Tenant(
        val trailId: UUID, val environmentId: UUID, val policyId: UUID, val digest: String,
        val approvalId: UUID, val gateResultId: UUID, val assessmentId: UUID
    )

    private lateinit var a: Tenant
    private lateinit var b: Tenant
    private lateinit var keyA: String

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
        val hex = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        val flow = flows.createFlow(CreateFlowRequest(name = "rest-$org", orgSlug = org))
        val trail = trails.createTrail(
            CreateTrailRequest(flowId = flow.id, gitAuthor = "a", gitAuthorEmail = "a@example.com", orgSlug = org,
                gitCommitSha = "sha-$org", gitBranch = "main")
        )
        attestations.recordAttestation(trail.id, CreateAttestationRequest(type = "junit", status = AttestationStatus.PASSED, orgSlug = org))
        val env = environments.createEnvironment(CreateEnvironmentRequest(name = "env-$org", type = EnvironmentType.K8S, orgSlug = org))
        val policy = policies.createPolicy(CreatePolicyRequest(name = "policy-$org", orgSlug = org))
        artifacts.reportArtifact(trail.id, CreateArtifactRequest(imageName = "img-$org", imageTag = "1", sha256Digest = "sha256:$hex", reportedBy = "ci", orgSlug = org))
        val approval = approvals.requestApproval(CreateApprovalRequest(trailId = trail.id))
        val gate = gateResults.save(DeploymentGateResult(artifactSha256 = hex, environmentId = env.id, decision = GateDecision.ALLOWED))
        val assessment = assessments.save(ComplianceAssessment(frameworkId = UUID.randomUUID(), trailId = trail.id,
            overallStatus = AssessmentStatus.COMPLIANT, orgSlug = org))
        Tenant(trail.id, env.id, policy.id, "sha256:$hex", approval.id, gate.id, assessment.id)
    }

    private fun key(orgSlug: String?, vararg scopes: String): String = asAdmin {
        val owner = users.save(User(email = "rest-${System.nanoTime()}@example.com", name = "Rest"))
        apiKeyService.createApiKey(
            CreateApiKeyRequest(ownerId = owner.id, label = "rest", ownerType = OwnerType.USER, orgSlug = orgSlug,
                scopes = scopes.toList().ifEmpty { listOf("flows:read", "trails:read", "evidence:read", "policies:read") })
        ).plainTextKey
    }

    @BeforeEach
    fun setUp() {
        clearSecurity(); rateLimiter.reset(); validationCache.clear()
        a = seed(orgA)
        b = seed(orgB)
        keyA = key(orgA)
    }

    @AfterEach
    fun tearDown() { clearSecurity(); rateLimiter.reset(); validationCache.clear() }

    private fun body(key: String, path: String) =
        mockMvc.get(path) { header("Authorization", "Bearer $key") }.andReturn().response

    @Test
    fun `another organisation's environment, policy and approval are 404`() {
        assertThat(body(keyA, "/api/v1/environments/${b.environmentId}").status).isEqualTo(404)
        assertThat(body(keyA, "/api/v1/environments/${b.environmentId}/snapshots").status).isEqualTo(404)
        assertThat(body(keyA, "/api/v1/policies/${b.policyId}").status).isEqualTo(404)
        assertThat(body(keyA, "/api/v1/approvals/${b.approvalId}").status).isEqualTo(404)
        assertThat(body(keyA, "/api/v1/compliance/assessments/${b.assessmentId}").status).isEqualTo(404)
        assertThat(body(keyA, "/api/v1/environments/${a.environmentId}").status).isEqualTo(200)
    }

    @Test
    fun `lists show only the key's organisation`() {
        fun list(path: String) = body(keyA, path).contentAsString
        assertThat(list("/api/v1/environments")).contains(a.environmentId.toString()).doesNotContain(b.environmentId.toString())
        assertThat(list("/api/v1/policies")).contains(a.policyId.toString()).doesNotContain(b.policyId.toString())
        assertThat(list("/api/v1/approvals")).contains(a.approvalId.toString()).doesNotContain(b.approvalId.toString())
        assertThat(list("/api/v1/gate/results")).contains(a.gateResultId.toString()).doesNotContain(b.gateResultId.toString())
        assertThat(list("/api/v1/compliance/assessments")).contains(a.assessmentId.toString()).doesNotContain(b.assessmentId.toString())
    }

    @Test
    fun `an artifact digest from another organisation finds nothing`() {
        assertThat(body(keyA, "/api/v1/artifacts?sha256=${b.digest}").contentAsString).doesNotContain(b.trailId.toString())
        assertThat(body(keyA, "/api/v1/artifacts?sha256=${a.digest}").contentAsString).contains(a.trailId.toString())
    }

    @Test
    fun `the audit log shows only the key's organisation`() {
        val log = body(keyA, "/api/v1/audit?size=100").contentAsString
        assertThat(log).contains(a.trailId.toString()).doesNotContain(b.trailId.toString())
    }

    @Test
    fun `a cross-organisation read by an unbound credential is audited once`() {
        val unbound = key(null)
        val before = auditEvents.countWithFilters(eventType = AuditEventType.CROSS_TENANT_READ)

        assertThat(body(unbound, "/api/v1/environments").status).isEqualTo(200)

        assertThat(auditEvents.countWithFilters(eventType = AuditEventType.CROSS_TENANT_READ)).isEqualTo(before + 1)
    }

    @Test
    fun `a bound key's own reads are not audited as cross-organisation`() {
        val before = auditEvents.countWithFilters(eventType = AuditEventType.CROSS_TENANT_READ)

        body(keyA, "/api/v1/environments")

        assertThat(auditEvents.countWithFilters(eventType = AuditEventType.CROSS_TENANT_READ)).isEqualTo(before)
    }
}
