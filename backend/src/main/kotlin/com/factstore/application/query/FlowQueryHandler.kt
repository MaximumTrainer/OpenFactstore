package com.factstore.application.query

import com.factstore.application.TenantScope
import com.factstore.core.port.inbound.query.IFlowQueryHandler
import com.factstore.core.port.outbound.read.IFlowReadRepository
import com.factstore.dto.query.FlowTemplateView
import com.factstore.dto.query.FlowView
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.util.UUID

@Service
@Transactional(readOnly = true)
class FlowQueryHandler(
    private val flowReadRepository: IFlowReadRepository,
    private val tenantScope: TenantScope = TenantScope.UNSCOPED
) : IFlowQueryHandler {

    private val log = LoggerFactory.getLogger(FlowQueryHandler::class.java)

    // Another organisation's flow is "not found" (#155 FR-5.2).
    override fun getFlow(id: UUID): FlowView =
        tenantScope.require(flowReadRepository.findById(id), { it.orgSlug }) { "Flow not found: $id" }

    override fun listFlows(): List<FlowView> = flowReadRepository.findAll().filter { tenantScope.visible(it.orgSlug) }

    override fun listFlowsByOrg(orgSlug: String): List<FlowView> =
        flowReadRepository.findAllByOrgSlug(orgSlug).filter { tenantScope.visible(it.orgSlug) }

    override fun getFlowTemplate(id: UUID): FlowTemplateView {
        val flow = getFlow(id)
        val effectiveTemplate = flow.templateYaml?.let { yaml ->
            try {
                @Suppress("UNCHECKED_CAST")
                Yaml(SafeConstructor(LoaderOptions())).load<Map<String, Any>>(yaml)
            } catch (e: Exception) {
                log.warn("Failed to parse template YAML for flow {}: {}", id, e.message)
                null
            }
        }
        return FlowTemplateView(
            flowId = flow.id,
            templateYaml = flow.templateYaml,
            effectiveTemplate = effectiveTemplate
        )
    }
}
