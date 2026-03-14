package com.factstore.application

import com.factstore.core.domain.ApprovalStatus
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.DeploymentGateResult
import com.factstore.core.domain.DeploymentPolicy
import com.factstore.core.domain.GateDecision
import com.factstore.core.port.inbound.IDeploymentPolicyService
import com.factstore.core.port.inbound.IEnvironmentAllowlistService
import com.factstore.core.port.outbound.IApprovalRepository
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IBuildProvenanceRepository
import com.factstore.core.port.outbound.IDeploymentGateResultRepository
import com.factstore.core.port.outbound.IDeploymentPolicyRepository
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.dto.CreateDeploymentPolicyRequest
import com.factstore.dto.DeploymentPolicyResponse
import com.factstore.dto.GateEvaluateRequest
import com.factstore.dto.GateEvaluateResponse
import com.factstore.dto.UpdateDeploymentPolicyRequest
import com.factstore.exception.NotFoundException
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class DeploymentPolicyService(
    private val policyRepository: IDeploymentPolicyRepository,
    private val gateResultRepository: IDeploymentGateResultRepository,
    private val artifactRepository: IArtifactRepository,
    private val attestationRepository: IAttestationRepository,
    private val buildProvenanceRepository: IBuildProvenanceRepository,
    private val approvalRepository: IApprovalRepository,
    private val flowRepository: IFlowRepository,
    private val allowlistService: IEnvironmentAllowlistService,
    private val meterRegistry: MeterRegistry
) : IDeploymentPolicyService {

    private val log = LoggerFactory.getLogger(DeploymentPolicyService::class.java)

    private val gateEvaluationsCounter: Counter by lazy {
        Counter.builder("factstore_gate_evaluations_total")
            .description("Total number of deployment gate evaluations")
            .register(meterRegistry)
    }
    private val gateAllowedCounter: Counter by lazy {
        Counter.builder("factstore_gate_allowed_total")
            .description("Deployment gate evaluations that resulted in ALLOWED")
            .register(meterRegistry)
    }
    private val gateBlockedCounter: Counter by lazy {
        Counter.builder("factstore_gate_blocked_total")
            .description("Deployment gate evaluations that resulted in BLOCKED")
            .register(meterRegistry)
    }

    override fun createPolicy(request: CreateDeploymentPolicyRequest): DeploymentPolicyResponse {
        flowRepository.findById(request.flowId)
            ?: throw NotFoundException("Flow not found: ${request.flowId}")

        val policy = DeploymentPolicy(
            name = request.name,
            description = request.description,
            flowId = request.flowId,
            environmentId = request.environmentId,
            enforceProvenance = request.enforceProvenance,
            enforceApprovals = request.enforceApprovals
        ).also { it.requiredAttestationTypes = request.requiredAttestationTypes }

        return policyRepository.save(policy).toResponse()
    }

    override fun listPolicies(): List<DeploymentPolicyResponse> =
        policyRepository.findAll().map { it.toResponse() }

    override fun getPolicy(id: UUID): DeploymentPolicyResponse =
        policyRepository.findById(id)?.toResponse()
            ?: throw NotFoundException("Deployment policy not found: $id")

    override fun updatePolicy(id: UUID, request: UpdateDeploymentPolicyRequest): DeploymentPolicyResponse {
        val policy = policyRepository.findById(id)
            ?: throw NotFoundException("Deployment policy not found: $id")

        request.name?.let { policy.name = it }
        request.description?.let { policy.description = it }
        request.enforceProvenance?.let { policy.enforceProvenance = it }
        request.enforceApprovals?.let { policy.enforceApprovals = it }
        request.requiredAttestationTypes?.let { policy.requiredAttestationTypes = it }
        request.isActive?.let { policy.isActive = it }
        policy.updatedAt = Instant.now()

        return policyRepository.save(policy).toResponse()
    }

    override fun deletePolicy(id: UUID) {
        if (!policyRepository.existsById(id)) throw NotFoundException("Deployment policy not found: $id")
        policyRepository.deleteById(id)
    }

    override fun evaluateGate(request: GateEvaluateRequest): GateEvaluateResponse {
        gateEvaluationsCounter.increment()

        // Allow-listed artifacts bypass all policy checks
        if (request.environmentId != null &&
            allowlistService.isAllowlisted(request.environmentId, request.artifactSha256, null)) {
            log.info("Artifact ${request.artifactSha256} is allow-listed for environment ${request.environmentId} — gate ALLOWED")
            gateAllowedCounter.increment()
            val result = DeploymentGateResult(
                policyId = null,
                artifactSha256 = request.artifactSha256,
                environmentId = request.environmentId,
                requestedBy = request.requestedBy,
                decision = GateDecision.ALLOWED
            ).also { it.blockReasons = emptyList() }
            return gateResultRepository.save(result).toResponse(0)
        }

        val policies = if (request.environmentId != null)
            policyRepository.findByEnvironmentId(request.environmentId).filter { it.isActive }
                .ifEmpty { policyRepository.findActive() }
        else
            policyRepository.findActive()

        val blockReasons = mutableListOf<String>()
        var lastPolicyId: UUID? = null

        for (policy in policies) {
            lastPolicyId = policy.id

            if (policy.requiredAttestationTypes.isNotEmpty()) {
                val artifacts = artifactRepository.findBySha256Digest(request.artifactSha256)
                if (artifacts.isEmpty()) {
                    blockReasons.add("No artifacts found with digest: ${request.artifactSha256}")
                } else {
                    for (artifact in artifacts) {
                        val attestations = attestationRepository.findByTrailId(artifact.trailId)
                        val passedTypes = attestations
                            .filter { it.status == AttestationStatus.PASSED }
                            .map { it.type }
                            .toSet()
                        policy.requiredAttestationTypes.forEach { required ->
                            if (required !in passedTypes) {
                                blockReasons.add("Missing attestation: $required (policy: ${policy.name})")
                            }
                        }
                    }
                }
            }

            if (policy.enforceProvenance) {
                val artifacts = artifactRepository.findBySha256Digest(request.artifactSha256)
                for (artifact in artifacts) {
                    val hasProvenance = buildProvenanceRepository.findByArtifactId(artifact.id) != null
                    if (!hasProvenance) {
                        blockReasons.add("No build provenance found for artifact (policy: ${policy.name})")
                    }
                }
            }

            if (policy.enforceApprovals) {
                val artifacts = artifactRepository.findBySha256Digest(request.artifactSha256)
                for (artifact in artifacts) {
                    val approvals = approvalRepository.findByTrailId(artifact.trailId)
                    if (approvals.none { it.status == ApprovalStatus.APPROVED }) {
                        blockReasons.add("No approved release approval found (policy: ${policy.name})")
                    }
                }
            }
        }

        val decision = if (blockReasons.isEmpty()) GateDecision.ALLOWED else GateDecision.BLOCKED
        if (decision == GateDecision.ALLOWED) gateAllowedCounter.increment() else gateBlockedCounter.increment()
        val result = DeploymentGateResult(
            policyId = lastPolicyId,
            artifactSha256 = request.artifactSha256,
            environmentId = request.environmentId,
            requestedBy = request.requestedBy,
            decision = decision
        ).also { it.blockReasons = blockReasons }

        log.info("Gate evaluation for ${request.artifactSha256}: $decision (${policies.size} policies, ${blockReasons.size} block reasons)")

        return gateResultRepository.save(result).toResponse(policies.size)
    }

    override fun listGateResults(): List<GateEvaluateResponse> =
        gateResultRepository.findAll().map { it.toResponse(0) }

    private fun DeploymentPolicy.toResponse() = DeploymentPolicyResponse(
        id = id,
        name = name,
        description = description,
        flowId = flowId,
        environmentId = environmentId,
        enforceProvenance = enforceProvenance,
        enforceApprovals = enforceApprovals,
        requiredAttestationTypes = requiredAttestationTypes,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun DeploymentGateResult.toResponse(policiesEvaluated: Int) = GateEvaluateResponse(
        id = id,
        decision = decision,
        artifactSha256 = artifactSha256,
        environmentId = environmentId,
        evaluatedAt = evaluatedAt,
        blockReasons = blockReasons,
        policiesEvaluated = policiesEvaluated
    )
}
