package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class AssessmentStatus { COMPLIANT, NON_COMPLIANT, PARTIAL }

@Entity
@Table(name = "compliance_assessments")
class ComplianceAssessment(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "framework_id", nullable = false) val frameworkId: UUID,
    @Column(name = "trail_id", nullable = false) val trailId: UUID,
    @Enumerated(EnumType.STRING) @Column(name = "overall_status", nullable = false) var overallStatus: AssessmentStatus,
    @Column(name = "control_results_json", columnDefinition = "TEXT") var controlResultsJson: String? = null,
    @Column(name = "org_slug") val orgSlug: String? = null,
    @Column(name = "assessed_at") val assessedAt: Instant = Instant.now()
)
