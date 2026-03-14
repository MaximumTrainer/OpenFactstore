package com.factstore.application

import com.factstore.adapter.mock.*
import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.AssessmentStatus
import com.factstore.core.domain.Trail
import com.factstore.dto.*
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class RegulatoryComplianceServiceTest {

    private lateinit var service: RegulatoryComplianceService
    private lateinit var frameworkRepository: InMemoryRegulatoryFrameworkRepository
    private lateinit var controlRepository: InMemoryRegulatoryControlRepository
    private lateinit var mappingRepository: InMemoryComplianceMappingRepository
    private lateinit var assessmentRepository: InMemoryComplianceAssessmentRepository
    private lateinit var attestationRepository: InMemoryAttestationRepository
    private lateinit var trailRepository: InMemoryTrailRepository

    @BeforeEach
    fun setup() {
        frameworkRepository = InMemoryRegulatoryFrameworkRepository()
        controlRepository = InMemoryRegulatoryControlRepository()
        mappingRepository = InMemoryComplianceMappingRepository()
        assessmentRepository = InMemoryComplianceAssessmentRepository()
        attestationRepository = InMemoryAttestationRepository()
        trailRepository = InMemoryTrailRepository()
        service = RegulatoryComplianceService(
            frameworkRepository,
            controlRepository,
            mappingRepository,
            assessmentRepository,
            attestationRepository,
            trailRepository
        )
        // Don't call seedBuiltinFrameworks in tests — seeds are for production context
    }

    private fun createTrail(flowId: UUID = UUID.randomUUID()): UUID {
        val trail = Trail(
            flowId = flowId,
            gitCommitSha = "abc123",
            gitBranch = "main",
            gitAuthor = "Test User",
            gitAuthorEmail = "test@example.com"
        )
        trailRepository.save(trail)
        return trail.id
    }

    @Test
    fun `create framework persists and returns response`() {
        val response = service.createFramework(
            CreateFrameworkRequest(name = "Test Framework", version = "1.0", description = "A test framework")
        )
        assertEquals("Test Framework", response.name)
        assertEquals("1.0", response.version)
        assertEquals("A test framework", response.description)
        assertTrue(response.isActive)
        assertNotNull(response.id)
    }

    @Test
    fun `list frameworks returns all frameworks`() {
        service.createFramework(CreateFrameworkRequest(name = "FW-A", version = "1.0"))
        service.createFramework(CreateFrameworkRequest(name = "FW-B", version = "2.0"))
        val all = service.listFrameworks()
        assertEquals(2, all.size)
    }

    @Test
    fun `get framework includes controls`() {
        val fw = service.createFramework(CreateFrameworkRequest(name = "FW-X", version = "1.0"))
        service.addControl(fw.id, CreateControlRequest(controlId = "C-1", title = "Control One", requiredEvidenceTypes = listOf("SCAN")))
        val retrieved = service.getFramework(fw.id)
        assertEquals(1, retrieved.controls.size)
        assertEquals("C-1", retrieved.controls[0].controlId)
        assertEquals(listOf("SCAN"), retrieved.controls[0].requiredEvidenceTypes)
    }

    @Test
    fun `get framework throws NotFoundException for unknown id`() {
        assertThrows(NotFoundException::class.java) {
            service.getFramework(UUID.randomUUID())
        }
    }

    @Test
    fun `add control to framework persists control`() {
        val fw = service.createFramework(CreateFrameworkRequest(name = "FW", version = "1.0"))
        val control = service.addControl(
            fw.id,
            CreateControlRequest(
                controlId = "PCI-1",
                title = "Patch management",
                requiredEvidenceTypes = listOf("SECURITY_SCAN", "PATCH_ATTESTATION")
            )
        )
        assertEquals("PCI-1", control.controlId)
        assertEquals(listOf("SECURITY_SCAN", "PATCH_ATTESTATION"), control.requiredEvidenceTypes)
        assertEquals(fw.id, control.frameworkId)
    }

    @Test
    fun `add control throws NotFoundException for unknown framework`() {
        assertThrows(NotFoundException::class.java) {
            service.addControl(UUID.randomUUID(), CreateControlRequest(controlId = "X", title = "Y"))
        }
    }

    @Test
    fun `create mapping persists mapping`() {
        val fw = service.createFramework(CreateFrameworkRequest(name = "FW", version = "1.0"))
        val control = service.addControl(fw.id, CreateControlRequest(controlId = "C-1", title = "Control"))
        val flowId = UUID.randomUUID()
        val mapping = service.createMapping(
            CreateMappingRequest(
                regulatoryControlId = control.id,
                flowId = flowId,
                attestationType = "SECURITY_SCAN",
                isMandatory = true
            )
        )
        assertEquals(control.id, mapping.regulatoryControlId)
        assertEquals(flowId, mapping.flowId)
        assertEquals("SECURITY_SCAN", mapping.attestationType)
        assertTrue(mapping.isMandatory)
    }

    @Test
    fun `list mappings returns all mappings`() {
        val fw = service.createFramework(CreateFrameworkRequest(name = "FW", version = "1.0"))
        val c1 = service.addControl(fw.id, CreateControlRequest(controlId = "C-1", title = "C1"))
        val c2 = service.addControl(fw.id, CreateControlRequest(controlId = "C-2", title = "C2"))
        val flowId = UUID.randomUUID()
        service.createMapping(CreateMappingRequest(c1.id, flowId, "TYPE_A"))
        service.createMapping(CreateMappingRequest(c2.id, flowId, "TYPE_B"))
        assertEquals(2, service.listMappings().size)
    }

    @Test
    fun `assess trail with all required attestations returns COMPLIANT`() {
        val fw = service.createFramework(CreateFrameworkRequest(name = "FW", version = "1.0"))
        service.addControl(
            fw.id,
            CreateControlRequest(controlId = "C-1", title = "Control", requiredEvidenceTypes = listOf("SECURITY_SCAN", "BUILD_ATTESTATION"))
        )
        val trailId = createTrail()
        attestationRepository.save(Attestation(trailId = trailId, type = "SECURITY_SCAN", status = AttestationStatus.PASSED))
        attestationRepository.save(Attestation(trailId = trailId, type = "BUILD_ATTESTATION", status = AttestationStatus.PASSED))

        val result = service.assessTrail(AssessTrailRequest(frameworkId = fw.id, trailId = trailId))
        assertEquals(AssessmentStatus.COMPLIANT, result.overallStatus)
        assertEquals(1, result.controlResults.size)
        assertEquals("SATISFIED", result.controlResults[0].status)
    }

    @Test
    fun `assess trail with all attestations missing returns NON_COMPLIANT`() {
        val fw = service.createFramework(CreateFrameworkRequest(name = "FW", version = "1.0"))
        service.addControl(
            fw.id,
            CreateControlRequest(controlId = "C-1", title = "Control", requiredEvidenceTypes = listOf("SECURITY_SCAN"))
        )
        val trailId = createTrail()
        // No attestations

        val result = service.assessTrail(AssessTrailRequest(frameworkId = fw.id, trailId = trailId))
        assertEquals(AssessmentStatus.NON_COMPLIANT, result.overallStatus)
        assertEquals("NOT_SATISFIED", result.controlResults[0].status)
        assertEquals(listOf("SECURITY_SCAN"), result.controlResults[0].missingEvidence)
    }

    @Test
    fun `assess trail with some missing attestations returns PARTIAL`() {
        val fw = service.createFramework(CreateFrameworkRequest(name = "FW", version = "1.0"))
        service.addControl(
            fw.id,
            CreateControlRequest(controlId = "C-1", title = "C1 satisfied", requiredEvidenceTypes = listOf("SECURITY_SCAN"))
        )
        service.addControl(
            fw.id,
            CreateControlRequest(controlId = "C-2", title = "C2 missing", requiredEvidenceTypes = listOf("PATCH_ATTESTATION"))
        )
        val trailId = createTrail()
        attestationRepository.save(Attestation(trailId = trailId, type = "SECURITY_SCAN", status = AttestationStatus.PASSED))
        // PATCH_ATTESTATION is missing

        val result = service.assessTrail(AssessTrailRequest(frameworkId = fw.id, trailId = trailId))
        assertEquals(AssessmentStatus.PARTIAL, result.overallStatus)
        val c1 = result.controlResults.first { it.controlId == "C-1" }
        val c2 = result.controlResults.first { it.controlId == "C-2" }
        assertEquals("SATISFIED", c1.status)
        assertEquals("NOT_SATISFIED", c2.status)
    }

    @Test
    fun `assess trail ignores failed attestations`() {
        val fw = service.createFramework(CreateFrameworkRequest(name = "FW", version = "1.0"))
        service.addControl(fw.id, CreateControlRequest(controlId = "C-1", title = "Control", requiredEvidenceTypes = listOf("SECURITY_SCAN")))
        val trailId = createTrail()
        // Failed attestation should not satisfy the control
        attestationRepository.save(Attestation(trailId = trailId, type = "SECURITY_SCAN", status = AttestationStatus.FAILED))

        val result = service.assessTrail(AssessTrailRequest(frameworkId = fw.id, trailId = trailId))
        assertEquals(AssessmentStatus.NON_COMPLIANT, result.overallStatus)
    }

    @Test
    fun `assess trail persists and is retrievable`() {
        val fw = service.createFramework(CreateFrameworkRequest(name = "FW", version = "1.0"))
        service.addControl(fw.id, CreateControlRequest(controlId = "C-1", title = "Control", requiredEvidenceTypes = listOf("SCAN")))
        val trailId = createTrail()
        attestationRepository.save(Attestation(trailId = trailId, type = "SCAN", status = AttestationStatus.PASSED))

        val created = service.assessTrail(AssessTrailRequest(frameworkId = fw.id, trailId = trailId))
        val retrieved = service.getAssessment(created.id)
        assertEquals(created.id, retrieved.id)
        assertEquals(AssessmentStatus.COMPLIANT, retrieved.overallStatus)
    }

    @Test
    fun `list assessments returns all assessments`() {
        val fw = service.createFramework(CreateFrameworkRequest(name = "FW", version = "1.0"))
        service.addControl(fw.id, CreateControlRequest(controlId = "C-1", title = "Control"))
        val t1 = createTrail(); val t2 = createTrail()
        service.assessTrail(AssessTrailRequest(frameworkId = fw.id, trailId = t1))
        service.assessTrail(AssessTrailRequest(frameworkId = fw.id, trailId = t2))
        assertEquals(2, service.listAssessments().size)
    }

    @Test
    fun `get assessment throws NotFoundException for unknown id`() {
        assertThrows(NotFoundException::class.java) {
            service.getAssessment(UUID.randomUUID())
        }
    }

    @Test
    fun `generate report aggregates assessments for framework`() {
        val fw = service.createFramework(CreateFrameworkRequest(name = "PCI-DSS Test", version = "4.0"))
        service.addControl(fw.id, CreateControlRequest(controlId = "C-1", title = "Control"))
        val t1 = createTrail(); val t2 = createTrail()
        attestationRepository.save(Attestation(trailId = t1, type = "SCAN", status = AttestationStatus.PASSED))
        service.assessTrail(AssessTrailRequest(frameworkId = fw.id, trailId = t1))
        service.assessTrail(AssessTrailRequest(frameworkId = fw.id, trailId = t2))

        val report = service.generateReport(fw.id)
        assertEquals(fw.id, report.frameworkId)
        assertEquals("PCI-DSS Test", report.frameworkName)
        assertEquals("4.0", report.frameworkVersion)
        assertEquals(2, report.assessments.size)
        assertNotNull(report.generatedAt)
    }

    @Test
    fun `generate report throws NotFoundException for unknown framework`() {
        assertThrows(NotFoundException::class.java) {
            service.generateReport(UUID.randomUUID())
        }
    }
}
