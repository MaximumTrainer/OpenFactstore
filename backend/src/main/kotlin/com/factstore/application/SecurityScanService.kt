package com.factstore.application

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.SecurityScanResult
import com.factstore.core.domain.SecurityThreshold
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.inbound.ISecurityScanService
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.ISecurityScanRepository
import com.factstore.core.port.outbound.ISecurityThresholdRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.*
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class SecurityScanService(
    private val securityScanRepository: ISecurityScanRepository,
    private val securityThresholdRepository: ISecurityThresholdRepository,
    private val attestationRepository: IAttestationRepository,
    private val trailRepository: ITrailRepository
) : ISecurityScanService {

    private val log = LoggerFactory.getLogger(SecurityScanService::class.java)
    private val objectMapper = jacksonObjectMapper()

    override fun recordScan(trailId: UUID, request: RecordSecurityScanRequest): SecurityScanResponse {
        val trail = trailRepository.findById(trailId) ?: throw NotFoundException("Trail not found: $trailId")
        val threshold = securityThresholdRepository.findByFlowId(trail.flowId)

        val scan = SecurityScanResult(
            trailId = trailId,
            tool = request.tool,
            toolVersion = request.toolVersion,
            scanType = request.scanType,
            target = request.target,
            criticalVulnerabilities = request.criticalVulnerabilities,
            highVulnerabilities = request.highVulnerabilities,
            mediumVulnerabilities = request.mediumVulnerabilities,
            lowVulnerabilities = request.lowVulnerabilities,
            informational = request.informational,
            scanDurationSeconds = request.scanDurationSeconds,
            reportUrl = request.reportUrl,
            orgSlug = request.orgSlug
        )

        val evaluation = if (threshold != null) evaluateThresholds(scan, threshold) else ThresholdEvaluationResult(true, emptyList())
        val attestationStatus = if (evaluation.passed) AttestationStatus.PASSED else AttestationStatus.FAILED

        val details = objectMapper.writeValueAsString(mapOf(
            "tool" to request.tool,
            "toolVersion" to request.toolVersion,
            "scanType" to request.scanType?.name,
            "target" to request.target,
            "criticalVulnerabilities" to request.criticalVulnerabilities,
            "highVulnerabilities" to request.highVulnerabilities,
            "mediumVulnerabilities" to request.mediumVulnerabilities,
            "lowVulnerabilities" to request.lowVulnerabilities,
            "informational" to request.informational,
            "scanDurationSeconds" to request.scanDurationSeconds,
            "reportUrl" to request.reportUrl
        ))

        val attestation = attestationRepository.save(Attestation(
            trailId = trailId,
            type = "SECURITY_SCAN",
            status = attestationStatus,
            details = details,
            name = "Security scan: ${request.tool}",
            orgSlug = request.orgSlug
        ))

        scan.attestationId = attestation.id
        val savedScan = securityScanRepository.save(scan)

        if (attestationStatus == AttestationStatus.FAILED) {
            trail.status = TrailStatus.NON_COMPLIANT
            trail.updatedAt = Instant.now()
            trailRepository.save(trail)
        }

        log.info("Recorded security scan: ${savedScan.id} tool=${savedScan.tool} status=$attestationStatus")
        return savedScan.toResponse(!evaluation.passed, evaluation.breaches)
    }

    @Transactional(readOnly = true)
    override fun listScans(trailId: UUID): List<SecurityScanResponse> {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        return securityScanRepository.findByTrailId(trailId).map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    override fun getScan(id: UUID): SecurityScanResponse {
        val scan = securityScanRepository.findById(id) ?: throw NotFoundException("Security scan not found: $id")
        return scan.toResponse()
    }

    @Transactional(readOnly = true)
    override fun getSummary(): SecurityScanSummaryResponse {
        val scans = securityScanRepository.findAll()
        return SecurityScanSummaryResponse(
            totalScans = scans.size,
            totalCritical = scans.sumOf { it.criticalVulnerabilities },
            totalHigh = scans.sumOf { it.highVulnerabilities },
            totalMedium = scans.sumOf { it.mediumVulnerabilities },
            totalLow = scans.sumOf { it.lowVulnerabilities },
            scansWithCritical = scans.count { it.criticalVulnerabilities > 0 }
        )
    }

    override fun setThresholds(flowId: UUID, request: SetSecurityThresholdRequest): SecurityThresholdResponse {
        val existing = securityThresholdRepository.findByFlowId(flowId)
        val threshold = if (existing != null) {
            existing.maxCritical = request.maxCritical
            existing.maxHigh = request.maxHigh
            existing.maxMedium = request.maxMedium
            existing.maxLow = request.maxLow
            existing.updatedAt = Instant.now()
            existing
        } else {
            SecurityThreshold(
                flowId = flowId,
                maxCritical = request.maxCritical,
                maxHigh = request.maxHigh,
                maxMedium = request.maxMedium,
                maxLow = request.maxLow
            )
        }
        val saved = securityThresholdRepository.save(threshold)
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun getThresholds(flowId: UUID): SecurityThresholdResponse {
        val threshold = securityThresholdRepository.findByFlowId(flowId)
            ?: throw NotFoundException("Security thresholds not found for flow: $flowId")
        return threshold.toResponse()
    }

    override fun evaluateThresholds(scanResult: SecurityScanResult, threshold: SecurityThreshold): ThresholdEvaluationResult {
        val breaches = mutableListOf<String>()
        if (scanResult.criticalVulnerabilities > threshold.maxCritical)
            breaches.add("Critical vulnerabilities ${scanResult.criticalVulnerabilities} exceeds threshold ${threshold.maxCritical}")
        if (scanResult.highVulnerabilities > threshold.maxHigh)
            breaches.add("High vulnerabilities ${scanResult.highVulnerabilities} exceeds threshold ${threshold.maxHigh}")
        if (scanResult.mediumVulnerabilities > threshold.maxMedium)
            breaches.add("Medium vulnerabilities ${scanResult.mediumVulnerabilities} exceeds threshold ${threshold.maxMedium}")
        if (scanResult.lowVulnerabilities > threshold.maxLow)
            breaches.add("Low vulnerabilities ${scanResult.lowVulnerabilities} exceeds threshold ${threshold.maxLow}")
        return ThresholdEvaluationResult(breaches.isEmpty(), breaches)
    }
}

fun SecurityScanResult.toResponse(thresholdBreached: Boolean = false, breachDetails: List<String> = emptyList()) = SecurityScanResponse(
    id = id,
    trailId = trailId,
    attestationId = attestationId,
    tool = tool,
    toolVersion = toolVersion,
    scanType = scanType,
    target = target,
    criticalVulnerabilities = criticalVulnerabilities,
    highVulnerabilities = highVulnerabilities,
    mediumVulnerabilities = mediumVulnerabilities,
    lowVulnerabilities = lowVulnerabilities,
    informational = informational,
    scanDurationSeconds = scanDurationSeconds,
    reportUrl = reportUrl,
    orgSlug = orgSlug,
    createdAt = createdAt,
    thresholdBreached = thresholdBreached,
    breachDetails = breachDetails
)

fun SecurityThreshold.toResponse() = SecurityThresholdResponse(
    id = id,
    flowId = flowId,
    maxCritical = maxCritical,
    maxHigh = maxHigh,
    maxMedium = maxMedium,
    maxLow = maxLow,
    createdAt = createdAt,
    updatedAt = updatedAt
)
