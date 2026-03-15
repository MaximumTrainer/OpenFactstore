package com.factstore.adapter.inbound.web

import com.factstore.core.port.outbound.ISecretStore
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class SecretRequest(val value: String)
data class SecretResponse(val path: String, val value: String?)

@RestController
@RequestMapping("/api/v1/secrets")
@Tag(name = "Secrets", description = "Secret store management (requires vault.enabled=true for persistence)")
class SecretStoreController(private val secretStore: ISecretStore) {

    @GetMapping("/{path}")
    @Operation(summary = "Retrieve a secret by path")
    fun get(@PathVariable path: String): ResponseEntity<SecretResponse> {
        val value = secretStore.get(path)
        return ResponseEntity.ok(SecretResponse(path = path, value = value))
    }

    @PutMapping("/{path}")
    @Operation(summary = "Store a secret at path")
    fun put(@PathVariable path: String, @RequestBody request: SecretRequest): ResponseEntity<Void> {
        secretStore.put(path, request.value)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{path}")
    @Operation(summary = "Delete a secret at path")
    fun delete(@PathVariable path: String): ResponseEntity<Void> {
        secretStore.delete(path)
        return ResponseEntity.noContent().build()
    }
}
