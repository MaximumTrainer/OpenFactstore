package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "deployment_policies")
class DeploymentPolicy(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false) var name: String,
    @Column(columnDefinition = "TEXT") var description: String = "",
    @Column(name = "environment_id") var environmentId: UUID? = null,
    @Column(name = "flow_id", nullable = false) var flowId: UUID,
    @Column(name = "enforce_provenance", nullable = false) var enforceProvenance: Boolean = false,
    @Column(name = "enforce_approvals", nullable = false) var enforceApprovals: Boolean = false,
    @Column(name = "required_attestation_types", columnDefinition = "TEXT") var requiredAttestationTypesRaw: String = "",
    @Column(name = "is_active", nullable = false) var isActive: Boolean = true,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
    @Column(name = "require_signature", nullable = false) var requireSignature: Boolean = false
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
