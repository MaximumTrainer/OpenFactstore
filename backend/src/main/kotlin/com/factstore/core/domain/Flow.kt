package com.factstore.core.domain

import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "flows")
class Flow(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true, nullable = false)
    var name: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = "",

    @Column(name = "required_attestation_types", columnDefinition = "TEXT")
    var requiredAttestationTypesRaw: String = "",

    @Column(name = "org_slug", nullable = true, length = 255)
    var orgSlug: String? = null,

    @Column(name = "template_yaml", columnDefinition = "TEXT")
    var templateYaml: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    var requiredAttestationTypes: List<String>
        get() = if (requiredAttestationTypesRaw.isBlank()) emptyList()
                else requiredAttestationTypesRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        set(value) { requiredAttestationTypesRaw = value.joinToString(",") }

    @Column(name = "requires_approval", nullable = false)
    var requiresApproval: Boolean = false

    @Column(name = "required_approver_roles", columnDefinition = "TEXT")
    var requiredApproverRolesRaw: String = ""

    var requiredApproverRoles: List<String>
        get() = if (requiredApproverRolesRaw.isBlank()) emptyList() else requiredApproverRolesRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        set(value) { requiredApproverRolesRaw = value.joinToString(",") }

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "flow_tags",
        joinColumns = [JoinColumn(name = "flow_id")],
        uniqueConstraints = [UniqueConstraint(columnNames = ["flow_id", "tag_key"])]
    )
    @MapKeyColumn(name = "tag_key", length = 64)
    @Column(name = "tag_value", length = 256)
    var tags: MutableMap<String, String> = mutableMapOf()
}
