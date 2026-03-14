package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "regulatory_controls")
class RegulatoryControl(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "framework_id", nullable = false) val frameworkId: UUID,
    @Column(name = "control_id", nullable = false) val controlId: String,
    @Column(nullable = false) val title: String,
    @Column(columnDefinition = "TEXT") val description: String? = null,
    @Column(name = "required_evidence_types", columnDefinition = "TEXT") val requiredEvidenceTypes: String? = null,
    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)
