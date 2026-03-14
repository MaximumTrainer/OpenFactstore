package com.factstore.application

import com.factstore.core.domain.BundleStatus
import com.factstore.core.domain.OpaBundle
import com.factstore.core.domain.PolicyDecision
import com.factstore.core.port.inbound.IOpaService
import com.factstore.core.port.outbound.IOpaBundleRepository
import com.factstore.core.port.outbound.IPolicyDecisionRepository
import com.factstore.core.port.outbound.IPolicyEvaluator
import com.factstore.core.port.outbound.PolicyInput
import com.factstore.dto.BundleResponse
import com.factstore.dto.EvaluatePolicyRequest
import com.factstore.dto.PolicyDecisionResponse
import com.factstore.dto.UploadBundleRequest
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class OpaService(
    private val bundleRepository: IOpaBundleRepository,
    private val decisionRepository: IPolicyDecisionRepository,
    private val policyEvaluator: IPolicyEvaluator,
    private val objectMapper: ObjectMapper
) : IOpaService {

    private val log = LoggerFactory.getLogger(OpaService::class.java)

    override fun uploadBundle(request: UploadBundleRequest): BundleResponse {
        val bundle = OpaBundle(
            name = request.name,
            version = request.version,
            regoContent = request.regoContent,
            orgSlug = request.orgSlug
        )
        val saved = bundleRepository.save(bundle)
        log.info("Uploaded OPA bundle: ${saved.id} - ${saved.name} v${saved.version}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listBundles(): List<BundleResponse> =
        bundleRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getBundle(id: UUID): BundleResponse =
        (bundleRepository.findById(id) ?: throw NotFoundException("OPA bundle not found: $id")).toResponse()

    override fun activateBundle(id: UUID): BundleResponse {
        val target = bundleRepository.findById(id) ?: throw NotFoundException("OPA bundle not found: $id")
        // Deactivate any currently active bundle
        bundleRepository.findActive()?.let { active ->
            if (active.id != id) {
                active.status = BundleStatus.INACTIVE
                active.updatedAt = Instant.now()
                bundleRepository.save(active)
            }
        }
        target.status = BundleStatus.ACTIVE
        target.updatedAt = Instant.now()
        val saved = bundleRepository.save(target)
        log.info("Activated OPA bundle: ${saved.id} - ${saved.name} v${saved.version}")
        return saved.toResponse()
    }

    override fun evaluatePolicy(request: EvaluatePolicyRequest): PolicyDecisionResponse {
        val activeBundle = bundleRepository.findActive()
            ?: throw NotFoundException("No active OPA bundle found. Upload and activate a bundle first.")

        val input = PolicyInput(
            artifactName = request.artifactName,
            artifactVersion = request.artifactVersion,
            environment = request.environment,
            attestations = request.attestations,
            approvalStatus = request.approvalStatus
        )

        val result = policyEvaluator.evaluate(input, activeBundle.regoContent)

        val inputJson = objectMapper.writeValueAsString(mapOf(
            "artifactName" to request.artifactName,
            "artifactVersion" to request.artifactVersion,
            "environment" to request.environment,
            "attestations" to request.attestations,
            "approvalStatus" to request.approvalStatus
        ))

        val decision = PolicyDecision(
            bundleId = activeBundle.id,
            inputJson = inputJson,
            resultAllow = result.allow,
            denyReasons = if (result.denyReasons.isEmpty()) null else result.denyReasons.joinToString("||"),
            orgSlug = activeBundle.orgSlug
        )

        val saved = decisionRepository.save(decision)
        log.info("Policy decision ${saved.id}: allow=${saved.resultAllow} for artifact=${request.artifactName}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listDecisions(): List<PolicyDecisionResponse> =
        decisionRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getDecision(id: UUID): PolicyDecisionResponse =
        (decisionRepository.findById(id) ?: throw NotFoundException("Policy decision not found: $id")).toResponse()
}

fun OpaBundle.toResponse() = BundleResponse(
    id = id,
    name = name,
    version = version,
    regoContent = regoContent,
    status = status,
    orgSlug = orgSlug,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun PolicyDecision.toResponse() = PolicyDecisionResponse(
    id = id,
    bundleId = bundleId,
    inputJson = inputJson,
    resultAllow = resultAllow,
    denyReasons = denyReasons?.split("||")?.filter { it.isNotBlank() } ?: emptyList(),
    orgSlug = orgSlug,
    evaluatedAt = evaluatedAt
)
