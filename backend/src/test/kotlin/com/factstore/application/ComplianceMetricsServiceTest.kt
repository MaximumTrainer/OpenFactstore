package com.factstore.application

import com.factstore.adapter.mock.InMemoryAttestationRepository
import com.factstore.adapter.mock.InMemoryDriftReportRepository
import com.factstore.adapter.mock.InMemorySecurityScanRepository
import com.factstore.adapter.mock.InMemoryTrailRepository
import com.factstore.core.domain.Approval
import com.factstore.core.domain.ApprovalStatus
import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.DriftReport
import com.factstore.core.domain.SecurityScanResult
import com.factstore.core.domain.Trail
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.outbound.IApprovalRepository
import com.factstore.core.port.outbound.IDriftReportRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class ComplianceMetricsServiceTest {

    private lateinit var service: ComplianceMetricsService
    private lateinit var trailRepository: InMemoryTrailRepository
    private lateinit var attestationRepository: InMemoryAttestationRepository
    private lateinit var scanRepository: InMemorySecurityScanRepository
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var approvalRepository: IApprovalRepository
    private lateinit var driftReportRepository: IDriftReportRepository

    @BeforeEach
    fun setup() {
        trailRepository = InMemoryTrailRepository()
        attestationRepository = InMemoryAttestationRepository()
        scanRepository = InMemorySecurityScanRepository()
        meterRegistry = SimpleMeterRegistry()
        approvalRepository = object : IApprovalRepository {
            override fun save(approval: Approval) = approval
            override fun findById(id: UUID) = null
            override fun findByTrailId(trailId: UUID) = emptyList<Approval>()
            override fun findByStatus(status: ApprovalStatus) = emptyList<Approval>()
            override fun findAll() = emptyList<Approval>()
            override fun existsById(id: UUID) = false
        }
        driftReportRepository = InMemoryDriftReportRepository()
        service = ComplianceMetricsService(meterRegistry, trailRepository, attestationRepository, scanRepository, approvalRepository, driftReportRepository)
        service.registerMetrics()
    }

    private fun trail(status: TrailStatus = TrailStatus.PENDING): Trail {
        val t = Trail(
            flowId = UUID.randomUUID(),
            gitCommitSha = "abc123",
            gitBranch = "main",
            gitAuthor = "Test User",
            gitAuthorEmail = "test@example.com",
            status = status
        )
        trailRepository.save(t)
        return t
    }

    @Test
    fun `metrics are registered with the meter registry`() {
        assertNotNull(meterRegistry.find("factstore_trails_total").gauge())
        assertNotNull(meterRegistry.find("factstore_trails_compliant").gauge())
        assertNotNull(meterRegistry.find("factstore_trails_non_compliant").gauge())
        assertNotNull(meterRegistry.find("factstore_attestations_total").gauge())
        assertNotNull(meterRegistry.find("factstore_attestations_passed").gauge())
        assertNotNull(meterRegistry.find("factstore_attestations_failed").gauge())
        assertNotNull(meterRegistry.find("factstore_security_scans_total").gauge())
        assertNotNull(meterRegistry.find("factstore_security_scans_passed").gauge())
        assertNotNull(meterRegistry.find("factstore_security_scans_failed").gauge())
        assertNotNull(meterRegistry.find("factstore_compliance_rate").gauge())
    }

    @Test
    fun `getComplianceMetrics returns correct counts`() {
        trail(TrailStatus.COMPLIANT)
        trail(TrailStatus.COMPLIANT)
        trail(TrailStatus.NON_COMPLIANT)

        val trailId = UUID.randomUUID()
        attestationRepository.save(Attestation(trailId = trailId, type = "junit", status = AttestationStatus.PASSED))
        attestationRepository.save(Attestation(trailId = trailId, type = "snyk", status = AttestationStatus.FAILED))

        val metrics = service.getComplianceMetrics()

        assertEquals(3, metrics.totalTrails)
        assertEquals(2, metrics.compliantTrails)
        assertEquals(1, metrics.nonCompliantTrails)
        assertEquals(2, metrics.totalAttestations)
        assertEquals(1, metrics.passedAttestations)
        assertEquals(1, metrics.failedAttestations)
        assertNotNull(metrics.generatedAt)
    }

    @Test
    fun `compliance rate is 100 when all trails are compliant`() {
        trail(TrailStatus.COMPLIANT)
        trail(TrailStatus.COMPLIANT)

        val metrics = service.getComplianceMetrics()

        assertEquals(100.0, metrics.complianceRate, 0.001)
    }

    @Test
    fun `compliance rate is 0 when no trails exist`() {
        val metrics = service.getComplianceMetrics()
        assertEquals(0.0, metrics.complianceRate, 0.001)
    }

    @Test
    fun `compliance rate is calculated correctly for mixed statuses`() {
        trail(TrailStatus.COMPLIANT)
        trail(TrailStatus.NON_COMPLIANT)
        trail(TrailStatus.NON_COMPLIANT)

        val metrics = service.getComplianceMetrics()

        assertEquals(1.0 / 3.0 * 100, metrics.complianceRate, 0.001)
    }

    @Test
    fun `getSecurityMetrics returns correct scan counts`() {
        val trailId = UUID.randomUUID()
        scanRepository.save(SecurityScanResult(trailId = trailId, tool = "Trivy", criticalVulnerabilities = 0, highVulnerabilities = 0))
        scanRepository.save(SecurityScanResult(trailId = trailId, tool = "Snyk", criticalVulnerabilities = 2, highVulnerabilities = 1))

        val metrics = service.getSecurityMetrics()

        assertEquals(2, metrics.totalScans)
        assertEquals(1, metrics.passedScans)
        assertEquals(1, metrics.failedScans)
        assertEquals(2, metrics.totalCritical)
        assertEquals(1, metrics.totalHigh)
    }

    @Test
    fun `gauge values reflect current repository state`() {
        assertEquals(0.0, meterRegistry.find("factstore_trails_total").gauge()!!.value(), 0.001)

        trail(TrailStatus.COMPLIANT)
        trail(TrailStatus.COMPLIANT)
        trail(TrailStatus.NON_COMPLIANT)

        assertEquals(3.0, meterRegistry.find("factstore_trails_total").gauge()!!.value(), 0.001)
        assertEquals(2.0, meterRegistry.find("factstore_trails_compliant").gauge()!!.value(), 0.001)
        assertEquals(1.0, meterRegistry.find("factstore_trails_non_compliant").gauge()!!.value(), 0.001)
    }

    @Test
    fun `attestation gauges reflect attestation statuses`() {
        val trailId = UUID.randomUUID()
        attestationRepository.save(Attestation(trailId = trailId, type = "junit", status = AttestationStatus.PASSED))
        attestationRepository.save(Attestation(trailId = trailId, type = "snyk", status = AttestationStatus.PASSED))
        attestationRepository.save(Attestation(trailId = trailId, type = "trivy", status = AttestationStatus.FAILED))

        assertEquals(3.0, meterRegistry.find("factstore_attestations_total").gauge()!!.value(), 0.001)
        assertEquals(2.0, meterRegistry.find("factstore_attestations_passed").gauge()!!.value(), 0.001)
        assertEquals(1.0, meterRegistry.find("factstore_attestations_failed").gauge()!!.value(), 0.001)
    }

    @Test
    fun `security scan gauges reflect scan vulnerability counts`() {
        val trailId = UUID.randomUUID()
        scanRepository.save(SecurityScanResult(trailId = trailId, tool = "Trivy", criticalVulnerabilities = 0, highVulnerabilities = 0))
        scanRepository.save(SecurityScanResult(trailId = trailId, tool = "Snyk", criticalVulnerabilities = 1, highVulnerabilities = 0))
        scanRepository.save(SecurityScanResult(trailId = trailId, tool = "ZAP", criticalVulnerabilities = 0, highVulnerabilities = 2))

        assertEquals(3.0, meterRegistry.find("factstore_security_scans_total").gauge()!!.value(), 0.001)
        assertEquals(1.0, meterRegistry.find("factstore_security_scans_passed").gauge()!!.value(), 0.001)
        assertEquals(2.0, meterRegistry.find("factstore_security_scans_failed").gauge()!!.value(), 0.001)
    }

    @Test
    fun `compliance rate gauge returns correct percentage`() {
        trail(TrailStatus.COMPLIANT)
        trail(TrailStatus.COMPLIANT)
        trail(TrailStatus.NON_COMPLIANT)

        val rate = meterRegistry.find("factstore_compliance_rate").gauge()!!.value()
        assertEquals(2.0 / 3.0 * 100, rate, 0.001)
    }

    @Test
    fun `compliance rate gauge returns zero when no trails exist`() {
        val rate = meterRegistry.find("factstore_compliance_rate").gauge()!!.value()
        assertEquals(0.0, rate, 0.001)
    }

    @Test
    fun `approvals pending gauge reflects pending approval count`() {
        val pendingList = mutableListOf<Approval>()
        val customApprovalRepo = object : IApprovalRepository {
            override fun save(approval: Approval): Approval { pendingList.add(approval); return approval }
            override fun findById(id: UUID) = null
            override fun findByTrailId(trailId: UUID) = emptyList<Approval>()
            override fun findByStatus(status: ApprovalStatus) =
                if (status == ApprovalStatus.PENDING_APPROVAL) pendingList.toList() else emptyList()
            override fun findAll() = pendingList.toList()
            override fun existsById(id: UUID) = false
        }
        val localRegistry = SimpleMeterRegistry()
        val localService = ComplianceMetricsService(
            localRegistry, trailRepository, attestationRepository, scanRepository, customApprovalRepo, driftReportRepository
        )
        localService.registerMetrics()

        assertEquals(0.0, localRegistry.find("factstore_approvals_pending").gauge()!!.value(), 0.001)

        pendingList.add(Approval(trailId = UUID.randomUUID(), flowId = UUID.randomUUID()))
        pendingList.add(Approval(trailId = UUID.randomUUID(), flowId = UUID.randomUUID()))
        assertEquals(2.0, localRegistry.find("factstore_approvals_pending").gauge()!!.value(), 0.001)
    }

    @Test
    fun `drift detected gauge reflects reports with drift`() {
        driftReportRepository.save(DriftReport(
            environmentId = UUID.randomUUID(), snapshotId = UUID.randomUUID(), hasDrift = true
        ))
        driftReportRepository.save(DriftReport(
            environmentId = UUID.randomUUID(), snapshotId = UUID.randomUUID(), hasDrift = false
        ))

        assertEquals(1.0, meterRegistry.find("factstore_drift_detected").gauge()!!.value(), 0.001)
    }

    @Test
    fun `getSecurityMetrics correctly identifies scan with only high vulnerabilities as failed`() {
        val trailId = UUID.randomUUID()
        scanRepository.save(SecurityScanResult(trailId = trailId, tool = "ZAP", criticalVulnerabilities = 0, highVulnerabilities = 3))

        val metrics = service.getSecurityMetrics()
        assertEquals(0, metrics.passedScans)
        assertEquals(1, metrics.failedScans)
    }

    @Test
    fun `getSecurityMetrics correctly identifies scan with only critical vulnerabilities as failed`() {
        val trailId = UUID.randomUUID()
        scanRepository.save(SecurityScanResult(trailId = trailId, tool = "Trivy", criticalVulnerabilities = 2, highVulnerabilities = 0))

        val metrics = service.getSecurityMetrics()
        assertEquals(0, metrics.passedScans)
        assertEquals(1, metrics.failedScans)
    }

    @Test
    fun `getSecurityMetrics reports totalMedium and totalLow correctly`() {
        val trailId = UUID.randomUUID()
        scanRepository.save(SecurityScanResult(trailId = trailId, tool = "Trivy",
            criticalVulnerabilities = 0, highVulnerabilities = 0, mediumVulnerabilities = 4, lowVulnerabilities = 6))
        scanRepository.save(SecurityScanResult(trailId = trailId, tool = "Snyk",
            criticalVulnerabilities = 0, highVulnerabilities = 0, mediumVulnerabilities = 2, lowVulnerabilities = 1))

        val metrics = service.getSecurityMetrics()
        assertEquals(6, metrics.totalMedium)
        assertEquals(7, metrics.totalLow)
    }

    @Test
    fun `getComplianceMetrics counts only passed attestations in passedAttestations`() {
        val trailId = UUID.randomUUID()
        attestationRepository.save(Attestation(trailId = trailId, type = "junit", status = AttestationStatus.PASSED))
        attestationRepository.save(Attestation(trailId = trailId, type = "snyk", status = AttestationStatus.PASSED))
        attestationRepository.save(Attestation(trailId = trailId, type = "trivy", status = AttestationStatus.FAILED))

        val metrics = service.getComplianceMetrics()
        assertEquals(2, metrics.passedAttestations)
        assertEquals(1, metrics.failedAttestations)
        assertEquals(3, metrics.totalAttestations)
    }

    @Test
    fun `getComplianceMetrics with all attestations passed`() {
        val trailId = UUID.randomUUID()
        attestationRepository.save(Attestation(trailId = trailId, type = "junit", status = AttestationStatus.PASSED))
        attestationRepository.save(Attestation(trailId = trailId, type = "snyk", status = AttestationStatus.PASSED))

        val metrics = service.getComplianceMetrics()
        assertEquals(2, metrics.passedAttestations)
        assertEquals(0, metrics.failedAttestations)
    }

    @Test
    fun `getComplianceMetrics with all attestations failed`() {
        val trailId = UUID.randomUUID()
        attestationRepository.save(Attestation(trailId = trailId, type = "junit", status = AttestationStatus.FAILED))
        attestationRepository.save(Attestation(trailId = trailId, type = "snyk", status = AttestationStatus.FAILED))

        val metrics = service.getComplianceMetrics()
        assertEquals(0, metrics.passedAttestations)
        assertEquals(2, metrics.failedAttestations)
    }
}
