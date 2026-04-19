package com.factstore.adapter.inbound.web.query

import com.factstore.core.port.inbound.query.IFlowQueryHandler
import com.factstore.dto.query.FlowTemplateView
import com.factstore.dto.query.FlowView
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v2/flows")
@Tag(name = "Flows – Queries", description = "Flow read operations (CQRS query path)")
class FlowQueryController(private val queryHandler: IFlowQueryHandler) {

    @GetMapping
    @Operation(summary = "List all flows")
    fun listFlows(): ResponseEntity<List<FlowView>> =
        ResponseEntity.ok(queryHandler.listFlows())

    @GetMapping("/{id}")
    @Operation(summary = "Get flow by ID")
    fun getFlow(@PathVariable id: UUID): ResponseEntity<FlowView> =
        ResponseEntity.ok(queryHandler.getFlow(id))

    @GetMapping("/{id}/template")
    @Operation(summary = "Get flow template")
    fun getFlowTemplate(@PathVariable id: UUID): ResponseEntity<FlowTemplateView> =
        ResponseEntity.ok(queryHandler.getFlowTemplate(id))
}
