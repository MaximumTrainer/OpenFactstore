package com.factstore.service

import com.factstore.domain.Flow
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.FlowResponse
import com.factstore.dto.UpdateFlowRequest
import com.factstore.exception.NotFoundException
import com.factstore.exception.ConflictException
import com.factstore.repository.FlowRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class FlowService(private val flowRepository: FlowRepository) {

    private val log = LoggerFactory.getLogger(FlowService::class.java)

    fun createFlow(request: CreateFlowRequest): FlowResponse {
        if (flowRepository.existsByName(request.name)) {
            throw ConflictException("Flow with name '${request.name}' already exists")
        }
        val flow = Flow(
            name = request.name,
            description = request.description
        ).also { it.requiredAttestationTypes = request.requiredAttestationTypes }
        val saved = flowRepository.save(flow)
        log.info("Created flow: ${saved.id} - ${saved.name}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun listFlows(): List<FlowResponse> = flowRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    fun getFlow(id: UUID): FlowResponse =
        flowRepository.findById(id).orElseThrow { NotFoundException("Flow not found: $id") }.toResponse()

    fun updateFlow(id: UUID, request: UpdateFlowRequest): FlowResponse {
        val flow = flowRepository.findById(id).orElseThrow { NotFoundException("Flow not found: $id") }
        request.name?.let {
            if (it != flow.name && flowRepository.existsByName(it)) {
                throw ConflictException("Flow with name '$it' already exists")
            }
            flow.name = it
        }
        request.description?.let { flow.description = it }
        request.requiredAttestationTypes?.let { flow.requiredAttestationTypes = it }
        flow.updatedAt = Instant.now()
        return flowRepository.save(flow).toResponse()
    }

    fun deleteFlow(id: UUID) {
        if (!flowRepository.existsById(id)) throw NotFoundException("Flow not found: $id")
        flowRepository.deleteById(id)
        log.info("Deleted flow: $id")
    }

    fun getFlowEntity(id: UUID): Flow =
        flowRepository.findById(id).orElseThrow { NotFoundException("Flow not found: $id") }
}

fun Flow.toResponse() = FlowResponse(
    id = id,
    name = name,
    description = description,
    requiredAttestationTypes = requiredAttestationTypes,
    createdAt = createdAt,
    updatedAt = updatedAt
)
