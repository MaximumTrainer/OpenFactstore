package com.factstore.adapter.inbound.web

import org.springframework.security.access.prepost.PreAuthorize
import com.factstore.application.HubService
import com.factstore.application.OrgTemplateService
import com.factstore.core.domain.HubTemplate
import com.factstore.core.domain.TemplateCategory
import com.factstore.dto.ComposeTemplateRequest
import com.factstore.dto.ComposedTemplateResponse
import com.factstore.dto.CreateOrgTemplateRequest
import com.factstore.dto.OrgTemplateResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/hub")
@Tag(name = "Factstore Hub", description = "Flow template catalogue: service types, frameworks, and org templates")
class HubController(
    private val hubService: HubService,
    private val orgTemplateService: OrgTemplateService
) {

    @PreAuthorize("@authz.authenticated()")
    @GetMapping("/templates")
    @Operation(
        summary = "List flow templates",
        description = "Built-in service-type and regulatory-framework templates, plus any the " +
            "organisation has published. Filter with category=SERVICE_TYPE or category=FRAMEWORK."
    )
    fun listTemplates(
        @RequestParam(required = false) category: TemplateCategory?,
        @RequestParam(required = false) orgSlug: String?
    ): ResponseEntity<List<HubTemplate>> =
        ResponseEntity.ok(hubService.listTemplates(category, orgSlug))

    @PreAuthorize("@authz.authenticated()")
    @GetMapping("/templates/{id}")
    @Operation(summary = "Get a flow template by ID")
    fun getTemplate(
        @PathVariable id: String,
        @RequestParam(required = false) orgSlug: String?
    ): ResponseEntity<HubTemplate> =
        ResponseEntity.ok(hubService.getTemplate(id, orgSlug))

    @PreAuthorize("@authz.authenticated()")
    @PostMapping("/templates/compose")
    @Operation(
        summary = "Combine several templates into one",
        description = "Takes the union of the required attestations - a service type plus a " +
            "regulatory framework, say - and reports any gate the templates disagree about."
    )
    fun compose(@RequestBody request: ComposeTemplateRequest): ResponseEntity<ComposedTemplateResponse> =
        ResponseEntity.ok(hubService.compose(request))

    // --- Organisation templates ------------------------------------------

    @PreAuthorize("@authz.authenticated()")
    @GetMapping("/templates/custom")
    @Operation(summary = "List the templates this organisation has published")
    fun listOrgTemplates(
        @RequestParam(required = false) orgSlug: String?
    ): ResponseEntity<List<OrgTemplateResponse>> =
        ResponseEntity.ok(orgTemplateService.list(orgSlug))

    @PreAuthorize("@authz.has('flows:write')")
    @PostMapping("/templates/custom")
    @Operation(
        summary = "Publish an organisation template",
        description = "An org template whose id matches a built-in shadows it, so a platform team " +
            "can override the house standard."
    )
    fun createOrgTemplate(
        @RequestBody request: CreateOrgTemplateRequest
    ): ResponseEntity<OrgTemplateResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(orgTemplateService.create(request))

    @PreAuthorize("@authz.has('flows:write')")
    @PutMapping("/templates/custom/{id}")
    @Operation(summary = "Update an organisation template")
    fun updateOrgTemplate(
        @PathVariable id: UUID,
        @RequestBody request: CreateOrgTemplateRequest
    ): ResponseEntity<OrgTemplateResponse> =
        ResponseEntity.ok(orgTemplateService.update(id, request))

    @PreAuthorize("@authz.has('flows:write')")
    @DeleteMapping("/templates/custom/{id}")
    @Operation(summary = "Withdraw an organisation template")
    fun deleteOrgTemplate(@PathVariable id: UUID): ResponseEntity<Void> {
        orgTemplateService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
