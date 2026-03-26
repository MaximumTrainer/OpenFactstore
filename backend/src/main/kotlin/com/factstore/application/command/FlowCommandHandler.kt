package com.factstore.application.command

import com.factstore.core.domain.Flow
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.port.inbound.command.IFlowCommandHandler
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.dto.command.CommandResult
import com.factstore.dto.command.CreateFlowCommand
import com.factstore.dto.command.DeleteFlowCommand
import com.factstore.dto.command.UpdateFlowCommand
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Handles flow-related commands on the write side of the CQRS split.
 *
 * **Dual-write strategy:** State is persisted to the JPA entity store *and*
 * an event is appended to the event log within the same transaction.  The
 * JPA entities remain the authoritative read model for now; the event log
 * provides an immutable audit trail and enables future replay-based
 * projections.  A later iteration may drop the direct JPA write and
 * reconstruct state entirely from the event log.
 */
@Service
@Transactional
class FlowCommandHandler(
    private val flowRepository: IFlowRepository,
    private val eventAppender: EventAppender
) : IFlowCommandHandler {

    private val log = LoggerFactory.getLogger(FlowCommandHandler::class.java)

    override fun createFlow(command: CreateFlowCommand): CommandResult {
        if (flowRepository.existsByName(command.name)) {
            throw ConflictException("Flow with name '${command.name}' already exists")
        }
        validateTags(command.tags)
        val flow = Flow(
            name = command.name,
            description = command.description,
            orgSlug = command.orgSlug
        ).also {
            it.requiredAttestationTypes = command.requiredAttestationTypes
            it.tags = command.tags.toMutableMap()
            it.templateYaml = command.templateYaml
            it.requiresApproval = command.requiresApproval
            it.requiredApproverRoles = command.requiredApproverRoles
        }
        val saved = flowRepository.save(flow)
        eventAppender.append(DomainEvent.FlowCreated(
            aggregateId = saved.id,
            name = saved.name,
            description = saved.description,
            orgSlug = saved.orgSlug,
            requiredAttestationTypes = saved.requiredAttestationTypes,
            tags = saved.tags.toMap(),
            templateYaml = saved.templateYaml,
            requiresApproval = saved.requiresApproval,
            requiredApproverRoles = saved.requiredApproverRoles
        ))
        log.info("Created flow: ${saved.id} - ${saved.name}")
        return CommandResult(id = saved.id, status = "created")
    }

    override fun updateFlow(command: UpdateFlowCommand): CommandResult {
        val flow = flowRepository.findById(command.id) ?: throw NotFoundException("Flow not found: ${command.id}")
        command.name?.let {
            if (it != flow.name && flowRepository.existsByName(it)) {
                throw ConflictException("Flow with name '$it' already exists")
            }
            flow.name = it
        }
        command.description?.let { flow.description = it }
        command.requiredAttestationTypes?.let { flow.requiredAttestationTypes = it }
        command.tags?.let {
            validateTags(it)
            flow.tags = it.toMutableMap()
        }
        command.templateYaml?.let { flow.templateYaml = it }
        command.requiresApproval?.let { flow.requiresApproval = it }
        command.requiredApproverRoles?.let { flow.requiredApproverRoles = it }
        flow.updatedAt = Instant.now()
        val saved = flowRepository.save(flow)
        eventAppender.append(DomainEvent.FlowUpdated(
            aggregateId = saved.id,
            name = command.name,
            description = command.description,
            requiredAttestationTypes = command.requiredAttestationTypes,
            tags = command.tags,
            templateYaml = command.templateYaml,
            requiresApproval = command.requiresApproval,
            requiredApproverRoles = command.requiredApproverRoles
        ))
        return CommandResult(id = saved.id, status = "updated")
    }

    override fun deleteFlow(command: DeleteFlowCommand) {
        if (!flowRepository.existsById(command.id)) throw NotFoundException("Flow not found: ${command.id}")
        flowRepository.deleteById(command.id)
        eventAppender.append(DomainEvent.FlowDeleted(aggregateId = command.id))
        log.info("Deleted flow: ${command.id}")
    }

    private fun validateTags(tags: Map<String, String>) {
        if (tags.size > 50) throw BadRequestException("Flow may have at most 50 tags")
        tags.forEach { (k, v) ->
            if (k.isBlank()) throw BadRequestException("Tag key must not be blank")
            if (k.length > 64) throw BadRequestException("Tag key '$k' exceeds 64 characters")
            if (v.length > 256) throw BadRequestException("Tag value for key '$k' exceeds 256 characters")
        }
    }
}
