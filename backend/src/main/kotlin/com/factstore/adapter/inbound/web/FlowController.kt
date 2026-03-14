package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IFlowService
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.FlowResponse
import com.factstore.dto.FlowTemplateResponse
import com.factstore.dto.UpdateFlowRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/flows")
@Tag(name = "Flows", description = "Flow management")
class FlowController(private val flowService: IFlowService) {

    @PostMapping
    @Operation(summary = "Create a new flow")
    fun createFlow(@RequestBody request: CreateFlowRequest): ResponseEntity<FlowResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(flowService.createFlow(request))

    @GetMapping
    @Operation(summary = "List all flows")
    fun listFlows(): ResponseEntity<List<FlowResponse>> =
        ResponseEntity.ok(flowService.listFlows())

    @GetMapping("/{id}")
    @Operation(summary = "Get flow by ID")
    fun getFlow(@PathVariable id: UUID): ResponseEntity<FlowResponse> =
        ResponseEntity.ok(flowService.getFlow(id))

    @PutMapping("/{id}")
    @Operation(summary = "Update a flow")
    fun updateFlow(@PathVariable id: UUID, @RequestBody request: UpdateFlowRequest): ResponseEntity<FlowResponse> =
        ResponseEntity.ok(flowService.updateFlow(id, request))

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a flow")
    fun deleteFlow(@PathVariable id: UUID): ResponseEntity<Void> {
        flowService.deleteFlow(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{id}/template")
    @Operation(summary = "Get flow template")
    fun getFlowTemplate(@PathVariable id: UUID): ResponseEntity<FlowTemplateResponse> =
        ResponseEntity.ok(flowService.getFlowTemplate(id))
}
