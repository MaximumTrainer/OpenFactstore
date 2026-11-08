package com.factstore.adapter.inbound.web

import com.factstore.core.domain.AuthFailureReason
import com.factstore.dto.AuthProblemResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

/**
 * Answers an unauthenticated request to a protected endpoint with `401` and
 * `WWW-Authenticate: Bearer` (#155 FR-2.4, acceptance criterion 1).
 *
 * Spring Security's default here was a bare `403`, which tells a client "you may not" when the
 * truth is "you have not said who you are". The body has the same shape as the API key filter's
 * refusals, with reason `MISSING`.
 */
@Component
class BearerAuthenticationEntryPoint(private val objectMapper: ObjectMapper) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, ApiKeyAuthFilter.WWW_AUTHENTICATE)
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(
                AuthProblemResponse(
                    error = "Unauthorized",
                    reason = AuthFailureReason.MISSING,
                    message = "No credential was presented"
                )
            )
        )
    }
}
