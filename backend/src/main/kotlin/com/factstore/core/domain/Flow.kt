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

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = "",

    @Column(name = "required_attestation_types", columnDefinition = "TEXT")
    var requiredAttestationTypesRaw: String = "",

    @Column(name = "org_slug", nullable = true, length = 255)
    var orgSlug: String? = null,

    /** The hub template this flow was created from, if any (#162). */
    @Column(name = "template_id", nullable = true, length = 128)
    var templateId: String? = null,

    /** The version of that template at the time it was applied. */
    @Column(name = "template_version", nullable = true, length = 32)
    var templateVersion: String? = null,

    @Column(name = "template_yaml", columnDefinition = "TEXT")
    var templateYaml: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "archived_at", nullable = true)
    var archivedAt: Instant? = null,

    @Column(name = "previous_names", columnDefinition = "TEXT", nullable = true)
    var previousNames: String? = null
) {
    var requiredAttestationTypes: List<String>
        get() = if (requiredAttestationTypesRaw.isBlank()) emptyList()
                else requiredAttestationTypesRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        set(value) { requiredAttestationTypesRaw = value.joinToString(",") }

    val parsedPreviousNames: List<String>
        get() = previousNames?.split("|")?.filter { it.isNotBlank() } ?: emptyList()

    fun addPreviousName(name: String) {
        previousNames = (parsedPreviousNames + name).joinToString("|")
    }

    @Column(name = "requires_approval", nullable = false)
    var requiresApproval: Boolean = false

    /**
     * #240: when true, a self-asserted attestation (a status the caller claimed and the server
     * could not check) does not count towards compliance, unless its type is in
     * [acceptedSelfAssertedTypes].
     */
    @Column(name = "reject_self_asserted", nullable = false)
    var rejectSelfAsserted: Boolean = false

    @Column(name = "accepted_self_asserted_types", columnDefinition = "TEXT")
    var acceptedSelfAssertedTypesRaw: String = ""

    var acceptedSelfAssertedTypes: List<String>
        get() = if (acceptedSelfAssertedTypesRaw.isBlank()) emptyList()
                else acceptedSelfAssertedTypesRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        set(value) { acceptedSelfAssertedTypesRaw = value.joinToString(",") }

    /** Whether [attestationType]'s self-asserted evidence counts for this flow (#240). */
    fun acceptsSelfAsserted(attestationType: String): Boolean =
        !rejectSelfAsserted || attestationType in acceptedSelfAssertedTypes

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
