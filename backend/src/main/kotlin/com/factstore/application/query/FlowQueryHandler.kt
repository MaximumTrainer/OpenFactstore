package com.factstore.application.query

import com.factstore.core.port.inbound.query.IFlowQueryHandler
import com.factstore.core.port.outbound.read.IFlowReadRepository
import com.factstore.dto.query.FlowTemplateView
import com.factstore.dto.query.FlowView
import com.factstore.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.yaml.snakeyaml.Yaml
import java.util.UUID

@Service
@Transactional(readOnly = true)
class FlowQueryHandler(private val flowReadRepository: IFlowReadRepository) : IFlowQueryHandler {

    override fun getFlow(id: UUID): FlowView =
        flowReadRepository.findById(id) ?: throw NotFoundException("Flow not found: $id")

    override fun listFlows(): List<FlowView> = flowReadRepository.findAll()

    override fun listFlowsByOrg(orgSlug: String): List<FlowView> =
        flowReadRepository.findAllByOrgSlug(orgSlug)

    override fun getFlowTemplate(id: UUID): FlowTemplateView {
        val flow = flowReadRepository.findById(id) ?: throw NotFoundException("Flow not found: $id")
        val effectiveTemplate = flow.templateYaml?.let { yaml ->
            @Suppress("UNCHECKED_CAST")
            Yaml().load<Map<String, Any>>(yaml)
        }
        return FlowTemplateView(
            flowId = flow.id,
            templateYaml = flow.templateYaml,
            effectiveTemplate = effectiveTemplate
        )
    }
}
