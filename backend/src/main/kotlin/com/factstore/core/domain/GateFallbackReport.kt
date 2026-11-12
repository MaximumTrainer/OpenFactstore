package com.factstore.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A client-side gate fallback that has been reported and audited (#193). */
@Entity
@Table(name = "gate_fallback_reports")
class GateFallbackReport(
    @Id
    @Column(name = "report_id")
    val reportId: UUID,

    @Column(name = "audit_id", nullable = false)
    val auditId: UUID,

    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant = Instant.now()
)
