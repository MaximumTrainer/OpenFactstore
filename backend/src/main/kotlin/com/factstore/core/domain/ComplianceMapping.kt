package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "compliance_mappings")
class ComplianceMapping(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "control_id", nullable = false) val regulatoryControlId: UUID,
    @Column(name = "flow_id", nullable = false) val flowId: UUID,
    @Column(name = "attestation_type", nullable = false) val attestationType: String,
    @Column(name = "is_mandatory") val isMandatory: Boolean = true,
    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)
