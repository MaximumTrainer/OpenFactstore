package com.factstore.core.domain

import jakarta.persistence.*
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

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    var requiredAttestationTypes: List<String>
        get() = if (requiredAttestationTypesRaw.isBlank()) emptyList()
                else requiredAttestationTypesRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        set(value) { requiredAttestationTypesRaw = value.joinToString(",") }

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
