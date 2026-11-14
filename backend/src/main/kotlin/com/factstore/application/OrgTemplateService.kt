package com.factstore.application

import com.factstore.application.template.TemplateParser
import com.factstore.core.domain.OrgTemplate
import com.factstore.core.port.outbound.IOrgTemplateRepository
import com.factstore.dto.CreateOrgTemplateRequest
import com.factstore.dto.OrgTemplateResponse
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Templates an organisation publishes for itself (#162), so a platform team can define the
 * house standard rather than only consuming the built-in catalogue.
 */
@Service
@Transactional
class OrgTemplateService(private val repository: IOrgTemplateRepository) {

    private val log = LoggerFactory.getLogger(OrgTemplateService::class.java)
    private val templateParser = TemplateParser()

    @Transactional(readOnly = true)
    fun list(orgSlug: String? = null): List<OrgTemplateResponse> =
        repository.findAll()
            .filter { orgSlug == null || it.orgSlug == orgSlug }
            .sortedBy { it.templateId }
            .map { it.toResponse() }

    fun create(request: CreateOrgTemplateRequest): OrgTemplateResponse {
        validate(request)
        repository.findByOrgSlugAndTemplateId(request.orgSlug, request.templateId)?.let {
            throw ConflictException("Template '${request.templateId}' already exists for this organisation")
        }
        val saved = repository.save(
            OrgTemplate(
                templateId = request.templateId,
                orgSlug = request.orgSlug,
                name = request.name,
                description = request.description,
                framework = request.framework,
                version = request.version,
                category = request.category,
                serviceType = request.serviceType,
                templateYaml = request.templateYaml
            )
        )
        log.info("Published org template ${saved.templateId} for ${saved.orgSlug ?: "all organisations"}")
        return saved.toResponse()
    }

    fun update(id: UUID, request: CreateOrgTemplateRequest): OrgTemplateResponse {
        validate(request)
        val template = repository.findById(id) ?: throw NotFoundException("Org template not found: $id")
        template.templateId = request.templateId
        template.name = request.name
        template.description = request.description
        template.framework = request.framework
        template.version = request.version
        template.category = request.category
        template.serviceType = request.serviceType
        template.templateYaml = request.templateYaml
        template.updatedAt = Instant.now()
        return repository.save(template).toResponse()
    }

    fun delete(id: UUID) {
        if (repository.findById(id) == null) throw NotFoundException("Org template not found: $id")
        repository.deleteById(id)
        log.info("Withdrew org template $id")
    }

    /** A template nobody can parse is worse than no template, so reject it at publication. */
    private fun validate(request: CreateOrgTemplateRequest) {
        if (request.templateId.isBlank()) throw BadRequestException("templateId is required")
        if (request.name.isBlank()) throw BadRequestException("name is required")
        if (request.templateYaml.isBlank()) throw BadRequestException("templateYaml is required")
        templateParser.validate(request.templateYaml)
    }

    private fun OrgTemplate.toResponse() = OrgTemplateResponse(
        id = id,
        templateId = templateId,
        name = name,
        description = description,
        category = category,
        serviceType = serviceType,
        framework = framework,
        version = version,
        orgSlug = orgSlug,
        templateYaml = templateYaml,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
