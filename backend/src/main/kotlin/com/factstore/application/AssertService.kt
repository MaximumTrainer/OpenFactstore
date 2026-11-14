package com.factstore.application

import com.factstore.application.policy.PolicyParser
import com.factstore.application.policy.PolicyExpressionEvaluator
import com.factstore.application.template.TemplateParser
import com.factstore.application.template.ParsedTemplate
import com.factstore.core.domain.Artifact
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.ApprovalStatus
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.Flow
import com.factstore.core.domain.Policy
import com.factstore.core.domain.Trail
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.inbound.IAssertService
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IApprovalRepository
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.IPolicyRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.AssertRequest
import com.factstore.dto.AssertResponse
import com.factstore.dto.ComplianceStatus
import com.factstore.exception.BadRequestException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Evaluates an artifact/trail against a flow's requirements.
 *
 * An assertion is always decided against exactly one trail — the pipeline execution being judged:
 *
 *  - `trailId` supplied (or [assertTrail]) → that trail decides, and a digest that belongs to a
 *    different trail is rejected.
 *  - digest only → the **most recent** trail carrying that digest decides. A re-run of the same
 *    commit therefore has to earn its own verdict rather than inheriting the previous run's.
 *
 * The deciding trail's [TrailStatus] is written in the same transaction as the
 * `GATE_ALLOWED`/`GATE_BLOCKED` audit event, so status and audit log cannot diverge.
 */
@Service
@Transactional
class AssertService(
    private val artifactRepository: IArtifactRepository,
    private val attestationRepository: IAttestationRepository,
    private val flowRepository: IFlowRepository,
    private val trailRepository: ITrailRepository,
    private val auditService: IAuditService,
    private val approvalRepository: IApprovalRepository,
    private val templateParser: TemplateParser,
    private val policyParser: PolicyParser,
    private val policyRepository: IPolicyRepository,
    private val policyExpressionEvaluator: PolicyExpressionEvaluator,
    private val actorResolver: ActorResolver
) : IAssertService {

    private val log = LoggerFactory.getLogger(AssertService::class.java)

    override fun assertCompliance(request: AssertRequest): AssertResponse {
        val flow = flowRepository.findById(request.flowId)
            ?: throw NotFoundException("Flow not found: ${request.flowId}")

        request.trailId?.let { trailId ->
            val trail = trailRepository.findById(trailId)
                ?: throw NotFoundException("Trail not found: $trailId")
            val artifact = artifactForTrail(trail.id, request.sha256Digest)
            return evaluate(request.sha256Digest, flow, trail, artifact)
        }

        val candidates = artifactRepository.findBySha256Digest(request.sha256Digest)
            .mapNotNull { artifact -> trailRepository.findById(artifact.trailId)?.let { it to artifact } }

        if (candidates.isEmpty()) {
            val response = AssertResponse(
                sha256Digest = request.sha256Digest,
                flowId = request.flowId,
                status = ComplianceStatus.NON_COMPLIANT,
                missingAttestationTypes = flow.requiredAttestationTypes,
                failedAttestationTypes = emptyList(),
                details = "No artifacts found with digest ${request.sha256Digest}"
            )
            emitPolicyEvent(response)
            return response
        }

        // Deterministic and honest: the newest execution carrying this digest is the one judged.
        val (trail, artifact) = candidates.maxWith(
            compareBy({ it.first.createdAt }, { it.second.reportedAt }, { it.second.id })
        )
        return evaluate(request.sha256Digest, flow, trail, artifact)
    }

    override fun assertTrail(trailId: UUID, flowId: UUID?, sha256Digest: String?): AssertResponse {
        val trail = trailRepository.findById(trailId)
            ?: throw NotFoundException("Trail not found: $trailId")
        val effectiveFlowId = flowId ?: trail.flowId
        val flow = flowRepository.findById(effectiveFlowId)
            ?: throw NotFoundException("Flow not found: $effectiveFlowId")

        val artifact = if (!sha256Digest.isNullOrBlank()) {
            artifactForTrail(trail.id, sha256Digest)
        } else {
            // Gates that run before the image is pushed have no digest to key on;
            // fall back to the most recently reported artifact, or none at all.
            artifactRepository.findByTrailId(trail.id).maxByOrNull { it.reportedAt }
        }

        return evaluate(artifact?.sha256Digest ?: sha256Digest.orEmpty(), flow, trail, artifact)
    }

    /**
     * Resolves the artifact with [digest] on [trailId], rejecting a digest that belongs elsewhere.
     * A blank digest means "no artifact yet" and is allowed.
     */
    private fun artifactForTrail(trailId: UUID, digest: String?): Artifact? {
        if (digest.isNullOrBlank()) return null
        return artifactRepository.findBySha256Digest(digest).firstOrNull { it.trailId == trailId }
            ?: throw BadRequestException(
                "Artifact digest $digest does not belong to trail $trailId"
            )
    }

    private fun evaluate(digest: String, flow: Flow, trail: Trail, artifact: Artifact?): AssertResponse {
        val effectiveTemplateYaml = trail.templateYaml ?: flow.templateYaml
        val response = if (effectiveTemplateYaml != null) {
            evaluateWithTemplate(digest, flow, trail, artifact, effectiveTemplateYaml)
        } else {
            evaluateWithRequiredTypes(digest, flow, trail)
        }
        applyTrailStatus(trail, response.status)
        emitPolicyEvent(response)
        return response
    }

    private fun evaluateWithRequiredTypes(digest: String, flow: Flow, trail: Trail): AssertResponse {
        val required = flow.requiredAttestationTypes
        if (required.isEmpty()) {
            return AssertResponse(
                sha256Digest = digest,
                flowId = flow.id,
                status = ComplianceStatus.COMPLIANT,
                missingAttestationTypes = emptyList(),
                failedAttestationTypes = emptyList(),
                details = "Flow has no required attestation types; artifact is compliant",
                trailId = trail.id
            )
        }

        val attestations = attestationRepository.findByTrailId(trail.id)
        val evidence = SelfAssertion.classify(required, attestations, flow) { it.type }
        val passedTypes = evidence.passed
        val failedTypes = attestations.filter { it.status == AttestationStatus.FAILED }.map { it.type }
        val missing = required.filter { it !in passedTypes }

        if (missing.isEmpty()) {
            approvalGap(flow, trail, digest)?.let { return it }
            log.info("Artifact $digest is COMPLIANT for flow ${flow.id} (trail ${trail.id})")
            return AssertResponse(
                sha256Digest = digest,
                flowId = flow.id,
                status = ComplianceStatus.COMPLIANT,
                missingAttestationTypes = emptyList(),
                failedAttestationTypes = failedTypes,
                details = "All required attestations passed${evidence.note()}",
                trailId = trail.id,
                selfAssertedAttestationTypes = evidence.selfAsserted
            )
        }

        log.info("Artifact $digest is NON_COMPLIANT for flow ${flow.id} (trail ${trail.id}); missing: $missing")
        return AssertResponse(
            sha256Digest = digest,
            flowId = flow.id,
            status = ComplianceStatus.NON_COMPLIANT,
            missingAttestationTypes = missing,
            failedAttestationTypes = failedTypes,
            details = "Missing required attestations: ${missing.joinToString(", ")}${evidence.note()}",
            trailId = trail.id,
            selfAssertedAttestationTypes = evidence.selfAsserted,
            rejectedSelfAssertedAttestationTypes = evidence.rejected
        )
    }

    private fun evaluateWithTemplate(
        digest: String,
        flow: Flow,
        trail: Trail,
        artifact: Artifact?,
        templateYaml: String
    ): AssertResponse {
        val parsed = templateParser.parse(templateYaml)
        val allRequired = computeRequired(flow, artifact?.imageName, parsed)

        val attestations = attestationRepository.findByTrailId(trail.id)
        val evidence = SelfAssertion.classify(allRequired, attestations, flow) { it.name }
        val passedNames = evidence.passed
        val failedNames = attestations.filter { it.status == AttestationStatus.FAILED }.mapNotNull { it.name }
        val missing = allRequired.filter { it !in passedNames }

        if (missing.isEmpty()) {
            approvalGap(flow, trail, digest)?.let { return it }
            log.info("Artifact $digest is COMPLIANT (template) for flow ${flow.id} (trail ${trail.id})")
            return AssertResponse(
                sha256Digest = digest,
                flowId = flow.id,
                status = ComplianceStatus.COMPLIANT,
                missingAttestationTypes = emptyList(),
                failedAttestationTypes = emptyList(),
                missingAttestationNames = emptyList(),
                failedAttestationNames = failedNames,
                details = "All required attestations passed${evidence.note()}",
                trailId = trail.id,
                selfAssertedAttestationTypes = evidence.selfAsserted
            )
        }

        log.info("Artifact $digest is NON_COMPLIANT (template) for flow ${flow.id} (trail ${trail.id}); missing: $missing")
        return AssertResponse(
            sha256Digest = digest,
            flowId = flow.id,
            status = ComplianceStatus.NON_COMPLIANT,
            missingAttestationTypes = emptyList(),
            failedAttestationTypes = emptyList(),
            missingAttestationNames = missing,
            failedAttestationNames = failedNames,
            details = "Missing required attestations: ${missing.joinToString(", ")}${evidence.note()}",
            trailId = trail.id,
            selfAssertedAttestationTypes = evidence.selfAsserted,
            rejectedSelfAssertedAttestationTypes = evidence.rejected
        )
    }

    /** Returns a NON_COMPLIANT response when the flow needs an approval that has not been granted. */
    private fun approvalGap(flow: Flow, trail: Trail, digest: String): AssertResponse? {
        if (!flow.requiresApproval) return null
        val approved = approvalRepository.findByTrailId(trail.id).any { it.status == ApprovalStatus.APPROVED }
        if (approved) return null
        return AssertResponse(
            sha256Digest = digest,
            flowId = flow.id,
            status = ComplianceStatus.NON_COMPLIANT,
            missingAttestationTypes = emptyList(),
            failedAttestationTypes = emptyList(),
            details = "Approval required but not yet granted",
            trailId = trail.id
        )
    }

    /** #158: the trail carries the verdict of the most recent evaluation made against it. */
    private fun applyTrailStatus(trail: Trail, status: ComplianceStatus) {
        val newStatus = when (status) {
            ComplianceStatus.COMPLIANT -> TrailStatus.COMPLIANT
            ComplianceStatus.NON_COMPLIANT -> TrailStatus.NON_COMPLIANT
        }
        if (trail.status == newStatus) return
        trail.status = newStatus
        trail.updatedAt = Instant.now()
        trailRepository.save(trail)
        log.info("Trail ${trail.id} status -> $newStatus")
    }

    private fun computeRequired(
        flow: Flow,
        artifactName: String?,
        effectiveParsed: ParsedTemplate?
    ): List<String> {
        val evalCtx = PolicyExpressionEvaluator.EvaluationContext(
            flowName = flow.name,
            artifactName = artifactName
        )
        val trailRequired = (effectiveParsed?.trailAttestations ?: emptyList())
            .filter { it.ifCondition == null || policyExpressionEvaluator.evaluate(it.ifCondition, evalCtx) }
            .map { it.name }
        val artifactEntry = effectiveParsed?.artifacts?.firstOrNull { it.name == artifactName }
        val artifactRequired = (artifactEntry?.attestations ?: emptyList())
            .filter { it.ifCondition == null || policyExpressionEvaluator.evaluate(it.ifCondition, evalCtx) }
            .map { it.name }
        val policyRequired = mutableListOf<String>()
        for (policy in policyRepository.findAll()) {
            if (policy.orgSlug != null && policy.orgSlug != flow.orgSlug) continue
            val policyYamlContent = policy.policyYaml ?: continue
            val parsed = policyParser.parse(policyYamlContent) ?: continue
            parsed.artifactRules.requiredAttestations
                .filter { rule -> rule.ifCondition == null || policyExpressionEvaluator.evaluate(rule.ifCondition, evalCtx) }
                .mapTo(policyRequired) { rule -> rule.name }
        }
        return (trailRequired + artifactRequired + policyRequired).distinct()
    }

    private fun emitPolicyEvent(response: AssertResponse) {
        val eventType = if (response.status == ComplianceStatus.COMPLIANT)
            AuditEventType.GATE_ALLOWED else AuditEventType.GATE_BLOCKED
        auditService.record(
            eventType = eventType,
            // Whoever asked for this decision owns it. A gate event attributed to "system"
            // cannot answer "who released this" (#156 FR-7.1).
            actor = actorResolver.current(),
            payload = mapOf(
                "sha256Digest" to response.sha256Digest,
                "flowId" to response.flowId.toString(),
                "trailId" to (response.trailId?.toString() ?: ""),
                "status" to response.status.name,
                "missingAttestationTypes" to response.missingAttestationTypes,
                "failedAttestationTypes" to response.failedAttestationTypes
            ),
            trailId = response.trailId,
            artifactSha256 = response.sha256Digest
        )
    }
}

/**
 * #240: which required attestations are satisfied, and which of those rest only on the caller's
 * word. A self-asserted PASSED counts unless the flow refuses self-assertion for that type.
 */
data class SelfAssertion(
    val passed: Set<String>,
    val selfAsserted: List<String>,
    val rejected: List<String>
) {
    fun note(): String = buildString {
        if (selfAsserted.isNotEmpty()) append("; self-asserted (not verified by the server): ${selfAsserted.joinToString(", ")}")
        if (rejected.isNotEmpty()) append("; self-asserted evidence not accepted by this flow: ${rejected.joinToString(", ")}")
    }

    companion object {
        fun classify(
            required: List<String>,
            attestations: List<com.factstore.core.domain.Attestation>,
            flow: Flow,
            key: (com.factstore.core.domain.Attestation) -> String?
        ): SelfAssertion {
            val passing = attestations.filter { it.status == AttestationStatus.PASSED }
            val verified = passing.filter { !it.selfAsserted }.mapNotNull(key).toSet()
            val acceptedClaims = passing.filter { it.selfAsserted && flow.acceptsSelfAsserted(it.type) }.mapNotNull(key).toSet()
            val refusedClaims = passing.filter { it.selfAsserted && !flow.acceptsSelfAsserted(it.type) }.mapNotNull(key).toSet()
            val passed = verified + acceptedClaims
            return SelfAssertion(
                passed = passed,
                selfAsserted = required.filter { it in acceptedClaims && it !in verified },
                rejected = required.filter { it in refusedClaims && it !in passed }
            )
        }
    }
}
