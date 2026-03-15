package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IPolicyService
import com.factstore.dto.CreatePolicyRequest
import com.factstore.dto.PolicyResponse
import com.factstore.dto.UpdatePolicyRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/policies")
@Tag(name = "Policies", description = "Policy management")
class PolicyController(private val policyService: IPolicyService) {

    @PostMapping
    @Operation(summary = "Create a new policy")
    fun createPolicy(@RequestBody request: CreatePolicyRequest): ResponseEntity<PolicyResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(policyService.createPolicy(request))

    @GetMapping
    @Operation(summary = "List all policies")
    fun listPolicies(): ResponseEntity<List<PolicyResponse>> =
        ResponseEntity.ok(policyService.listPolicies())

    @GetMapping("/{id}")
    @Operation(summary = "Get policy by ID")
    fun getPolicy(@PathVariable id: UUID): ResponseEntity<PolicyResponse> =
        ResponseEntity.ok(policyService.getPolicy(id))

    @PutMapping("/{id}")
    @Operation(summary = "Update a policy")
    fun updatePolicy(@PathVariable id: UUID, @RequestBody request: UpdatePolicyRequest): ResponseEntity<PolicyResponse> =
        ResponseEntity.ok(policyService.updatePolicy(id, request))

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a policy")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEMBER', 'API_USER')")
    fun deletePolicy(@PathVariable id: UUID): ResponseEntity<Void> {
        policyService.deletePolicy(id)
        return ResponseEntity.noContent().build()
    }
}
