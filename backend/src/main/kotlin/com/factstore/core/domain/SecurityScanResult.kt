package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class ScanType { DAST, SAST, SCA, CONTAINER }

@Entity
@Table(name = "security_scan_results")
class SecurityScanResult(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "trail_id", nullable = false) val trailId: UUID,
    @Column(name = "attestation_id") var attestationId: UUID? = null,
    @Column(nullable = false) val tool: String,
    @Column(name = "tool_version") val toolVersion: String? = null,
    @Enumerated(EnumType.STRING) @Column(name = "scan_type") val scanType: ScanType? = null,
    val target: String? = null,
    @Column(name = "critical_vulnerabilities") val criticalVulnerabilities: Int = 0,
    @Column(name = "high_vulnerabilities") val highVulnerabilities: Int = 0,
    @Column(name = "medium_vulnerabilities") val mediumVulnerabilities: Int = 0,
    @Column(name = "low_vulnerabilities") val lowVulnerabilities: Int = 0,
    val informational: Int = 0,
    @Column(name = "scan_duration_seconds") val scanDurationSeconds: Long? = null,
    @Column(name = "report_url") val reportUrl: String? = null,
    @Column(name = "org_slug") val orgSlug: String? = null,
    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)
