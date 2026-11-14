package com.factstore.immutability

import com.factstore.application.ArtifactService
import com.factstore.application.AttestationService
import com.factstore.application.FlowService
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.EnvironmentType
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.inbound.IEnvironmentService
import com.factstore.core.port.inbound.ITrailCleanupService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.RecordSnapshotRequest
import com.factstore.dto.TrailResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * #245: evidence is immutable. Trails and environments are archived, never deleted; an archived
 * trail takes no new evidence; no endpoint changes or removes an attestation or its evidence.
 * Lawful erasure goes through cryptographic shredding, which is audited (see CryptoShreddingTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(
    authorities = [
        "SCOPE_admin", "SCOPE_flows:read", "SCOPE_flows:write", "SCOPE_trails:read", "SCOPE_trails:write",
        "SCOPE_attestations:write", "SCOPE_artifacts:write", "SCOPE_evidence:write"
    ]
)
class ArchiveNotDeleteTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: ITrailService
    @Autowired lateinit var cleanupService: ITrailCleanupService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var artifactService: ArtifactService
    @Autowired lateinit var environmentService: IEnvironmentService
    @Autowired lateinit var auditService: IAuditService

    private fun newFlowId(): UUID =
        flowService.createFlow(CreateFlowRequest("flow-245-${System.nanoTime()}", "d", listOf("custom-check"))).id

    private fun newTrail(flowId: UUID = newFlowId()): TrailResponse = trailService.createTrail(
        CreateTrailRequest(flowId = flowId, gitCommitSha = "sha", gitBranch = "main", gitAuthor = "a", gitAuthorEmail = "a@example.com")
    )

    private fun trailWithEvidence(): TrailResponse {
        val trail = newTrail()
        val att = attestationService.recordAttestation(trail.id, CreateAttestationRequest(type = "custom-check", status = AttestationStatus.PASSED))
        attestationService.uploadEvidence(trail.id, att.id, "report.txt", "text/plain", "ok".toByteArray())
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("img", "1", "sha256:${System.nanoTime()}", reportedBy = "ci"))
        return trail
    }

    private fun digest() = "sha256:" + UUID.randomUUID().toString().replace("-", "").repeat(2)

    private fun newEnvironment() =
        environmentService.createEnvironment(CreateEnvironmentRequest(name = "env-245-${System.nanoTime()}", type = EnvironmentType.K8S))

    // --- Trails ---------------------------------------------------------------

    @Test
    fun `DELETE on a trail archives it and removes no evidence`() {
        val trail = trailWithEvidence()
        val before = cleanupService.cascadeFor(trail.id)

        mockMvc.delete("/api/v1/trails/${trail.id}").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(trail.id.toString()) }
            jsonPath("$.archivedAt") { isNotEmpty() }
        }

        assertThat(trailService.getTrail(trail.id).archivedAt).isNotNull()
        assertThat(cleanupService.cascadeFor(trail.id)).isEqualTo(before)
        assertThat(before.attestations).isEqualTo(1)
        assertThat(before.artifacts).isEqualTo(1)
        assertThat(before.evidenceFiles).isEqualTo(1)
        assertThat(auditService.getEventsForTrail(trail.id).map { it.eventType }).contains(AuditEventType.TRAIL_ARCHIVED)
    }

    @Test
    fun `bulk cleanup refuses hard deletion`() {
        val trail = newTrail()
        mockMvc.post("/api/v1/trails/cleanup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"flowId":"${trail.flowId}","mode":"DELETE","dryRun":false}"""
        }.andExpect { status { isBadRequest() } }
        assertThat(trailService.getTrail(trail.id).archivedAt).isNull()
    }

    @Test
    fun `archived trails are hidden from lists unless includeArchived is set`() {
        val trail = newTrail()
        mockMvc.delete("/api/v1/trails/${trail.id}").andExpect { status { isOk() } }

        mockMvc.get("/api/v1/trails?flowId=${trail.flowId}").andExpect { jsonPath("$.length()") { value(0) } }
        mockMvc.get("/api/v1/trails?flowId=${trail.flowId}&includeArchived=true").andExpect {
            jsonPath("$[0].id") { value(trail.id.toString()) }
        }
    }

    @Test
    fun `writes to an archived trail are rejected with 409`() {
        val trail = trailWithEvidence()
        val attestationId = attestationService.listAttestations(trail.id, 0, 10).items.first().id
        mockMvc.post("/api/v1/trails/${trail.id}/archive").andExpect { status { isOk() } }

        mockMvc.post("/api/v1/trails/${trail.id}/attestations") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"type":"custom-check","status":"PASSED"}"""
        }.andExpect { status { isConflict() } }
        mockMvc.post("/api/v2/trails/${trail.id}/attestations") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"type":"custom-check","status":"PASSED"}"""
        }.andExpect { status { isConflict() } }
        mockMvc.post("/api/v1/trails/${trail.id}/artifacts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"imageName":"img","imageTag":"2","sha256Digest":"${digest()}","reportedBy":"ci"}"""
        }.andExpect { status { isConflict() } }
        mockMvc.post("/api/v2/trails/${trail.id}/artifacts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"imageName":"img","imageTag":"2","sha256Digest":"${digest()}","reportedBy":"ci"}"""
        }.andExpect { status { isConflict() } }
        val file = MockMultipartFile("file", "more.txt", "text/plain", "more".toByteArray())
        mockMvc.multipart("/api/v1/trails/${trail.id}/attestations/$attestationId/evidence") { file(file) }
            .andExpect { status { isConflict() } }
        mockMvc.multipart("/api/v2/trails/${trail.id}/attestations/$attestationId/evidence") { file(file) }
            .andExpect { status { isConflict() } }

        assertThat(cleanupService.cascadeFor(trail.id).attestations).isEqualTo(1)
    }

    @Test
    fun `evidence already attached to an attestation cannot be replaced`() {
        val trail = trailWithEvidence()
        val attestation = attestationService.listAttestations(trail.id, 0, 10).items.first()
        val file = MockMultipartFile("file", "other.txt", "text/plain", "other".toByteArray())

        mockMvc.multipart("/api/v1/trails/${trail.id}/attestations/${attestation.id}/evidence") { file(file) }
            .andExpect { status { isConflict() } }
        mockMvc.multipart("/api/v2/trails/${trail.id}/attestations/${attestation.id}/evidence") { file(file) }
            .andExpect { status { isConflict() } }

        val after = attestationService.listAttestations(trail.id, 0, 10).items.first()
        assertThat(after.evidenceFileHash).isEqualTo(attestation.evidenceFileHash)
        assertThat(cleanupService.cascadeFor(trail.id).evidenceFiles).isEqualTo(1)
    }

    @Test
    fun `an unarchived trail takes evidence again`() {
        val trail = newTrail()
        mockMvc.post("/api/v1/trails/${trail.id}/archive").andExpect { status { isOk() } }
        mockMvc.post("/api/v1/trails/${trail.id}/unarchive").andExpect { status { isOk() } }

        mockMvc.post("/api/v1/trails/${trail.id}/attestations") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"type":"custom-check","status":"PASSED"}"""
        }.andExpect { status { isCreated() } }
    }

    // --- Environments -----------------------------------------------------------

    @Test
    fun `DELETE on an environment archives it and keeps its snapshots`() {
        val env = newEnvironment()
        environmentService.recordSnapshot(env.id, RecordSnapshotRequest(recordedBy = "ci"))

        mockMvc.delete("/api/v1/environments/${env.id}").andExpect { status { isNoContent() } }

        assertThat(environmentService.getEnvironment(env.id).archivedAt).isNotNull()
        assertThat(environmentService.listSnapshots(env.id)).hasSize(1)
        assertThat(auditService.queryEvents(eventType = AuditEventType.ENVIRONMENT_ARCHIVED, page = 0, size = 100).events)
            .anySatisfy { assertThat(it.payload).contains(env.id.toString()) }
    }

    @Test
    fun `archived environments are hidden from lists unless includeArchived is set`() {
        val env = newEnvironment()
        mockMvc.delete("/api/v1/environments/${env.id}").andExpect { status { isNoContent() } }

        val hidden = environmentService.listEnvironments(0, 1000).items.map { it.id }
        assertThat(hidden).doesNotContain(env.id)
        mockMvc.get("/api/v1/environments?size=1000").andExpect {
            jsonPath("$.items[?(@.id == '${env.id}')]") { isEmpty() }
        }
        mockMvc.get("/api/v1/environments?size=1000&includeArchived=true").andExpect {
            jsonPath("$.items[?(@.id == '${env.id}')]") { isNotEmpty() }
        }
    }

    @Test
    fun `an archived environment takes no snapshots until it is unarchived`() {
        val env = newEnvironment()
        mockMvc.delete("/api/v1/environments/${env.id}").andExpect { status { isNoContent() } }

        mockMvc.post("/api/v1/environments/${env.id}/snapshots") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"recordedBy":"ci","artifacts":[]}"""
        }.andExpect { status { isConflict() } }

        mockMvc.post("/api/v1/environments/${env.id}/unarchive").andExpect {
            status { isOk() }
            jsonPath("$.archivedAt") { doesNotExist() }
        }
        mockMvc.post("/api/v1/environments/${env.id}/snapshots") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"recordedBy":"ci","artifacts":[]}"""
        }.andExpect { status { isCreated() } }
    }

    // --- Flows ------------------------------------------------------------------

    @Test
    fun `a flow with trails cannot be deleted even with force`() {
        val trail = newTrail()

        mockMvc.delete("/api/v1/flows/${trail.flowId}?force=true").andExpect { status { isConflict() } }
        mockMvc.delete("/api/v2/flows/${trail.flowId}?force=true").andExpect { status { isConflict() } }

        assertThat(trailService.getTrail(trail.id)).isNotNull()
    }

    // --- The API surface --------------------------------------------------------

    private val mutating = setOf(RequestMethod.DELETE, RequestMethod.PUT, RequestMethod.PATCH)

    /**
     * Every PUT/PATCH/DELETE route declared by any controller, read from the annotations so
     * controllers that only load with optional infrastructure (Vault, for one) are covered too.
     */
    private fun mutatingRoutes(): List<Pair<RequestMethod, String>> {
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AnnotationTypeFilter(RestController::class.java))
        return scanner.findCandidateComponents("com.factstore").flatMap { candidate ->
            val type = Class.forName(candidate.beanClassName)
            val prefixes = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping::class.java)
                ?.path?.toList()?.ifEmpty { null } ?: listOf("")
            type.declaredMethods.flatMap { method ->
                val mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping::class.java)
                    ?: return@flatMap emptyList()
                val paths = mapping.path.toList().ifEmpty { listOf("") }
                mapping.method.filter { it in mutating }.flatMap { verb ->
                    prefixes.flatMap { prefix -> paths.map { verb to prefix + it } }
                }
            }
        }
    }

    @Test
    fun `no endpoint can change or remove an attestation or its evidence`() {
        val routes = mutatingRoutes()
        assertThat(routes).isNotEmpty()

        // Attestation *types* are definitions, not evidence; everything else under an
        // attestation or evidence path must be append-only.
        val touchingEvidence = routes.filter { (_, path) ->
            (path.contains("attestation") || path.contains("evidence")) && !path.startsWith("/api/v1/attestation-types")
        }
        assertThat(touchingEvidence).describedAs("PUT/PATCH/DELETE routes on attestations or evidence").isEmpty()
    }

    @Test
    fun `the only DELETE routes on trails and environments are the archiving ones`() {
        val deletes = mutatingRoutes()
            .filter { (method, path) -> method == RequestMethod.DELETE && (path.contains("/trails") || path.contains("/environments/")) }
            .map { it.second }
            .filterNot { it.startsWith("/api/v1/logical-environments") || it.contains("/allowlist/") }

        assertThat(deletes).containsExactlyInAnyOrder("/api/v1/trails/{id}", "/api/v1/environments/{id}")
    }

    @Test
    fun `vault evidence has no DELETE route`() {
        assertThat(mutatingRoutes().map { it.second }.filter { it.startsWith("/api/v1/evidence") }).isEmpty()
    }
}
