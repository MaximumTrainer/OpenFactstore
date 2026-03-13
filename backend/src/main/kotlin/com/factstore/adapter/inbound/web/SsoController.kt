package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.ISsoConfigService
import com.factstore.dto.CreateSsoConfigRequest
import com.factstore.dto.SsoCallbackResponse
import com.factstore.dto.SsoConfigResponse
import com.factstore.dto.SsoLoginUrlResponse
import com.factstore.dto.SsoTestConnectionResponse
import com.factstore.dto.UpdateSsoConfigRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/organisations/{slug}/sso")
@Tag(name = "SSO", description = "Single Sign-On (OIDC) configuration and login flow")
class SsoController(private val ssoService: ISsoConfigService) {

    @PostMapping
    @Operation(summary = "Create SSO configuration for an organisation")
    fun createSsoConfig(
        @PathVariable slug: String,
        @RequestBody request: CreateSsoConfigRequest
    ): ResponseEntity<SsoConfigResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(ssoService.createSsoConfig(slug, request))

    @GetMapping
    @Operation(summary = "Get SSO configuration for an organisation")
    fun getSsoConfig(@PathVariable slug: String): ResponseEntity<SsoConfigResponse> =
        ResponseEntity.ok(ssoService.getSsoConfig(slug))

    @PutMapping
    @Operation(summary = "Update SSO configuration for an organisation")
    fun updateSsoConfig(
        @PathVariable slug: String,
        @RequestBody request: UpdateSsoConfigRequest
    ): ResponseEntity<SsoConfigResponse> =
        ResponseEntity.ok(ssoService.updateSsoConfig(slug, request))

    @DeleteMapping
    @Operation(summary = "Delete SSO configuration for an organisation")
    fun deleteSsoConfig(@PathVariable slug: String): ResponseEntity<Void> {
        ssoService.deleteSsoConfig(slug)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/test")
    @Operation(summary = "Test the OIDC connection for an organisation's SSO configuration")
    fun testSsoConnection(@PathVariable slug: String): ResponseEntity<SsoTestConnectionResponse> =
        ResponseEntity.ok(ssoService.testSsoConnection(slug))

    @GetMapping("/login")
    @Operation(
        summary = "Initiate SSO login — returns the IdP authorization URL",
        description = "The client should redirect the browser to the returned `loginUrl`."
    )
    fun initiateSsoLogin(
        @PathVariable slug: String,
        @RequestParam redirectUri: String
    ): ResponseEntity<SsoLoginUrlResponse> =
        ResponseEntity.ok(ssoService.initiateSsoLogin(slug, redirectUri))

    @GetMapping("/callback")
    @Operation(
        summary = "Handle the OIDC callback from the identity provider",
        description = "Exchanges the authorization code, provisions the user (JIT), and returns a Factstore JWT."
    )
    fun handleSsoCallback(
        @PathVariable slug: String,
        @RequestParam code: String,
        @RequestParam state: String,
        request: HttpServletRequest
    ): ResponseEntity<SsoCallbackResponse> {
        // Reconstruct the redirect URI from the incoming request so it matches what was sent to the IdP.
        val redirectUri = request.requestURL.toString()
        return ResponseEntity.ok(ssoService.handleSsoCallback(slug, code, state, redirectUri))
    }
}
