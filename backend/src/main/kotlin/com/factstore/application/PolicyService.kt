package com.factstore.application

import com.factstore.core.domain.Policy
import com.factstore.core.port.inbound.IPolicyService
import com.factstore.core.port.outbound.IPolicyRepository
import com.factstore.dto.CreatePolicyRequest
import com.factstore.dto.PolicyResponse
import com.factstore.dto.UpdatePolicyRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class PolicyService(private val policyRepository: IPolicyRepository) : IPolicyService {

    private val log = LoggerFactory.getLogger(PolicyService::class.java)

    override fun createPolicy(request: CreatePolicyRequest): PolicyResponse {
        if (policyRepository.existsByName(request.name)) {
            throw ConflictException("Policy with name '${request.name}' already exists")
        }
        val policy = Policy(
            name = request.name,
            enforceProvenance = request.enforceProvenance,
            enforceTrailCompliance = request.enforceTrailCompliance,
            orgSlug = request.orgSlug
        ).also { it.requiredAttestationTypes = request.requiredAttestationTypes }
        val saved = policyRepository.save(policy)
        log.info("Created policy: ${saved.id} - ${saved.name}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listPolicies(): List<PolicyResponse> =
        policyRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getPolicy(id: UUID): PolicyResponse =
        (policyRepository.findById(id) ?: throw NotFoundException("Policy not found: $id")).toResponse()

    override fun updatePolicy(id: UUID, request: UpdatePolicyRequest): PolicyResponse {
        val policy = policyRepository.findById(id) ?: throw NotFoundException("Policy not found: $id")
        request.name?.let {
            if (it != policy.name && policyRepository.existsByName(it)) {
                throw ConflictException("Policy with name '$it' already exists")
            }
            policy.name = it
        }
        request.enforceProvenance?.let { policy.enforceProvenance = it }
        request.enforceTrailCompliance?.let { policy.enforceTrailCompliance = it }
        request.requiredAttestationTypes?.let { policy.requiredAttestationTypes = it }
        policy.updatedAt = Instant.now()
        return policyRepository.save(policy).toResponse()
    }

    override fun deletePolicy(id: UUID) {
        if (!policyRepository.existsById(id)) throw NotFoundException("Policy not found: $id")
        policyRepository.deleteById(id)
        log.info("Deleted policy: $id")
    }

    override fun updateWasmModule(id: UUID, wasmContent: String) {
        val policy = policyRepository.findById(id) ?: throw NotFoundException("Policy not found: $id")
        policy.wasmModuleContent = wasmContent
        policyRepository.save(policy)
    }
}

fun Policy.toResponse() = PolicyResponse(
    id = id,
    name = name,
    enforceProvenance = enforceProvenance,
    enforceTrailCompliance = enforceTrailCompliance,
    requiredAttestationTypes = requiredAttestationTypes,
    orgSlug = orgSlug,
    createdAt = createdAt,
    updatedAt = updatedAt
)
