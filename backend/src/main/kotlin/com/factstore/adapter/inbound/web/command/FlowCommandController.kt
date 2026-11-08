package com.factstore.adapter.inbound.web.command

import org.springframework.security.access.prepost.PreAuthorize
import com.factstore.application.DryRunContext
import com.factstore.core.port.inbound.command.IFlowCommandHandler
import com.factstore.dto.command.CommandResult
import com.factstore.dto.command.CreateFlowCommand
import com.factstore.dto.command.DeleteFlowCommand
import com.factstore.dto.command.UpdateFlowCommand
import com.factstore.dto.command.UpdateFlowRequest
import com.factstore.dto.DryRunResponse
import com.factstore.dto.FlowResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v2/flows")
@Tag(name = "Flows – Commands", description = "Flow write operations (CQRS command path)")
class FlowCommandController(private val commandHandler: IFlowCommandHandler) {

    @PreAuthorize("@authz.has('flows:write')")
    @PostMapping
    @Operation(summary = "Create a new flow")
    fun createFlow(
        @RequestBody command: CreateFlowCommand,
        httpRequest: HttpServletRequest
    ): ResponseEntity<*> {
        if (DryRunContext.isDryRun(httpRequest)) {
            val now = Instant.now()
            val wouldBe = FlowResponse(
                id = UUID.randomUUID(),
                name = command.name,
                description = command.description,
                requiredAttestationTypes = command.requiredAttestationTypes,
                tags = command.tags,
                orgSlug = command.orgSlug,
                templateYaml = command.templateYaml,
                createdAt = now,
                updatedAt = now,
                requiresApproval = command.requiresApproval,
                requiredApproverRoles = command.requiredApproverRoles
            )
            return ResponseEntity.ok(DryRunResponse(wouldCreate = wouldBe))
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(commandHandler.createFlow(command))
    }

    @PreAuthorize("@authz.has('flows:write')")
    @PutMapping("/{id}")
    @Operation(summary = "Update a flow")
    fun updateFlow(@PathVariable id: UUID, @RequestBody request: UpdateFlowRequest): ResponseEntity<CommandResult> =
        ResponseEntity.ok(commandHandler.updateFlow(UpdateFlowCommand(
            id = id,
            name = request.name,
            description = request.description,
            requiredAttestationTypes = request.requiredAttestationTypes,
            tags = request.tags,
            templateYaml = request.templateYaml,
            requiresApproval = request.requiresApproval,
            requiredApproverRoles = request.requiredApproverRoles
        )))

    @PreAuthorize("@authz.has('flows:write')")
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a flow",
        description = "Refused with 409 when trails are still attached, unless force=true."
    )
    fun deleteFlow(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "false") force: Boolean
    ): ResponseEntity<Void> {
        commandHandler.deleteFlow(DeleteFlowCommand(id, force))
        return ResponseEntity.noContent().build()
    }
}
