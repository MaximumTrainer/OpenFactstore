package com.factstore.application

import com.factstore.adapter.mock.InMemoryAttestationRepository
import com.factstore.adapter.mock.InMemorySecurityScanRepository
import com.factstore.adapter.mock.InMemorySecurityThresholdRepository
import com.factstore.adapter.mock.InMemoryTrailRepository
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.SecurityScanResult
import com.factstore.core.domain.SecurityThreshold
import com.factstore.core.domain.Trail
import com.factstore.dto.RecordSecurityScanRequest
import com.factstore.dto.SetSecurityThresholdRequest
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SecurityScanServiceTest {

    private lateinit var service: SecurityScanService
    private lateinit var scanRepository: InMemorySecurityScanRepository
    private lateinit var thresholdRepository: InMemorySecurityThresholdRepository
    private lateinit var attestationRepository: InMemoryAttestationRepository
    private lateinit var trailRepository: InMemoryTrailRepository

    @BeforeEach
    fun setup() {
        scanRepository = InMemorySecurityScanRepository()
        thresholdRepository = InMemorySecurityThresholdRepository()
        attestationRepository = InMemoryAttestationRepository()
        trailRepository = InMemoryTrailRepository()
        service = SecurityScanService(scanRepository, thresholdRepository, attestationRepository, trailRepository)
    }

    private fun createTrail(): UUID {
        val flowId = UUID.randomUUID()
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
    fun `record scan with no threshold results in PASSED attestation`() {
        val trailId = createTrail()
        val request = RecordSecurityScanRequest(tool = "Trivy", criticalVulnerabilities = 5)
        val response = service.recordScan(trailId, request)
        assertEquals("Trivy", response.tool)
        assertFalse(response.thresholdBreached)
        assertTrue(response.breachDetails.isEmpty())
        val attestation = attestationRepository.findByTrailId(trailId).first()
        assertEquals(AttestationStatus.PASSED, attestation.status)
        assertEquals("SECURITY_SCAN", attestation.type)
    }

    @Test
    fun `record scan exceeding critical threshold results in FAILED attestation`() {
        val trailId = createTrail()
        val trail = trailRepository.findById(trailId)!!
        thresholdRepository.save(SecurityThreshold(flowId = trail.flowId, maxCritical = 0))

        val request = RecordSecurityScanRequest(tool = "OWASP ZAP", criticalVulnerabilities = 1)
        val response = service.recordScan(trailId, request)
        assertTrue(response.thresholdBreached)
        assertTrue(response.breachDetails.isNotEmpty())
        val attestation = attestationRepository.findByTrailId(trailId).first()
        assertEquals(AttestationStatus.FAILED, attestation.status)
    }

    @Test
    fun `record scan exceeding medium threshold results in FAILED`() {
        val trailId = createTrail()
        val trail = trailRepository.findById(trailId)!!
        thresholdRepository.save(SecurityThreshold(flowId = trail.flowId, maxMedium = 5))

        val request = RecordSecurityScanRequest(tool = "Snyk", mediumVulnerabilities = 10)
        val response = service.recordScan(trailId, request)
        assertTrue(response.thresholdBreached)
    }

    @Test
    fun `summary aggregates counts across all scans`() {
        val trailId = createTrail()
        service.recordScan(trailId, RecordSecurityScanRequest(tool = "Trivy", criticalVulnerabilities = 2, highVulnerabilities = 3))
        service.recordScan(trailId, RecordSecurityScanRequest(tool = "Snyk", criticalVulnerabilities = 1, highVulnerabilities = 1))
        val summary = service.getSummary()
        assertEquals(2, summary.totalScans)
        assertEquals(3, summary.totalCritical)
        assertEquals(4, summary.totalHigh)
        assertEquals(2, summary.scansWithCritical)
    }

    @Test
    fun `set thresholds creates new threshold`() {
        val flowId = UUID.randomUUID()
        val response = service.setThresholds(flowId, SetSecurityThresholdRequest(maxCritical = 0, maxHigh = 5, maxMedium = 10))
        assertEquals(flowId, response.flowId)
        assertEquals(0, response.maxCritical)
        assertEquals(5, response.maxHigh)
    }

    @Test
    fun `set thresholds updates existing threshold`() {
        val flowId = UUID.randomUUID()
        service.setThresholds(flowId, SetSecurityThresholdRequest(maxCritical = 0, maxHigh = 0))
        val updated = service.setThresholds(flowId, SetSecurityThresholdRequest(maxCritical = 5, maxHigh = 10))
        assertEquals(5, updated.maxCritical)
        assertEquals(10, updated.maxHigh)
        assertEquals(1, thresholdRepository.findAll().size)
    }

    @Test
    fun `get thresholds throws NotFoundException for unknown flow`() {
        assertThrows(NotFoundException::class.java) {
            service.getThresholds(UUID.randomUUID())
        }
    }

    @Test
    fun `threshold evaluation identifies all breach types`() {
        val trailId = createTrail()
        val trail = trailRepository.findById(trailId)!!
        val threshold = SecurityThreshold(
            flowId = trail.flowId, maxCritical = 0, maxHigh = 0, maxMedium = 5, maxLow = 100
        )
        val scan = SecurityScanResult(
            trailId = trailId, tool = "Test",
            criticalVulnerabilities = 1, highVulnerabilities = 1,
            mediumVulnerabilities = 6, lowVulnerabilities = 101
        )
        val result = service.evaluateThresholds(scan, threshold)
        assertFalse(result.passed)
        assertEquals(4, result.breaches.size)
    }

    @Test
    fun `record scan for unknown trail throws NotFoundException`() {
        assertThrows(NotFoundException::class.java) {
            service.recordScan(UUID.randomUUID(), RecordSecurityScanRequest(tool = "Trivy"))
        }
    }
}
