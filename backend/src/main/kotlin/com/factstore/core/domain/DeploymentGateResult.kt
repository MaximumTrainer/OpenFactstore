package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class GateDecision { ALLOWED, BLOCKED }

@Entity
@Table(name = "deployment_gate_results")
class DeploymentGateResult(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "policy_id") var policyId: UUID? = null,
    @Column(name = "artifact_sha256", nullable = false) val artifactSha256: String,
    @Column(name = "environment_id") val environmentId: UUID? = null,
    @Column(name = "requested_by") val requestedBy: String? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) val decision: GateDecision,
    @Column(name = "evaluated_at", nullable = false) val evaluatedAt: Instant = Instant.now(),
    @Column(name = "block_reasons", columnDefinition = "TEXT") var blockReasonsRaw: String = "",
    @Column(name = "signature_verified") var signatureVerified: Boolean? = null
) {
    var blockReasons: List<String>
        get() = if (blockReasonsRaw.isBlank()) emptyList()
                else blockReasonsRaw.split("||").map { it.trim() }.filter { it.isNotBlank() }
        set(value) { blockReasonsRaw = value.joinToString("||") }
}
