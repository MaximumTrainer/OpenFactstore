package com.factstore

import com.factstore.application.ArtifactService
import com.factstore.application.AttestationService
import com.factstore.application.FlowService
import com.factstore.application.ReportService
import com.factstore.application.TrailService
import com.factstore.core.domain.AttestationStatus
import com.factstore.dto.*
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Transactional
class ReportServiceTest {

    @Autowired lateinit var reportService: ReportService
    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: TrailService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var artifactService: ArtifactService

    private fun createTrailForFlow(flowId: UUID): TrailResponse =
        trailService.createTrail(CreateTrailRequest(
            flowId = flowId,
            gitCommitSha = "abc${System.nanoTime()}",
            gitBranch = "main",
            gitAuthor = "tester",
            gitAuthorEmail = "tester@example.com"
        ))

    @Test
    fun `getComplianceReport returns report for a specific flow`() {
        val flow = flowService.createFlow(CreateFlowRequest("report-flow-${System.nanoTime()}", "desc", listOf("junit")))
        createTrailForFlow(flow.id)
        createTrailForFlow(flow.id)

        val report = reportService.getComplianceReport(flow.id, null, null)
        assertEquals(flow.id, report.flowId)
        assertEquals(flow.name, report.flowName)
        assertEquals(2, report.totalTrails)
        assertEquals(0, report.compliantTrails)
        assertEquals(0, report.nonCompliantTrails)
        assertEquals(2, report.pendingTrails)
        assertEquals(0.0, report.complianceRate)
    }

    @Test
    fun `getComplianceReport with unknown flowId throws NotFoundException`() {
        assertThrows<NotFoundException> {
            reportService.getComplianceReport(UUID.randomUUID(), null, null)
        }
    }

    @Test
    fun `getComplianceReport with null flowId aggregates all trails`() {
        val report = reportService.getComplianceReport(null, null, null)
        assertTrue(report.totalTrails >= 0)
        assertEquals(report.compliantTrails + report.nonCompliantTrails + report.pendingTrails, report.totalTrails)
    }

    @Test
    fun `getComplianceReport date range filter works`() {
        val flow = flowService.createFlow(CreateFlowRequest("report-date-flow-${System.nanoTime()}", "desc"))
        createTrailForFlow(flow.id)

        val from = Instant.now().minusSeconds(3600)
        val to = Instant.now().plusSeconds(3600)
        val report = reportService.getComplianceReport(flow.id, from, to)
        assertEquals(1, report.totalTrails)
        assertEquals(from, report.from)
        assertEquals(to, report.to)
    }

    @Test
    fun `getComplianceReport lists non-compliant trails`() {
        val flow = flowService.createFlow(CreateFlowRequest("report-nc-flow-${System.nanoTime()}", "desc", listOf("junit")))
        val trail = createTrailForFlow(flow.id)
        attestationService.recordAttestation(trail.id, CreateAttestationRequest("junit", AttestationStatus.FAILED))

        val report = reportService.getComplianceReport(flow.id, null, null)
        assertEquals(1, report.nonCompliantTrails)
        assertTrue(report.nonCompliantTrailList.any { it.id == trail.id })
    }

    @Test
    fun `getAuditTrailExport returns full trail data`() {
        val flow = flowService.createFlow(CreateFlowRequest("export-flow-${System.nanoTime()}", "desc", listOf("junit")))
        val trail = createTrailForFlow(flow.id)
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("myimage", "v1.0", "sha256:abc", reportedBy = "ci"))
        attestationService.recordAttestation(trail.id, CreateAttestationRequest("junit", AttestationStatus.PASSED))

        val export = reportService.getAuditTrailExport(trail.id)
        assertEquals(trail.id, export.trailId)
        assertEquals(trail.id, export.trail.id)
        assertEquals(flow.id, export.flow.id)
        assertEquals(1, export.artifacts.size)
        assertEquals(1, export.attestations.size)
        assertNotNull(export.exportedAt)
    }

    @Test
    fun `getAuditTrailExport with unknown trailId throws NotFoundException`() {
        assertThrows<NotFoundException> {
            reportService.getAuditTrailExport(UUID.randomUUID())
        }
    }
}
