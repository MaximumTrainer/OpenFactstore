package com.factstore.application

import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.Flow
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.inbound.IFlowService
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.ComposeTemplateRequest
import com.factstore.dto.FlowImpactResponse
import com.factstore.dto.FlowResponse
import com.factstore.dto.FlowTemplateResponse
import com.factstore.dto.PageResponse
import com.factstore.dto.TemplateDriftResponse
import com.factstore.dto.UpdateFlowRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.yaml.snakeyaml.Yaml
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class FlowService(
    private val flowRepository: IFlowRepository,
    private val trailRepository: ITrailRepository,
    private val auditService: IAuditService,
    private val actorResolver: ActorResolver,
    private val hubService: HubService,
    private val templateParser: com.factstore.application.template.TemplateParser,
    private val tenantScope: TenantScope = TenantScope.UNSCOPED
) : IFlowService {

    /** A flow the caller may see; another organisation's flow is "not found" (#155 FR-5.2). */
    private fun findVisibleFlow(id: UUID): com.factstore.core.domain.Flow =
        tenantScope.require(flowRepository.findById(id), { it.orgSlug }) { "Flow not found: $id" }

    private val log = LoggerFactory.getLogger(FlowService::class.java)

    override fun createFlow(request: CreateFlowRequest): FlowResponse {
        if (flowRepository.findByName(request.name) != null) {
            throw ConflictException("Flow with name '${request.name}' already exists")
        }
        validateTags(request.tags)
        // A template is copied onto the flow at creation, never linked live, so a later template
        // change can never silently alter what an in-flight release is judged against (#162).
        val templateIds = (listOfNotNull(request.templateId) + request.templateIds).distinct()
        val applied = templateIds.takeIf { it.isNotEmpty() }?.let {
            hubService.compose(ComposeTemplateRequest(templateIds = it, orgSlug = request.orgSlug))
        }

        val flow = Flow(
            name = request.name,
            description = request.description,
            orgSlug = request.orgSlug
        ).also {
            it.requiredAttestationTypes = request.requiredAttestationTypes
            it.tags = request.tags.toMutableMap()
            it.templateYaml = request.templateYaml ?: applied?.templateYaml
            it.requiresApproval = request.requiresApproval
            it.requiredApproverRoles = request.requiredApproverRoles
            if (applied != null) {
                it.templateId = templateIds.joinToString("+")
                it.templateVersion = templateIds
                    .joinToString("+") { id -> hubService.getTemplate(id, request.orgSlug).version }
            }
        }
        val saved = flowRepository.save(flow)
        log.info("Created flow: ${saved.id} - ${saved.name}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listFlows(includeArchived: Boolean): List<FlowResponse> =
        (if (includeArchived) flowRepository.findAll() else flowRepository.findAllActive())
            .filter { tenantScope.visible(it.orgSlug) }
            .map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun listFlows(page: Int, size: Int): PageResponse<FlowResponse> {
        if (tenantScope.orgSlug() != null) {
            val visible = flowRepository.findAll().filter { tenantScope.visible(it.orgSlug) }
            val items = visible.drop(page * size).take(size).map { it.toResponse() }
            val totalPages = if (size <= 0) 0 else (visible.size + size - 1) / size
            return PageResponse(items = items, page = page, size = size, totalItems = visible.size.toLong(), totalPages = totalPages)
        }
        val pageResult = flowRepository.findAll(PageRequest.of(page, size))
        return PageResponse(
            items = pageResult.content.map { it.toResponse() },
            page = pageResult.number,
            size = pageResult.size,
            totalItems = pageResult.totalElements,
            totalPages = pageResult.totalPages
        )
    }

    @Transactional(readOnly = true)
    override fun getFlow(id: UUID): FlowResponse =
        (findVisibleFlow(id)).toResponse()

    override fun updateFlow(id: UUID, request: UpdateFlowRequest): FlowResponse {
        val flow = findVisibleFlow(id)
        val changes = mutableMapOf<String, Map<String, Any?>>()

        request.name?.let {
            if (it != flow.name && flowRepository.findByName(it) != null) {
                throw ConflictException("Flow with name '$it' already exists")
            }
            if (it != flow.name) changes["name"] = diff(flow.name, it)
            flow.name = it
        }
        request.description?.let {
            if (it != flow.description) changes["description"] = diff(flow.description, it)
            flow.description = it
        }
        request.requiredAttestationTypes?.let {
            if (it != flow.requiredAttestationTypes) {
                changes["requiredAttestationTypes"] = diff(flow.requiredAttestationTypes, it)
            }
            flow.requiredAttestationTypes = it
        }
        request.tags?.let {
            validateTags(it)
            if (it != flow.tags) changes["tags"] = diff(flow.tags.toMap(), it)
            flow.tags = it.toMutableMap()
        }
        request.templateYaml?.let {
            // A template document can be thousands of lines; record that it changed, not the whole thing.
            if (it != flow.templateYaml) {
                changes["templateYaml"] = mapOf(
                    "before" to summariseYaml(flow.templateYaml),
                    "after" to summariseYaml(it)
                )
            }
            flow.templateYaml = it
        }
        request.requiresApproval?.let {
            if (it != flow.requiresApproval) changes["requiresApproval"] = diff(flow.requiresApproval, it)
            flow.requiresApproval = it
        }
        request.requiredApproverRoles?.let {
            if (it != flow.requiredApproverRoles) {
                changes["requiredApproverRoles"] = diff(flow.requiredApproverRoles, it)
            }
            flow.requiredApproverRoles = it
        }

        if (changes.isEmpty()) {
            log.debug("Flow $id update changed nothing; no audit event recorded")
            return flow.toResponse()
        }

        flow.updatedAt = Instant.now()
        val saved = flowRepository.save(flow)
        recordFlowChange(AuditEventType.FLOW_UPDATED, saved, changes)
        return saved.toResponse()
    }

    /**
     * How much existing evidence a change to this flow would affect. Changing the required
     * attestations changes how every attached trail evaluates on its next assert, so the UI
     * states the blast radius before the user confirms (#160).
     */
    @Transactional(readOnly = true)
    override fun getFlowImpact(id: UUID): FlowImpactResponse {
        val flow = findVisibleFlow(id)
        val trails = trailRepository.findByFlowId(id)
        return FlowImpactResponse(
            flowId = flow.id,
            flowName = flow.name,
            trailCount = trails.size,
            pendingTrailCount = trails.count { it.status == TrailStatus.PENDING }
        )
    }

    /**
     * Whether the flow still matches the template it was created from (#162).
     *
     * Reports the gap rather than closing it: re-applying a template is a decision a team makes,
     * because it changes how existing trails will evaluate.
     */
    @Transactional(readOnly = true)
    override fun getTemplateDrift(id: UUID): TemplateDriftResponse {
        val flow = findVisibleFlow(id)
        val templateIds = flow.templateId?.split("+")?.filter { it.isNotBlank() } ?: emptyList()
        if (templateIds.isEmpty()) {
            return TemplateDriftResponse(
                flowId = flow.id,
                templateId = null,
                templateVersion = null,
                currentTemplateVersion = null,
                drifted = false
            )
        }

        val current = runCatching {
            hubService.compose(ComposeTemplateRequest(templateIds = templateIds, orgSlug = flow.orgSlug))
        }.getOrNull()
        val currentVersion = runCatching {
            templateIds.joinToString("+") { hubService.getTemplate(it, flow.orgSlug).version }
        }.getOrNull()

        val templateRequires = current?.requiredAttestations.orEmpty().toSet()
        val flowRequires = (templateParser.parse(flow.templateYaml ?: "")
            ?.let { parsed ->
                parsed.trailAttestations.map { it.name } + parsed.artifacts.flatMap { a -> a.attestations.map { it.name } }
            } ?: flow.requiredAttestationTypes).toSet()

        val missing = (templateRequires - flowRequires).sorted()
        val added = (flowRequires - templateRequires).sorted()
        return TemplateDriftResponse(
            flowId = flow.id,
            templateId = flow.templateId,
            templateVersion = flow.templateVersion,
            currentTemplateVersion = currentVersion,
            drifted = missing.isNotEmpty() || added.isNotEmpty() || currentVersion != flow.templateVersion,
            missingFromFlow = missing,
            addedToFlow = added
        )
    }

    private fun diff(before: Any?, after: Any?): Map<String, Any?> = mapOf("before" to before, "after" to after)

    private fun summariseYaml(yaml: String?): String? =
        yaml?.let { "${it.lines().size} lines, ${it.length} chars" }

    private fun recordFlowChange(
        eventType: AuditEventType,
        flow: Flow,
        changes: Map<String, Map<String, Any?>>
    ) {
        auditService.record(
            eventType = eventType,
            actor = actorResolver.current(),
            payload = mapOf(
                "flowId" to flow.id.toString(),
                "flowName" to flow.name,
                "changes" to changes
            )
        )
    }

    override fun deleteFlow(id: UUID, force: Boolean) {
        val flow = findVisibleFlow(id)
        val trailCount = trailRepository.findByFlowId(id).size
        if (trailCount > 0 && !force) {
            throw ConflictException(
                "Flow '${flow.name}' still has $trailCount trail(s). Archive it to retire it without " +
                    "losing history, or pass force=true to delete it and its trails deliberately."
            )
        }
        recordFlowChange(
            AuditEventType.FLOW_DELETED,
            flow,
            mapOf("trailCount" to mapOf("before" to trailCount, "after" to 0))
        )
        flowRepository.deleteById(id)
        log.info("Deleted flow: $id (force=$force, trails=$trailCount)")
    }

    override fun archiveFlow(id: UUID): FlowResponse {
        val flow = findVisibleFlow(id)
        val before = flow.archivedAt
        flow.archivedAt = Instant.now()
        flow.updatedAt = Instant.now()
        val saved = flowRepository.save(flow)
        recordFlowChange(
            AuditEventType.FLOW_ARCHIVED,
            saved,
            mapOf("archivedAt" to diff(before?.toString(), saved.archivedAt?.toString()))
        )
        return saved.toResponse()
    }

    override fun unarchiveFlow(id: UUID): FlowResponse {
        val flow = findVisibleFlow(id)
        val before = flow.archivedAt
        flow.archivedAt = null
        flow.updatedAt = Instant.now()
        val saved = flowRepository.save(flow)
        recordFlowChange(
            AuditEventType.FLOW_UNARCHIVED,
            saved,
            mapOf("archivedAt" to diff(before?.toString(), null))
        )
        return saved.toResponse()
    }

    override fun renameFlow(id: UUID, newName: String): FlowResponse {
        val flow = findVisibleFlow(id)
        if (newName != flow.name && flowRepository.findByName(newName) != null) {
            throw ConflictException("Flow with name '$newName' already exists")
        }
        val previousName = flow.name
        flow.addPreviousName(flow.name)
        flow.name = newName
        flow.updatedAt = Instant.now()
        val saved = flowRepository.save(flow)
        recordFlowChange(AuditEventType.FLOW_RENAMED, saved, mapOf("name" to diff(previousName, newName)))
        return saved.toResponse()
    }

    override fun getFlowEntity(id: UUID): Flow =
        findVisibleFlow(id)

    @Transactional(readOnly = true)
    override fun listFlowsByOrg(orgSlug: String): List<FlowResponse> =
        flowRepository.findAllByOrgSlug(orgSlug).map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getFlowTemplate(id: UUID): FlowTemplateResponse {
        val flow = findVisibleFlow(id)
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
        if (tags.size > 50) throw BadRequestException("Flow may have at most 50 tags")
        tags.forEach { (k, v) ->
            if (k.isBlank()) throw BadRequestException("Tag key must not be blank")
            if (k.length > 64) throw BadRequestException("Tag key '$k' exceeds 64 characters")
            if (v.length > 256) throw BadRequestException("Tag value for key '$k' exceeds 256 characters")
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
    templateId = templateId,
    templateVersion = templateVersion,
    createdAt = createdAt,
    updatedAt = updatedAt,
    requiresApproval = requiresApproval,
    requiredApproverRoles = requiredApproverRoles,
    archivedAt = archivedAt
)

