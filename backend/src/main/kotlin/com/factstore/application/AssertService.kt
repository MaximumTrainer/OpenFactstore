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
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

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
    private val policyExpressionEvaluator: PolicyExpressionEvaluator
) : IAssertService {

    private val log = LoggerFactory.getLogger(AssertService::class.java)

    override fun assertCompliance(request: AssertRequest): AssertResponse {
        val flow = flowRepository.findById(request.flowId)
            ?: throw NotFoundException("Flow not found: ${request.flowId}")

        val artifacts = artifactRepository.findBySha256Digest(request.sha256Digest)
        if (artifacts.isEmpty()) {
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

        // Check if any artifact's trail has a template, or the flow does
        val firstArtifact = artifacts.first()
        val firstTrail = trailRepository.findById(firstArtifact.trailId)
        val effectiveTemplateYaml = firstTrail?.templateYaml ?: flow.templateYaml

        if (effectiveTemplateYaml != null) {
            return assertWithTemplate(request, flow, artifacts, effectiveTemplateYaml)
        }

        // Legacy: fall back to requiredAttestationTypes
        val required = flow.requiredAttestationTypes
        if (required.isEmpty()) {
            val response = AssertResponse(
                sha256Digest = request.sha256Digest,
                flowId = request.flowId,
                status = ComplianceStatus.COMPLIANT,
                missingAttestationTypes = emptyList(),
                failedAttestationTypes = emptyList(),
                details = "Flow has no required attestation types; artifact is compliant"
            )
            emitPolicyEvent(response)
            return response
        }

        // For each artifact, check if its trail has all required attestations passed
        for (artifact in artifacts) {
            val attestations = attestationRepository.findByTrailId(artifact.trailId)
            val passedTypes = attestations
                .filter { it.status == AttestationStatus.PASSED }
                .map { it.type }
                .toSet()
            val failedTypes = attestations
                .filter { it.status == AttestationStatus.FAILED }
                .map { it.type }
                .toList()
            val missing = required.filter { it !in passedTypes }

            if (missing.isEmpty()) {
                // Check approval requirement
                if (flow.requiresApproval) {
                    val approvals = approvalRepository.findByTrailId(artifact.trailId)
                    val hasApproved = approvals.any { it.status == ApprovalStatus.APPROVED }
                    if (!hasApproved) {
                        val response = AssertResponse(
                            sha256Digest = request.sha256Digest,
                            flowId = request.flowId,
                            status = ComplianceStatus.NON_COMPLIANT,
                            missingAttestationTypes = emptyList(),
                            failedAttestationTypes = emptyList(),
                            details = "Approval required but not yet granted"
                        )
                        emitPolicyEvent(response, trailId = artifact.trailId)
                        return response
                    }
                }
                log.info("Artifact ${request.sha256Digest} is COMPLIANT for flow ${request.flowId}")
                val response = AssertResponse(
                    sha256Digest = request.sha256Digest,
                    flowId = request.flowId,
                    status = ComplianceStatus.COMPLIANT,
                    missingAttestationTypes = emptyList(),
                    failedAttestationTypes = failedTypes,
                    details = "All required attestations passed"
                )
                emitPolicyEvent(response, trailId = artifact.trailId)
                return response
            }
        }

        // None of the trails are fully compliant - return the best result (least missing)
        val bestArtifact = artifacts.minByOrNull { artifact ->
            val attestations = attestationRepository.findByTrailId(artifact.trailId)
            val passedTypes = attestations.filter { it.status == AttestationStatus.PASSED }.map { it.type }.toSet()
            required.count { it !in passedTypes }
        }!!

        val attestations = attestationRepository.findByTrailId(bestArtifact.trailId)
        val passedTypes = attestations.filter { it.status == AttestationStatus.PASSED }.map { it.type }.toSet()
        val failedTypes = attestations.filter { it.status == AttestationStatus.FAILED }.map { it.type }
        val missing = required.filter { it !in passedTypes }

        log.info("Artifact ${request.sha256Digest} is NON_COMPLIANT for flow ${request.flowId}; missing: $missing")
        val response = AssertResponse(
            sha256Digest = request.sha256Digest,
            flowId = request.flowId,
            status = ComplianceStatus.NON_COMPLIANT,
            missingAttestationTypes = missing,
            failedAttestationTypes = failedTypes,
            details = "Missing required attestations: ${missing.joinToString(", ")}"
        )
        emitPolicyEvent(response, trailId = bestArtifact.trailId)
        return response
    }

    private fun assertWithTemplate(
        request: AssertRequest,
        flow: Flow,
        artifacts: List<Artifact>,
        templateYaml: String
    ): AssertResponse {
        val parsedTemplate = templateParser.parse(templateYaml)

        for (artifact in artifacts) {
            val trailObj = trailRepository.findById(artifact.trailId)
            val effectiveYaml = trailObj?.templateYaml ?: flow.templateYaml ?: templateYaml
            val effectiveParsed = if (effectiveYaml == templateYaml) parsedTemplate else templateParser.parse(effectiveYaml)

            val allRequired = computeRequired(flow, artifact.imageName, effectiveParsed)

            val attestations = attestationRepository.findByTrailId(artifact.trailId)
            val passedNames = attestations.filter { it.status == AttestationStatus.PASSED }.mapNotNull { it.name }.toSet()
            val failedNames = attestations.filter { it.status == AttestationStatus.FAILED }.mapNotNull { it.name }.toList()
            val missing = allRequired.filter { it !in passedNames }

            if (missing.isEmpty()) {
                // Check approval requirement
                if (flow.requiresApproval) {
                    val approvals = approvalRepository.findByTrailId(artifact.trailId)
                    val hasApproved = approvals.any { it.status == ApprovalStatus.APPROVED }
                    if (!hasApproved) {
                        val response = AssertResponse(
                            sha256Digest = request.sha256Digest,
                            flowId = request.flowId,
                            status = ComplianceStatus.NON_COMPLIANT,
                            missingAttestationTypes = emptyList(),
                            failedAttestationTypes = emptyList(),
                            details = "Approval required but not yet granted"
                        )
                        emitPolicyEvent(response, trailId = artifact.trailId)
                        return response
                    }
                }
                log.info("Artifact ${request.sha256Digest} is COMPLIANT (template) for flow ${request.flowId}")
                val response = AssertResponse(
                    sha256Digest = request.sha256Digest,
                    flowId = request.flowId,
                    status = ComplianceStatus.COMPLIANT,
                    missingAttestationTypes = emptyList(),
                    failedAttestationTypes = emptyList(),
                    missingAttestationNames = emptyList(),
                    failedAttestationNames = failedNames,
                    details = "All required attestations passed"
                )
                emitPolicyEvent(response, trailId = artifact.trailId)
                return response
            }
        }

        // None compliant - return best artifact result
        val bestArtifact = artifacts.minByOrNull { artifact ->
            val trailObj = trailRepository.findById(artifact.trailId)
            val effectiveYaml = trailObj?.templateYaml ?: flow.templateYaml ?: templateYaml
            val effectiveParsed = templateParser.parse(effectiveYaml)
            val allRequired = computeRequired(flow, artifact.imageName, effectiveParsed)
            val passedNames = attestationRepository.findByTrailId(artifact.trailId)
                .filter { it.status == AttestationStatus.PASSED }.mapNotNull { it.name }.toSet()
            allRequired.count { it !in passedNames }
        }!!

        val trailObj = trailRepository.findById(bestArtifact.trailId)
        val effectiveYaml = trailObj?.templateYaml ?: flow.templateYaml ?: templateYaml
        val effectiveParsed = templateParser.parse(effectiveYaml)
        val allRequired = computeRequired(flow, bestArtifact.imageName, effectiveParsed)
        val attestations = attestationRepository.findByTrailId(bestArtifact.trailId)
        val passedNames = attestations.filter { it.status == AttestationStatus.PASSED }.mapNotNull { it.name }.toSet()
        val failedNames = attestations.filter { it.status == AttestationStatus.FAILED }.mapNotNull { it.name }.toList()
        val missing = allRequired.filter { it !in passedNames }

        log.info("Artifact ${request.sha256Digest} is NON_COMPLIANT (template) for flow ${request.flowId}; missing: $missing")
        val response = AssertResponse(
            sha256Digest = request.sha256Digest,
            flowId = request.flowId,
            status = ComplianceStatus.NON_COMPLIANT,
            missingAttestationTypes = emptyList(),
            failedAttestationTypes = emptyList(),
            missingAttestationNames = missing,
            failedAttestationNames = failedNames,
            details = "Missing required attestations: ${missing.joinToString(", ")}"
        )
        emitPolicyEvent(response, trailId = bestArtifact.trailId)
        return response
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

    private fun emitPolicyEvent(response: AssertResponse, trailId: UUID? = null) {
        val eventType = if (response.status == ComplianceStatus.COMPLIANT)
            AuditEventType.GATE_ALLOWED else AuditEventType.GATE_BLOCKED
        auditService.record(
            eventType = eventType,
            actor = "system",
            payload = mapOf(
                "sha256Digest" to response.sha256Digest,
                "flowId" to response.flowId.toString(),
                "status" to response.status.name,
                "missingAttestationTypes" to response.missingAttestationTypes,
                "failedAttestationTypes" to response.failedAttestationTypes
            ),
            trailId = trailId,
            artifactSha256 = response.sha256Digest
        )
    }
}

