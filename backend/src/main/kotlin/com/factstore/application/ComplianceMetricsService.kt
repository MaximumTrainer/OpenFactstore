package com.factstore.application

import com.factstore.core.domain.ApprovalStatus
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.outbound.IApprovalRepository
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IDriftReportRepository
import com.factstore.core.port.outbound.ISecurityScanRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.ComplianceMetricsSummary
import com.factstore.dto.SecurityMetricsSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ComplianceMetricsService(
    private val meterRegistry: MeterRegistry,
    private val trailRepository: ITrailRepository,
    private val attestationRepository: IAttestationRepository,
    private val securityScanRepository: ISecurityScanRepository,
    private val approvalRepository: IApprovalRepository,
    private val driftReportRepository: IDriftReportRepository
) {

    @PostConstruct
    fun registerMetrics() {
        Gauge.builder("factstore_trails_total") { trailRepository.countAll().toDouble() }
            .description("Total number of trails").register(meterRegistry)

        Gauge.builder("factstore_trails_compliant") { trailRepository.countByStatus(TrailStatus.COMPLIANT).toDouble() }
            .description("Currently compliant trails").register(meterRegistry)

        Gauge.builder("factstore_trails_non_compliant") { trailRepository.countByStatus(TrailStatus.NON_COMPLIANT).toDouble() }
            .description("Currently non-compliant trails").register(meterRegistry)

        Gauge.builder("factstore_attestations_total") { attestationRepository.findAll().size.toDouble() }
            .description("Total number of attestations").register(meterRegistry)

        Gauge.builder("factstore_attestations_passed") {
            attestationRepository.findAll().count { it.status == AttestationStatus.PASSED }.toDouble()
        }.description("Passed attestations").register(meterRegistry)

        Gauge.builder("factstore_attestations_failed") {
            attestationRepository.findAll().count { it.status == AttestationStatus.FAILED }.toDouble()
        }.description("Failed attestations").register(meterRegistry)

        Gauge.builder("factstore_security_scans_total") { securityScanRepository.findAll().size.toDouble() }
            .description("Total security scans").register(meterRegistry)

        Gauge.builder("factstore_security_scans_passed") {
            securityScanRepository.findAll()
                .count { it.criticalVulnerabilities == 0 && it.highVulnerabilities == 0 }.toDouble()
        }.description("Security scans with no critical or high vulnerabilities").register(meterRegistry)

        Gauge.builder("factstore_security_scans_failed") {
            securityScanRepository.findAll()
                .count { it.criticalVulnerabilities > 0 || it.highVulnerabilities > 0 }.toDouble()
        }.description("Security scans with critical or high vulnerabilities").register(meterRegistry)

        Gauge.builder("factstore_compliance_rate") { computeComplianceRate() }
            .description("Compliance rate as a percentage (0-100)").register(meterRegistry)

        Gauge.builder("factstore_approvals_pending") {
            approvalRepository.findByStatus(ApprovalStatus.PENDING_APPROVAL).size.toDouble()
        }.description("Current number of pending approvals").register(meterRegistry)

        Gauge.builder("factstore_drift_detected") {
            driftReportRepository.countByHasDrift(true).toDouble()
        }.description("Number of drift reports that detected drift").register(meterRegistry)
    }

    fun getComplianceMetrics(): ComplianceMetricsSummary {
        val total = trailRepository.countAll().toInt()
        val compliant = trailRepository.countByStatus(TrailStatus.COMPLIANT).toInt()
        val nonCompliant = trailRepository.countByStatus(TrailStatus.NON_COMPLIANT).toInt()
        val attestations = attestationRepository.findAll()
        return ComplianceMetricsSummary(
            totalTrails = total,
            compliantTrails = compliant,
            nonCompliantTrails = nonCompliant,
            complianceRate = if (total == 0) 0.0 else compliant.toDouble() / total * 100,
            totalAttestations = attestations.size,
            passedAttestations = attestations.count { it.status == AttestationStatus.PASSED },
            failedAttestations = attestations.count { it.status == AttestationStatus.FAILED },
            generatedAt = Instant.now()
        )
    }

    fun getSecurityMetrics(): SecurityMetricsSummary {
        val scans = securityScanRepository.findAll()
        return SecurityMetricsSummary(
            totalScans = scans.size,
            passedScans = scans.count { it.criticalVulnerabilities == 0 && it.highVulnerabilities == 0 },
            failedScans = scans.count { it.criticalVulnerabilities > 0 || it.highVulnerabilities > 0 },
            totalCritical = scans.sumOf { it.criticalVulnerabilities },
            totalHigh = scans.sumOf { it.highVulnerabilities },
            totalMedium = scans.sumOf { it.mediumVulnerabilities },
            totalLow = scans.sumOf { it.lowVulnerabilities },
            generatedAt = Instant.now()
        )
    }

    private fun computeComplianceRate(): Double {
        val total = trailRepository.countAll()
        if (total == 0L) return 0.0
        return trailRepository.countByStatus(TrailStatus.COMPLIANT).toDouble() / total * 100
    }
}
