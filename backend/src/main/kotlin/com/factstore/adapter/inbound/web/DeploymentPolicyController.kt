package com.factstore.adapter.inbound.web

import org.springframework.security.access.prepost.PreAuthorize
import com.factstore.core.port.inbound.IDeploymentPolicyService
import com.factstore.dto.CreateDeploymentPolicyRequest
import com.factstore.dto.DeploymentPolicyResponse
import com.factstore.dto.GateEvaluateRequest
import com.factstore.dto.GateEvaluateResponse
import com.factstore.dto.UpdateDeploymentPolicyRequest
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@Tag(name = "Deployment Policies", description = "Deployment gate policy management")
class DeploymentPolicyController(private val policyService: IDeploymentPolicyService) {

    @PreAuthorize("@authz.has('policies:write')")
    @PostMapping("/api/v1/deployment-policies")
    fun createPolicy(@RequestBody req: CreateDeploymentPolicyRequest): ResponseEntity<DeploymentPolicyResponse> =
        ResponseEntity.ok(policyService.createPolicy(req))

    @PreAuthorize("@authz.has('policies:read')")
    @GetMapping("/api/v1/deployment-policies")
    fun listPolicies(): ResponseEntity<List<DeploymentPolicyResponse>> =
        ResponseEntity.ok(policyService.listPolicies())

    @PreAuthorize("@authz.has('policies:read')")
    @GetMapping("/api/v1/deployment-policies/{id}")
    fun getPolicy(@PathVariable id: UUID): ResponseEntity<DeploymentPolicyResponse> =
        ResponseEntity.ok(policyService.getPolicy(id))

    @PreAuthorize("@authz.has('policies:write')")
    @PutMapping("/api/v1/deployment-policies/{id}")
    fun updatePolicy(
        @PathVariable id: UUID,
        @RequestBody req: UpdateDeploymentPolicyRequest
    ): ResponseEntity<DeploymentPolicyResponse> =
        ResponseEntity.ok(policyService.updatePolicy(id, req))

    @PreAuthorize("@authz.has('policies:write')")
    @DeleteMapping("/api/v1/deployment-policies/{id}")
    fun deletePolicy(@PathVariable id: UUID): ResponseEntity<Void> {
        policyService.deletePolicy(id)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("@authz.has('assert:execute')")
    @PostMapping("/api/v1/gate/evaluate")
    fun evaluateGate(@RequestBody req: GateEvaluateRequest): ResponseEntity<GateEvaluateResponse> =
        ResponseEntity.ok(policyService.evaluateGate(req))

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/api/v1/gate/results")
    fun listGateResults(): ResponseEntity<List<GateEvaluateResponse>> =
        ResponseEntity.ok(policyService.listGateResults())
}
