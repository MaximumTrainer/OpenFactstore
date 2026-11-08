package com.factstore.adapter.inbound.web

import org.springframework.security.access.prepost.PreAuthorize
import com.factstore.core.port.inbound.IPolicyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class WasmModuleUploadRequest(val wasmContent: String)

@RestController
@RequestMapping("/api/v1/policies")
@Tag(name = "WASM Policy Plugins", description = "Upload and manage WASM policy plugins")
class WasmPolicyController(private val policyService: IPolicyService) {

    @PreAuthorize("@authz.has('policies:write')")
    @PutMapping("/{id}/wasm")
    @Operation(summary = "Upload a WASM policy module (JSON spec or base64-encoded .wasm)")
    fun uploadWasmModule(
        @PathVariable id: UUID,
        @RequestBody request: WasmModuleUploadRequest
    ): ResponseEntity<Void> {
        policyService.updateWasmModule(id, request.wasmContent)
        return ResponseEntity.noContent().build()
    }
}
