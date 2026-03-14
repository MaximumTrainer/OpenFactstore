package com.factstore.adapter.inbound.web

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Validates Factstore SSO JWTs issued by [com.factstore.application.SsoConfigService]
 * and populates the [SecurityContextHolder] when a valid token is found.
 *
 * The filter only processes `Authorization: Bearer <token>` headers where the token
 * looks like a JWT (three dot-separated Base64URL segments starting with `eyJ`).
 * API keys (which start with `fsp_` or `fss_`) are left to [ApiKeyAuthFilter].
 */
@Component
class SsoJwtAuthFilter(
    private val objectMapper: ObjectMapper,
    @Value("\${sso.jwt.secret:changeme-in-production}")
    private val jwtSecret: String
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(SsoJwtAuthFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (SecurityContextHolder.getContext().authentication == null) {
            val token = extractBearerToken(request)
            if (token != null && looksLikeJwt(token)) {
                try {
                    val claims = validateAndExtractClaims(token)
                    if (claims != null) {
                        val sub = claims["sub"] as? String
                        val email = claims["email"] as? String
                        if (sub != null) {
                            val auth = UsernamePasswordAuthenticationToken(
                                email ?: sub,
                                null,
                                listOf(SimpleGrantedAuthority("ROLE_SSO_USER"))
                            )
                            SecurityContextHolder.getContext().authentication = auth
                        }
                    }
                } catch (ex: Exception) {
                    log.debug("SSO JWT validation failed: ${ex.message}")
                }
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun extractBearerToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        return if (header.startsWith("Bearer ")) header.removePrefix("Bearer ").trim() else null
    }

    /** Returns true when the token has three Base64URL parts and the JWT header marker. */
    private fun looksLikeJwt(token: String): Boolean =
        token.count { it == '.' } == 2 && token.startsWith("eyJ")

    /**
     * Validates the HS256 signature and token expiry, then extracts the payload claims.
     * Returns `null` if the token is invalid or expired.
     */
    private fun validateAndExtractClaims(token: String): Map<String, Any>? {
        val parts = token.split(".")
        if (parts.size != 3) return null

        val signingInput = "${parts[0]}.${parts[1]}"
        val expectedSig = hmacSha256(signingInput, jwtSecret)
        if (!constTimeEquals(expectedSig, parts[2])) {
            log.debug("SSO JWT signature mismatch")
            return null
        }

        val payloadJson = try {
            String(Base64.getUrlDecoder().decode(padBase64(parts[1])), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val claims = try {
            objectMapper.readValue(payloadJson, Map::class.java) as Map<String, Any>
        } catch (_: Exception) {
            return null
        }

        val exp = (claims["exp"] as? Number)?.toLong() ?: return null
        if (Instant.now().epochSecond > exp) {
            log.debug("SSO JWT has expired")
            return null
        }

        return claims
    }

    private fun hmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(data.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun padBase64(s: String): String {
        val mod = s.length % 4
        return if (mod == 0) s else s + "=".repeat(4 - mod)
    }

    /** Constant-time comparison to prevent timing side-channel attacks. */
    private fun constTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
