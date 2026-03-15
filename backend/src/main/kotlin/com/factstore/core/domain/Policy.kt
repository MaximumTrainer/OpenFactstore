package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "policies")
class Policy(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true, nullable = false)
    var name: String,

    @Column(name = "enforce_provenance", nullable = false)
    var enforceProvenance: Boolean = false,

    @Column(name = "enforce_trail_compliance", nullable = false)
    var enforceTrailCompliance: Boolean = false,

    @Column(name = "required_attestation_types", columnDefinition = "TEXT")
    var requiredAttestationTypesRaw: String = "",

    @Column(name = "org_slug", nullable = true, length = 255)
    var orgSlug: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    var requiredAttestationTypes: List<String>
        get() = if (requiredAttestationTypesRaw.isBlank()) emptyList()
                else requiredAttestationTypesRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        set(value) { requiredAttestationTypesRaw = value.joinToString(",") }

    @Column(name = "wasm_module_content", columnDefinition = "TEXT")
    var wasmModuleContent: String? = null

    @Column(name = "policy_evaluator", nullable = false, length = 50)
    var policyEvaluator: String = "opa"
}
