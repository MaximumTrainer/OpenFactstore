package com.factstore.adapter.outbound.persistence.read

import com.factstore.core.domain.Flow
import com.factstore.core.port.outbound.read.IFlowReadRepository
import com.factstore.adapter.outbound.persistence.FlowRepositoryJpa
import com.factstore.dto.query.FlowView
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class FlowReadRepositoryAdapter(private val jpa: FlowRepositoryJpa) : IFlowReadRepository {

    override fun findById(id: UUID): FlowView? = jpa.findById(id).orElse(null)?.toView()

    override fun findAll(): List<FlowView> = jpa.findAll().map { it.toView() }

    override fun findAllByOrgSlug(orgSlug: String): List<FlowView> =
        jpa.findAllByOrgSlug(orgSlug).map { it.toView() }
}

fun Flow.toView() = FlowView(
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
