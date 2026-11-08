package com.factstore.adapter.inbound.web

import com.factstore.application.auth.DevSignInService
import com.factstore.core.domain.MemberRole
import com.factstore.exception.BadRequestException
import com.factstore.exception.ForbiddenException
import com.factstore.exception.NotFoundException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class DevSignInRequest(
    val email: String,
    val name: String? = null,
    val orgSlug: String? = null,
    val role: MemberRole? = null
)

data class DevSignInResponse(
    val email: String,
    val orgSlug: String,
    val role: MemberRole,
    val expiresAt: Instant
)

/**
 * Sign-in for local development, with no identity provider (#156 FR-8.4).
 *
 * Off unless `security.dev-login.enabled=true`; when off, both routes answer 404 as if they did not
 * exist. When on, they only answer requests from the machine the service runs on, unless
 * `security.dev-login.loopback-only=false` (for example behind a local Docker network).
 * [com.factstore.config.DevSignInGuard] refuses to start the application with it on under `prod`.
 */
@RestController
@RequestMapping("/api/v1/auth/dev-login")
@Tag(name = "Authentication", description = "Sign-in identity, sessions and sign-out")
class DevSignInController(
    private val devSignInService: DevSignInService,
    @Value("\${security.dev-login.enabled:false}") private val enabled: Boolean,
    @Value("\${security.dev-login.loopback-only:true}") private val loopbackOnly: Boolean
) {

    @PublicEndpoint("development sign-in; off unless enabled, loopback only, refused under prod")
    @GetMapping
    @Operation(summary = "Whether dev sign-in is available (development only; 404 when off)")
    fun availability(): ResponseEntity<Map<String, Boolean>> {
        requireEnabled()
        return ResponseEntity.ok(mapOf("enabled" to true))
    }

    @PublicEndpoint("development sign-in; off unless enabled, loopback only, refused under prod")
    @PostMapping
    @Operation(summary = "Sign in as any email and role without an identity provider (development only)")
    fun signIn(
        @RequestBody request: DevSignInRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<DevSignInResponse> {
        requireEnabled()
        if (loopbackOnly && httpRequest.remoteAddr !in LOOPBACK) {
            throw ForbiddenException("Dev sign-in only accepts requests from this machine")
        }
        val email = request.email.trim()
        if (!email.contains('@')) throw BadRequestException("email must be an email address")
        val orgSlug = request.orgSlug?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_ORG
        val role = request.role ?: MemberRole.ADMIN

        val issued = devSignInService.signIn(email, request.name, orgSlug, role, httpRequest.remoteAddr)
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, AuthController.sessionCookie(issued.token, httpRequest))
        return ResponseEntity.ok(DevSignInResponse(email, orgSlug, role, issued.expiresAt))
    }

    private fun requireEnabled() {
        if (!enabled) throw NotFoundException("Not found")
    }

    private companion object {
        const val DEFAULT_ORG = "dev"
        val LOOPBACK = setOf("127.0.0.1", "::1", "0:0:0:0:0:0:0:1")
    }
}
