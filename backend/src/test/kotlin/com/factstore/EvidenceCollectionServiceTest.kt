package com.factstore

import com.factstore.application.EvidenceCollectionService
import com.factstore.application.FlowService
import com.factstore.application.TrailService
import com.factstore.core.domain.AttestationStatus
import com.factstore.dto.BulkEvidenceItem
import com.factstore.dto.BulkEvidenceRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.ReportCoverageRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class EvidenceCollectionServiceTest {

    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: TrailService
    @Autowired lateinit var evidenceCollectionService: EvidenceCollectionService

    private fun setupTrail(requiredTypes: List<String> = listOf("test-coverage", "security-scan")): Pair<UUID, UUID> {
        val flow = flowService.createFlow(
            CreateFlowRequest("flow-ev-${System.nanoTime()}", "desc", requiredTypes)
        )
        val trail = trailService.createTrail(
            CreateTrailRequest(
                flowId = flow.id,
                gitCommitSha = "abc123",
                gitBranch = "main",
                gitAuthor = "author",
                gitAuthorEmail = "a@b.com"
            )
        )
        return flow.id to trail.id
    }

    @Test
    fun `reportCoverage records passed coverage when line coverage meets minimum`() {
        val (_, trailId) = setupTrail()
        val request = ReportCoverageRequest(
            tool = "jacoco",
            lineCoverage = 85.0,
            minCoverage = 80.0
        )
        val response = evidenceCollectionService.reportCoverage(trailId, request)
        assertEquals("jacoco", response.tool)
        assertTrue(response.passed)
        assertEquals(85.0, response.lineCoverage)
        assertEquals(80.0, response.minCoverage)
        assertEquals(trailId, response.trailId)
    }

    @Test
    fun `reportCoverage records failed coverage when line coverage is below minimum`() {
        val (_, trailId) = setupTrail()
        val request = ReportCoverageRequest(
            tool = "jacoco",
            lineCoverage = 70.0,
            minCoverage = 80.0
        )
        val response = evidenceCollectionService.reportCoverage(trailId, request)
        assertFalse(response.passed)
    }

    @Test
    fun `reportCoverage passes when no minimum is set`() {
        val (_, trailId) = setupTrail()
        val request = ReportCoverageRequest(tool = "istanbul", lineCoverage = 45.0)
        val response = evidenceCollectionService.reportCoverage(trailId, request)
        assertTrue(response.passed)
    }

    @Test
    fun `reportCoverage fails when minCoverage is set but no metrics provided`() {
        val (_, trailId) = setupTrail()
        val request = ReportCoverageRequest(tool = "jacoco", minCoverage = 80.0)
        val response = evidenceCollectionService.reportCoverage(trailId, request)
        assertFalse(response.passed)
    }

    @Test
    fun `reportCoverage creates test-coverage attestation satisfying flow requirements`() {
        val (_, trailId) = setupTrail(requiredTypes = listOf("test-coverage"))
        evidenceCollectionService.reportCoverage(trailId, ReportCoverageRequest(tool = "jacoco", lineCoverage = 90.0))
        val summary = evidenceCollectionService.getEvidenceSummary(trailId)
        assertTrue(summary.isComplete)
        assertFalse(summary.missingRequiredTypes.contains("test-coverage"))
    }

    @Test
    fun `getCoverageReports returns list for trail`() {
        val (_, trailId) = setupTrail()
        evidenceCollectionService.reportCoverage(trailId, ReportCoverageRequest(tool = "jacoco", lineCoverage = 90.0))
        evidenceCollectionService.reportCoverage(trailId, ReportCoverageRequest(tool = "junit", lineCoverage = 88.0))
        val reports = evidenceCollectionService.getCoverageReports(trailId)
        assertEquals(2, reports.size)
    }

    @Test
    fun `reportCoverage throws NotFoundException for unknown trail`() {
        org.junit.jupiter.api.assertThrows<com.factstore.exception.NotFoundException> {
            evidenceCollectionService.reportCoverage(
                UUID.randomUUID(),
                ReportCoverageRequest(tool = "jacoco")
            )
        }
    }

    @Test
    fun `collectBulkEvidence processes all items`() {
        val (_, trailId) = setupTrail()
        val request = BulkEvidenceRequest(
            items = listOf(
                BulkEvidenceItem(trailId, "security-scan", "owasp-zap", true, "No critical issues"),
                BulkEvidenceItem(trailId, "dependency-audit", "npm-audit", true, "No vulnerabilities")
            )
        )
        val response = evidenceCollectionService.collectBulkEvidence(request)
        assertEquals(2, response.accepted)
        assertEquals(0, response.failed)
        assertEquals(2, response.results.size)
    }

    @Test
    fun `collectBulkEvidence skips unknown trails and counts as failed`() {
        val (_, trailId) = setupTrail()
        val request = BulkEvidenceRequest(
            items = listOf(
                BulkEvidenceItem(trailId, "security-scan", "snyk", true),
                BulkEvidenceItem(UUID.randomUUID(), "security-scan", "snyk", true)
            )
        )
        val response = evidenceCollectionService.collectBulkEvidence(request)
        assertEquals(1, response.accepted)
        assertEquals(1, response.failed)
    }

    @Test
    fun `getEvidenceSummary returns summary with missing types`() {
        val (_, trailId) = setupTrail(requiredTypes = listOf("security-scan", "test-coverage"))
        // Only provide security-scan evidence
        evidenceCollectionService.collectBulkEvidence(
            BulkEvidenceRequest(listOf(BulkEvidenceItem(trailId, "security-scan", "snyk", true)))
        )
        val summary = evidenceCollectionService.getEvidenceSummary(trailId)
        assertEquals(trailId, summary.trailId)
        assertFalse(summary.isComplete)
        assertTrue(summary.missingRequiredTypes.contains("test-coverage"))
        assertFalse(summary.missingRequiredTypes.contains("security-scan"))
    }

    @Test
    fun `getEvidenceSummary marks complete when all required types collected`() {
        val (_, trailId) = setupTrail(requiredTypes = listOf("security-scan"))
        evidenceCollectionService.collectBulkEvidence(
            BulkEvidenceRequest(listOf(BulkEvidenceItem(trailId, "security-scan", "snyk", true)))
        )
        val summary = evidenceCollectionService.getEvidenceSummary(trailId)
        assertTrue(summary.isComplete)
        assertTrue(summary.missingRequiredTypes.isEmpty())
    }

    @Test
    fun `getEvidenceGaps returns trails with missing required evidence`() {
        val (_, trailId) = setupTrail(requiredTypes = listOf("security-scan", "test-coverage"))
        // Only provide one of two required types
        evidenceCollectionService.collectBulkEvidence(
            BulkEvidenceRequest(listOf(BulkEvidenceItem(trailId, "security-scan", "snyk", true)))
        )
        val gapsResponse = evidenceCollectionService.getEvidenceGaps()
        assertTrue(gapsResponse.totalTrailsWithGaps > 0)
        val gap = gapsResponse.gaps.firstOrNull { it.trailId == trailId }
        assertNotNull(gap)
        assertTrue(gap!!.missingTypes.contains("test-coverage"))
    }

    @Test
    fun `getEvidenceSummary counts attestation statuses correctly`() {
        val (_, trailId) = setupTrail(requiredTypes = emptyList())
        evidenceCollectionService.collectBulkEvidence(
            BulkEvidenceRequest(
                listOf(
                    BulkEvidenceItem(trailId, "security-scan", "snyk", true),
                    BulkEvidenceItem(trailId, "dependency-audit", "npm-audit", false)
                )
            )
        )
        val summary = evidenceCollectionService.getEvidenceSummary(trailId)
        assertEquals(1, summary.passedAttestations)
        assertEquals(1, summary.failedAttestations)
        assertEquals(2, summary.totalAttestations)
    }

    @Test
    fun `getEvidenceGaps returns empty response when no trails exist`() {
        // In a fresh transaction the DB is empty — verify we get a valid empty response
        val gapsResponse = evidenceCollectionService.getEvidenceGaps()
        assertNotNull(gapsResponse)
        assertEquals(0, gapsResponse.totalTrailsWithGaps)
        assertTrue(gapsResponse.gaps.isEmpty())
    }
}
