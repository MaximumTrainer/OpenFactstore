package com.factstore.application

import com.factstore.core.domain.Flow
import com.factstore.core.port.inbound.IFlowService
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.FlowResponse
import com.factstore.dto.FlowTemplateResponse
import com.factstore.dto.UpdateFlowRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.yaml.snakeyaml.Yaml
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class FlowService(private val flowRepository: IFlowRepository) : IFlowService {

    private val log = LoggerFactory.getLogger(FlowService::class.java)

    override fun createFlow(request: CreateFlowRequest): FlowResponse {
        if (flowRepository.existsByName(request.name)) {
            throw ConflictException("Flow with name '${request.name}' already exists")
        }
        validateTags(request.tags)
        val flow = Flow(
            name = request.name,
            description = request.description,
            orgSlug = request.orgSlug
        ).also {
            it.requiredAttestationTypes = request.requiredAttestationTypes
            it.tags = request.tags.toMutableMap()
            it.templateYaml = request.templateYaml
            it.requiresApproval = request.requiresApproval
            it.requiredApproverRoles = request.requiredApproverRoles
        }
        val saved = flowRepository.save(flow)
        log.info("Created flow: ${saved.id} - ${saved.name}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listFlows(): List<FlowResponse> = flowRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getFlow(id: UUID): FlowResponse =
        (flowRepository.findById(id) ?: throw NotFoundException("Flow not found: $id")).toResponse()

    override fun updateFlow(id: UUID, request: UpdateFlowRequest): FlowResponse {
        val flow = flowRepository.findById(id) ?: throw NotFoundException("Flow not found: $id")
        request.name?.let {
            if (it != flow.name && flowRepository.existsByName(it)) {
                throw ConflictException("Flow with name '$it' already exists")
            }
            flow.name = it
        }
        request.description?.let { flow.description = it }
        request.requiredAttestationTypes?.let { flow.requiredAttestationTypes = it }
        request.tags?.let {
            validateTags(it)
            flow.tags = it.toMutableMap()
        }
        request.templateYaml?.let { flow.templateYaml = it }
        request.requiresApproval?.let { flow.requiresApproval = it }
        request.requiredApproverRoles?.let { flow.requiredApproverRoles = it }
        flow.updatedAt = Instant.now()
        return flowRepository.save(flow).toResponse()
    }

    override fun deleteFlow(id: UUID) {
        if (!flowRepository.existsById(id)) throw NotFoundException("Flow not found: $id")
        flowRepository.deleteById(id)
        log.info("Deleted flow: $id")
    }

    override fun getFlowEntity(id: UUID): Flow =
        flowRepository.findById(id) ?: throw NotFoundException("Flow not found: $id")

    @Transactional(readOnly = true)
    override fun listFlowsByOrg(orgSlug: String): List<FlowResponse> =
        flowRepository.findAllByOrgSlug(orgSlug).map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getFlowTemplate(id: UUID): FlowTemplateResponse {
        val flow = flowRepository.findById(id) ?: throw NotFoundException("Flow not found: $id")
        val effectiveTemplate = flow.templateYaml?.let { yaml ->
            @Suppress("UNCHECKED_CAST")
            Yaml().load<Map<String, Any>>(yaml)
        }
        return FlowTemplateResponse(
            flowId = flow.id,
            templateYaml = flow.templateYaml,
            effectiveTemplate = effectiveTemplate
        )
    }

    private fun validateTags(tags: Map<String, String>) {
        require(tags.size <= 50) { "Flow may have at most 50 tags" }
        tags.forEach { (k, v) ->
            require(k.isNotBlank()) { "Tag key must not be blank" }
            require(k.length <= 64) { "Tag key '$k' exceeds 64 characters" }
            require(v.length <= 256) { "Tag value for key '$k' exceeds 256 characters" }
        }
    }
}

fun Flow.toResponse() = FlowResponse(
    id = id,
    name = name,
    description = description,
    requiredAttestationTypes = requiredAttestationTypes,
    tags = tags.toMap(),
    orgSlug = orgSlug,
    templateYaml = templateYaml,
    createdAt = createdAt,
    updatedAt = updatedAt,
    requiresApproval = requiresApproval,
    requiredApproverRoles = requiredApproverRoles
)

