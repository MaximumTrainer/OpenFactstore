package com.factstore.adapter.inbound.web

import com.factstore.application.signing.SigningKeyRegistryService
import com.factstore.application.signing.SigningKeyResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class RegisterSigningKeyRequest(val keyId: String, val publicKey: String)

/** The trusted registry of CI runners' source signing keys (#187). Administrators only. */
@RestController
@Tag(name = "Signing keys", description = "Ed25519 public keys CI runners sign facts with")
class SigningKeyController(private val registry: SigningKeyRegistryService) {

    @PostMapping("/api/v1/commands/signing-keys")
    @PreAuthorize("@authz.has('admin')")
    @Operation(
        summary = "Register a runner's signing key",
        description = "publicKey is an Ed25519 public key in PEM (openssl pkey -pubout). 201 when registered, " +
            "400 when it is not an Ed25519 key, 409 when the key id is taken. Rotate by registering a new key id."
    )
    fun register(@RequestBody request: RegisterSigningKeyRequest): ResponseEntity<SigningKeyResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(registry.register(request.keyId, request.publicKey))

    @PostMapping("/api/v1/commands/signing-keys/{keyId}/revoke")
    @PreAuthorize("@authz.has('admin')")
    @Operation(summary = "Revoke a signing key", description = "Facts signed with it are refused from now on.")
    fun revoke(@PathVariable keyId: String): ResponseEntity<SigningKeyResponse> = ResponseEntity.ok(registry.revoke(keyId))

    @GetMapping("/api/v1/queries/signing-keys")
    @PreAuthorize("@authz.has('admin')")
    @Operation(summary = "List the registered signing keys")
    fun list(): ResponseEntity<List<SigningKeyResponse>> = ResponseEntity.ok(registry.list())
}
