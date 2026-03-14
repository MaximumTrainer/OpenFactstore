package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "coverage_reports")
class CoverageReport(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "trail_id", nullable = false)
    var trailId: UUID,

    @Column(nullable = false)
    var tool: String,

    @Column(name = "line_coverage")
    var lineCoverage: Double? = null,

    @Column(name = "branch_coverage")
    var branchCoverage: Double? = null,

    @Column(name = "min_coverage")
    var minCoverage: Double? = null,

    @Column(nullable = false)
    var passed: Boolean,

    @Column(name = "report_file_name")
    var reportFileName: String? = null,

    @Column(name = "report_file_hash")
    var reportFileHash: String? = null,

    @Column(columnDefinition = "TEXT")
    var details: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
