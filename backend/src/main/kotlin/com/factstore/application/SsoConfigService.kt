package com.factstore.application

import com.factstore.adapter.outbound.UrlValidator
import com.factstore.core.domain.MemberRole
import com.factstore.core.domain.OrganisationMembership
import com.factstore.core.domain.SsoConfig
import com.factstore.core.domain.User
import com.factstore.core.port.inbound.ISsoConfigService
import com.factstore.core.port.outbound.IOrganisationMembershipRepository
import com.factstore.core.port.outbound.ISsoConfigRepository
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.CreateSsoConfigRequest
import com.factstore.dto.SsoCallbackResponse
import com.factstore.dto.SsoConfigResponse
import com.factstore.dto.SsoLoginUrlResponse
import com.factstore.dto.SsoTestConnectionResponse
import com.factstore.dto.UpdateSsoConfigRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
@Transactional
class SsoConfigService(
    private val ssoConfigRepository: ISsoConfigRepository,
    private val userRepository: IUserRepository,
    private val membershipRepository: IOrganisationMembershipRepository,
    @Qualifier("ssoRestTemplate") private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${sso.jwt.secret:changeme-in-production}")
    private val jwtSecret: String,
    @Value("\${sso.jwt.expiry-hours:8}")
    private val jwtExpiryHours: Long
) : ISsoConfigService {

    companion object {
        /**
         * Role privilege ordering for [resolveRole]: the first role in this list that matches
         * a user's IdP groups is selected, ensuring the highest-privilege role always wins.
         * Update this list if the [MemberRole] hierarchy changes.
         */
        internal val ROLE_PRIVILEGE_ORDER = listOf(
            MemberRole.ADMIN, MemberRole.MEMBER, MemberRole.VIEWER, MemberRole.SERVICE_ACCOUNT
        )
    }

    private val log = LoggerFactory.getLogger(SsoConfigService::class.java)

    /**
     * Short-lived OIDC state tokens keyed by state value, expiring after 10 minutes.
     *
     * NOTE: This in-memory map is **not** shared across instances in a multi-replica deployment.
     * If the OIDC callback lands on a different instance than the one that initiated the login,
     * the state will be missing and the login will fail.  For multi-instance deployments,
     * replace this map with a shared store (e.g., Redis or a DB-backed cache with TTL).
     */
    internal val pendingStates = ConcurrentHashMap<String, PendingOidcState>()

    data class PendingOidcState(
        val orgSlug: String,
        /** The redirect URI sent to the IdP; reused verbatim in the token exchange. */
        val redirectUri: String,
        val expiresAt: Instant
    )

    @PostConstruct
    fun warnIfDefaultJwtSecret() {
        if (jwtSecret == "changeme-in-production") {
            log.warn(
                "SSO JWT secret is using the default insecure value. " +
                    "Set the SSO_JWT_SECRET environment variable to a strong random secret before deploying to production."
            )
        }
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    override fun createSsoConfig(orgSlug: String, request: CreateSsoConfigRequest): SsoConfigResponse {
        if (ssoConfigRepository.existsByOrgSlug(orgSlug)) {
            throw ConflictException("SSO configuration already exists for organisation '$orgSlug'")
        }
        val normalizedUrl = request.issuerUrl.trimEnd('/')
        validateIssuerUrl(normalizedUrl)
        val config = SsoConfig(
            orgSlug = orgSlug,
            provider = request.provider,
            issuerUrl = normalizedUrl,
            clientId = request.clientId,
            clientSecret = request.clientSecret,
            attributeMappings = request.attributeMappings,
            groupRoleMappings = request.groupRoleMappings,
            isMandatory = request.isMandatory
        )
        val saved = ssoConfigRepository.save(config)
        log.info("Created SSO config for org=$orgSlug provider=${request.provider}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun getSsoConfig(orgSlug: String): SsoConfigResponse =
        (ssoConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("SSO configuration not found for organisation '$orgSlug'"))
            .toResponse()

    override fun updateSsoConfig(orgSlug: String, request: UpdateSsoConfigRequest): SsoConfigResponse {
        val config = ssoConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("SSO configuration not found for organisation '$orgSlug'")
        request.provider?.let { config.provider = it }
        request.issuerUrl?.let {
            val normalizedUrl = it.trimEnd('/')
            validateIssuerUrl(normalizedUrl)
            config.issuerUrl = normalizedUrl
        }
        request.clientId?.let { config.clientId = it }
        request.clientSecret?.let { config.clientSecret = it }
        request.attributeMappings?.let { config.attributeMappings = it }
        request.groupRoleMappings?.let { config.groupRoleMappings = it }
        request.isMandatory?.let { config.isMandatory = it }
        config.updatedAt = Instant.now()
        val saved = ssoConfigRepository.save(config)
        log.info("Updated SSO config for org=$orgSlug")
        return saved.toResponse()
    }

    override fun deleteSsoConfig(orgSlug: String) {
        val config = ssoConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("SSO configuration not found for organisation '$orgSlug'")
        ssoConfigRepository.delete(config)
        log.info("Deleted SSO config for org=$orgSlug")
    }

    @Transactional(readOnly = true)
    override fun isSsoMandatory(orgSlug: String): Boolean =
        ssoConfigRepository.findByOrgSlug(orgSlug)?.isMandatory == true

    // -------------------------------------------------------------------------
    // Test Connection
    // -------------------------------------------------------------------------

    override fun testSsoConnection(orgSlug: String): SsoTestConnectionResponse {
        val config = ssoConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("SSO configuration not found for organisation '$orgSlug'")
        return try {
            val discovery = fetchOidcDiscovery(config.issuerUrl)
            SsoTestConnectionResponse(
                success = true,
                message = "Successfully reached OIDC discovery endpoint",
                authorizationEndpoint = discovery["authorization_endpoint"] as? String,
                tokenEndpoint = discovery["token_endpoint"] as? String
            )
        } catch (ex: Exception) {
            log.warn("SSO test connection failed for org=$orgSlug: ${ex.message}")
            SsoTestConnectionResponse(
                success = false,
                message = "Unable to reach the OIDC discovery endpoint. Verify the issuer URL is correct and accessible."
            )
        }
    }

    // -------------------------------------------------------------------------
    // OIDC Login Flow
    // -------------------------------------------------------------------------

    override fun initiateSsoLogin(orgSlug: String, redirectUri: String): SsoLoginUrlResponse {
        val config = ssoConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("SSO configuration not found for organisation '$orgSlug'")
        val discovery = fetchOidcDiscovery(config.issuerUrl)
        val authorizationEndpoint = discovery["authorization_endpoint"] as? String
            ?: throw BadRequestException("OIDC discovery missing authorization_endpoint")

        // Purge any expired state entries to prevent unbounded map growth.
        val now = Instant.now()
        pendingStates.entries.removeIf { (_, v) -> now.isAfter(v.expiresAt) }

        val state = UUID.randomUUID().toString().replace("-", "")
        // Store the redirect URI alongside the state so the callback can reuse it verbatim.
        pendingStates[state] = PendingOidcState(
            orgSlug = orgSlug,
            redirectUri = redirectUri,
            expiresAt = now.plusSeconds(600)
        )

        val params = mapOf(
            "response_type" to "code",
            "client_id" to config.clientId,
            "redirect_uri" to redirectUri,
            "scope" to "openid profile email",
            "state" to state
        )
        val query = params.entries.joinToString("&") { (k, v) ->
            "${encode(k)}=${encode(v)}"
        }
        val loginUrl = "$authorizationEndpoint?$query"
        log.info("Initiated SSO login for org=$orgSlug provider=${config.provider}")
        return SsoLoginUrlResponse(loginUrl = loginUrl, state = state)
    }

    override fun handleSsoCallback(
        orgSlug: String,
        code: String,
        state: String
    ): SsoCallbackResponse {
        // Validate state and recover the redirect URI that was used for the authorization request.
        val pendingState = pendingStates.remove(state)
            ?: throw BadRequestException("Invalid or expired SSO state parameter")
        if (pendingState.orgSlug != orgSlug) {
            throw BadRequestException("SSO state does not match organisation")
        }
        if (Instant.now().isAfter(pendingState.expiresAt)) {
            throw BadRequestException("SSO state has expired; please restart the login flow")
        }

        val config = ssoConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("SSO configuration not found for organisation '$orgSlug'")

        val discovery = fetchOidcDiscovery(config.issuerUrl)
        val tokenEndpoint = discovery["token_endpoint"] as? String
            ?: throw BadRequestException("OIDC discovery missing token_endpoint")

        // Exchange authorization code for tokens using the same redirect URI as the
        // initial authorization request — OIDC requires an exact match.
        val tokenResponse = exchangeCodeForTokens(
            tokenEndpoint = tokenEndpoint,
            clientId = config.clientId,
            clientSecret = config.clientSecret,
            code = code,
            redirectUri = pendingState.redirectUri
        )

        val idToken = tokenResponse["id_token"] as? String
            ?: throw BadRequestException("OIDC token response missing id_token")

        // Parse claims from the ID token payload.
        // NOTE: This decodes the payload without verifying the JWT signature.
        // Full OIDC compliance requires validating the signature against the IdP's JWKS,
        // checking the `iss`, `aud`, and `nonce` claims, and enforcing expiry.
        // TLS protects the transport but does not replace cryptographic token validation.
        val claims = parseJwtClaims(idToken)

        val attrMappings: Map<String, String> = try {
            objectMapper.readValue(config.attributeMappings)
        } catch (ex: Exception) {
            log.warn("Failed to parse attribute_mappings for org=$orgSlug, falling back to defaults: ${ex.message}")
            mapOf("email" to "email", "name" to "name")
        }

        val email = claims[attrMappings.getOrDefault("email", "email")] as? String
            ?: throw BadRequestException("ID token missing email claim")
        val name = claims[attrMappings.getOrDefault("name", "name")] as? String ?: email

        // JIT user provisioning — create the user account on first SSO login.
        val user = userRepository.findByEmail(email)
            ?: userRepository.save(User(email = email, name = name))

        // Role mapping from IdP groups
        val groupsClaimKey = attrMappings["role"] ?: "groups"
        @Suppress("UNCHECKED_CAST")
        val groups = claims[groupsClaimKey] as? List<String> ?: emptyList()
        val role = resolveRole(groups, config.groupRoleMappings)

        // Create or synchronise org membership — update role on every SSO login so that
        // IdP group changes take effect without manual intervention.
        val existingMembership = membershipRepository.findByOrgSlugAndUserId(orgSlug, user.id)
        if (existingMembership == null) {
            membershipRepository.save(OrganisationMembership(orgSlug = orgSlug, userId = user.id, role = role))
        } else if (existingMembership.role != role) {
            existingMembership.role = role
            membershipRepository.save(existingMembership)
        }

        log.info("SSO callback: JIT provisioned/found user=${user.id} email=$email org=$orgSlug role=$role")

        val jwt = generateJwt(user.id, email, orgSlug)
        return SsoCallbackResponse(token = jwt, userId = user.id, email = email, name = name)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Lightweight save-time check: ensures the issuer URL uses HTTPS.
     * Full SSRF protection (private-range / loopback blocking) is applied in
     * [fetchOidcDiscovery] and [exchangeCodeForTokens] before any outbound call is made.
     */
    private fun validateIssuerUrl(url: String) {
        try {
            val uri = java.net.URI(url)
            if (uri.scheme?.lowercase() != "https") {
                throw BadRequestException("SSO issuer URL must use HTTPS (got scheme '${uri.scheme}')")
            }
        } catch (ex: BadRequestException) {
            throw ex
        } catch (ex: Exception) {
            throw BadRequestException("Invalid issuer URL: ${ex.message}")
        }
    }

    private fun fetchOidcDiscovery(issuerUrl: String): Map<String, Any> {
        // Full SSRF guard: reject private/loopback targets before making the request.
        try {
            UrlValidator.validate(issuerUrl)
        } catch (ex: IllegalArgumentException) {
            throw BadRequestException("Invalid issuer URL: ${ex.message}")
        }
        val discoveryUrl = "$issuerUrl/.well-known/openid-configuration"
        @Suppress("UNCHECKED_CAST")
        return restTemplate.getForObject(discoveryUrl, Map::class.java) as? Map<String, Any>
            ?: throw BadRequestException("Empty or invalid OIDC discovery document at $discoveryUrl")
    }

    private fun exchangeCodeForTokens(
        tokenEndpoint: String,
        clientId: String,
        clientSecret: String?,
        code: String,
        redirectUri: String
    ): Map<String, Any> {
        // Validate the token endpoint from the discovery doc before calling it.
        try {
            UrlValidator.validate(tokenEndpoint)
        } catch (ex: IllegalArgumentException) {
            throw BadRequestException("OIDC token endpoint is not a safe HTTPS URL: ${ex.message}")
        }
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", clientId)
            clientSecret?.let { add("client_secret", it) }
            add("code", code)
            add("redirect_uri", redirectUri)
        }
        @Suppress("UNCHECKED_CAST")
        return restTemplate.postForObject(
            tokenEndpoint,
            HttpEntity(body, headers),
            Map::class.java
        ) as? Map<String, Any>
            ?: throw BadRequestException("Empty or invalid token endpoint response")
    }

    /**
     * Decodes the payload of a JWT without verifying the signature.
     *
     * WARNING: This does **not** perform full OIDC token validation.  A production-grade
     * implementation must also verify the JWT signature against the IdP's published JWKS,
     * validate the `iss` and `aud` claims, and enforce token expiry.
     */
    private fun parseJwtClaims(idToken: String): Map<String, Any> {
        val parts = idToken.split(".")
        if (parts.size < 2) throw BadRequestException("Malformed ID token")
        val payloadJson = String(Base64.getUrlDecoder().decode(padBase64(parts[1])), StandardCharsets.UTF_8)
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(payloadJson, Map::class.java) as Map<String, Any>
    }

    private fun padBase64(s: String): String {
        val mod = s.length % 4
        return if (mod == 0) s else s + "=".repeat(4 - mod)
    }

    /**
     * Resolves the [MemberRole] for a user based on their IdP groups and the organisation's
     * [groupRoleMappingsJson].
     *
     * When the user belongs to multiple mapped groups, the **highest-privilege** role wins
     * (ADMIN > MEMBER > VIEWER > SERVICE_ACCOUNT), giving deterministic results regardless of
     * the order in which the IdP returns group claims.  Falls back to [MemberRole.MEMBER] when
     * no groups match or the mappings JSON is invalid.
     */
    internal fun resolveRole(groups: List<String>, groupRoleMappingsJson: String): MemberRole {
        if (groups.isEmpty()) return MemberRole.MEMBER
        val mappings: Map<String, String> = try {
            objectMapper.readValue(groupRoleMappingsJson)
        } catch (ex: Exception) {
            log.warn("Failed to parse group_role_mappings, falling back to MEMBER role: ${ex.message}")
            return MemberRole.MEMBER
        }
        val resolvedRoles = groups.mapNotNull { group ->
            val roleName = mappings[group] ?: return@mapNotNull null
            try { MemberRole.valueOf(roleName) } catch (_: IllegalArgumentException) { null }
        }.toSet()
        if (resolvedRoles.isEmpty()) return MemberRole.MEMBER
        return ROLE_PRIVILEGE_ORDER.first { it in resolvedRoles }
    }

    /**
     * Generates a compact HS256-signed JWT for the authenticated Factstore user.
     *
     * Payload claims: `sub` (userId), `email`, `org` (orgSlug), `iat`, `exp`.
     * The payload is serialised with [ObjectMapper] to correctly escape any special
     * characters in the email or orgSlug values.
     */
    internal fun generateJwt(userId: UUID, email: String, orgSlug: String): String {
        val now = Instant.now()
        val exp = now.plusSeconds(jwtExpiryHours * 3_600)

        val headerJson = objectMapper.writeValueAsString(mapOf("alg" to "HS256", "typ" to "JWT"))
        val payloadJson = objectMapper.writeValueAsString(
            mapOf(
                "sub" to userId.toString(),
                "email" to email,
                "org" to orgSlug,
                "iat" to now.epochSecond,
                "exp" to exp.epochSecond
            )
        )
        val header = base64Url(headerJson)
        val payload = base64Url(payloadJson)
        val signingInput = "$header.$payload"
        val signature = hmacSha256(signingInput, jwtSecret)
        return "$signingInput.$signature"
    }

    private fun base64Url(value: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun hmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(data.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private fun SsoConfig.toResponse() = SsoConfigResponse(
        id = id,
        orgSlug = orgSlug,
        provider = provider,
        issuerUrl = issuerUrl,
        clientId = clientId,
        attributeMappings = attributeMappings,
        groupRoleMappings = groupRoleMappings,
        isMandatory = isMandatory,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
