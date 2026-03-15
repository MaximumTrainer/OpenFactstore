package com.factstore.adapter.inbound.web

import com.factstore.application.HubService
import com.factstore.core.domain.HubTemplate
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/hub")
@Tag(name = "Factstore Hub", description = "Shared policy template registry")
class HubController(private val hubService: HubService) {

    @GetMapping("/templates")
    @Operation(summary = "List all built-in hub templates")
    fun listTemplates(): ResponseEntity<List<HubTemplate>> =
        ResponseEntity.ok(hubService.listTemplates())

    @GetMapping("/templates/{id}")
    @Operation(summary = "Get a hub template by ID")
    fun getTemplate(@PathVariable id: String): ResponseEntity<HubTemplate> =
        ResponseEntity.ok(hubService.getTemplate(id))
}
