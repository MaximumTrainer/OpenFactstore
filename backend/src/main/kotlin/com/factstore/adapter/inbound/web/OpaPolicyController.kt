package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IOpaService
import com.factstore.dto.BundleResponse
import com.factstore.dto.EvaluatePolicyRequest
import com.factstore.dto.PolicyDecisionResponse
import com.factstore.dto.UploadBundleRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/opa")
@Tag(name = "OPA Policies", description = "OPA policy bundle management and evaluation")
class OpaPolicyController(private val opaService: IOpaService) {

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate artifact against active OPA policy bundle")
    fun evaluatePolicy(@RequestBody request: EvaluatePolicyRequest): ResponseEntity<PolicyDecisionResponse> =
        ResponseEntity.ok(opaService.evaluatePolicy(request))

    @PostMapping("/bundles")
    @Operation(summary = "Upload a Rego policy bundle")
    fun uploadBundle(@RequestBody request: UploadBundleRequest): ResponseEntity<BundleResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(opaService.uploadBundle(request))

    @GetMapping("/bundles")
    @Operation(summary = "List all policy bundles")
    fun listBundles(): ResponseEntity<List<BundleResponse>> =
        ResponseEntity.ok(opaService.listBundles())

    @GetMapping("/bundles/{id}")
    @Operation(summary = "Get policy bundle by ID")
    fun getBundle(@PathVariable id: UUID): ResponseEntity<BundleResponse> =
        ResponseEntity.ok(opaService.getBundle(id))

    @PutMapping("/bundles/{id}/activate")
    @Operation(summary = "Activate a policy bundle")
    fun activateBundle(@PathVariable id: UUID): ResponseEntity<BundleResponse> =
        ResponseEntity.ok(opaService.activateBundle(id))

    @GetMapping("/decisions")
    @Operation(summary = "List recent policy decisions (audit trail)")
    fun listDecisions(): ResponseEntity<List<PolicyDecisionResponse>> =
        ResponseEntity.ok(opaService.listDecisions())

    @GetMapping("/decisions/{id}")
    @Operation(summary = "Get policy decision details")
    fun getDecision(@PathVariable id: UUID): ResponseEntity<PolicyDecisionResponse> =
        ResponseEntity.ok(opaService.getDecision(id))
}
