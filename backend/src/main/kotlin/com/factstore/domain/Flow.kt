package com.factstore.domain

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
}
