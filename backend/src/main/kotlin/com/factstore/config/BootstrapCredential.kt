package com.factstore.config

import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.User
import com.factstore.core.domain.security.Permission
import com.factstore.core.port.outbound.IApiKeyRepository
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.application.ApiKeyService
import com.factstore.dto.CreateApiKeyRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * First-run bootstrap (#155 FR-7).
 *
 * A fresh deployment with enforcement on has to be usable: otherwise there is no way to
 * authenticate, and therefore no way to create the credential that would let you authenticate.
 * Starting an unusable service and leaving the operator to discover it is the worst of the
 * options, so this either creates a bootstrap admin credential or fails fast with an
 * actionable message.
 *
 * The credential is written to the log **once**, at startup, with instructions to rotate it.
 * That is a deliberate trade: a secret in a startup log is not ideal, but it is visible,
 * short-lived by TTL, and far better than a well-known default.
 */
@Component
class BootstrapCredential(
    private val apiKeyService: ApiKeyService,
    private val apiKeyRepository: IApiKeyRepository,
    private val userRepository: IUserRepository,
    @Value("\${security.enforce-auth:false}") private val enforceAuth: Boolean,
    @Value("\${security.bootstrap.enabled:true}") private val bootstrapEnabled: Boolean,
    @Value("\${security.bootstrap.admin-email:admin@localhost}") private val adminEmail: String,
    @Value("\${security.bootstrap.ttl-days:7}") private val ttlDays: Int,
    /**
     * A bootstrap admin credential supplied by configuration (#155 FR-7.1), as an alternative
     * to the generated one.
     *
     * This is what lets an unattended environment authenticate without scraping a value out of
     * a startup log: CI, local development, a container image. It is applied whatever
     * `enforce-auth` is set to, because method security is always live — so even a permissive
     * deployment needs a credential to create a flow or upload a policy.
     */
    @Value("\${security.bootstrap.api-key:}") private val seededApiKey: String,
    /**
     * The key Prometheus scrapes with, supplied by configuration (#205). It carries only
     * `metrics:read`, so it opens `/actuator/prometheus` and nothing else.
     */
    @Value("\${security.bootstrap.metrics-api-key:}") private val seededMetricsKey: String = "",
    @Value("\${security.bootstrap.metrics-api-key-ttl-days:365}") private val metricsKeyTtlDays: Int = 365
) {

    private val log = LoggerFactory.getLogger(BootstrapCredential::class.java)

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun bootstrapIfNeeded() {
        if (seededMetricsKey.isNotBlank()) seedMetricsCredential()
        // A configuration-supplied credential is honoured regardless of enforcement, because
        // @PreAuthorize is always live: without a credential, an unauthenticated caller cannot
        // create a flow even when authentication is not enforced.
        if (seededApiKey.isNotBlank()) {
            seedConfiguredCredential()
            return
        }

        if (!enforceAuth) {
            log.warn(
                "SECURITY_ENFORCE_AUTH is false: every endpoint is reachable without a " +
                    "credential. This is for local development only — see docs/authentication.md."
            )
            return
        }

        // Any existing admin-scoped key means the system has already been bootstrapped.
        val existingAdminKey = runCatching {
            apiKeyRepository.findByOwnerId(bootstrapUser().id)
                .any { it.isActive && it.scopes.contains(Permission.ADMIN) }
        }.getOrElse { false }

        if (existingAdminKey) {
            log.info("Authentication is enforced and an admin credential already exists")
            return
        }

        if (!bootstrapEnabled) {
            throw IllegalStateException(
                "Refusing to start: authentication is enforced but there is no admin credential " +
                    "and bootstrapping is disabled (security.bootstrap.enabled=false). Nothing " +
                    "would be able to authenticate. Either provide a credential or re-enable " +
                    "bootstrapping."
            )
        }

        val user = bootstrapUser()
        // Minting an admin key requires holding `admin`; grant it to this one internal call
        // rather than making the escalation check skippable.
        val created = withAdminAuthority {
            apiKeyService.createApiKey(
                CreateApiKeyRequest(
                    ownerId = user.id,
                    ownerType = OwnerType.USER,
                    label = "bootstrap admin (rotate me)",
                    scopes = listOf(Permission.ADMIN.scope),
                    ttlDays = ttlDays
                )
            )
        }

        log.warn(
            """
            |
            |================ OpenFactstore bootstrap credential ================
            | A first-run admin API key has been created because authentication is
            | enforced and no admin credential existed.
            |
            |   key:     ${created.plainTextKey}
            |   owner:   ${user.email}
            |   expires: ${created.expiresAt} (${ttlDays} days)
            |
            | This is shown once and is not recoverable. Use it to create your real
            | admin identity, then revoke it:
            |
            |   DELETE /api/v1/api-keys/${created.id}/revoke
            |
            | Set security.bootstrap.enabled=false once you have done so.
            |====================================================================
            """.trimMargin()
        )
    }

    private fun seedConfiguredCredential() {
        val user = bootstrapUser()
        try {
            withAdminAuthority {
                apiKeyService.seedApiKey(
                    ownerId = user.id,
                    ownerType = OwnerType.USER,
                    label = "bootstrap admin (from configuration)",
                    scopes = setOf(Permission.ADMIN),
                    plainTextKey = seededApiKey,
                    ttlDays = ttlDays
                )
            }
        } catch (ex: IllegalArgumentException) {
            // A malformed seed is a configuration mistake worth failing on: the operator
            // believes they have a working credential and they do not.
            throw IllegalStateException(
                "security.bootstrap.api-key is not a usable API key: ${ex.message}", ex
            )
        }
        if (!enforceAuth) {
            log.warn(
                "SECURITY_ENFORCE_AUTH is false: every endpoint is reachable without a " +
                    "credential, though operations carrying an authorisation rule still need one. " +
                    "This is for local development only — see docs/authentication.md."
            )
        }
    }

    private fun seedMetricsCredential() {
        val user = bootstrapUser()
        try {
            withAdminAuthority {
                apiKeyService.seedApiKey(
                    ownerId = user.id,
                    ownerType = OwnerType.USER,
                    label = "prometheus scrape (from configuration)",
                    scopes = setOf(Permission.METRICS_READ),
                    plainTextKey = seededMetricsKey,
                    ttlDays = metricsKeyTtlDays
                )
            }
        } catch (ex: IllegalArgumentException) {
            throw IllegalStateException(
                "security.bootstrap.metrics-api-key is not a usable API key: ${ex.message}", ex
            )
        }
    }

    private fun bootstrapUser(): User =
        userRepository.findByEmail(adminEmail)
            ?: userRepository.save(User(email = adminEmail, name = "Bootstrap Administrator"))

    /**
     * Runs [block] with the `admin` authority, so the no-escalation rule in
     * [ApiKeyService.createApiKey] still applies to it rather than being bypassed.
     */
    private fun <T> withAdminAuthority(block: () -> T): T {
        val context = SecurityContextHolder.getContext()
        val previous = context.authentication
        context.authentication = UsernamePasswordAuthenticationToken(
            "bootstrap",
            null,
            listOf(SimpleGrantedAuthority(Permission.ADMIN.authority))
        )
        return try {
            block()
        } finally {
            context.authentication = previous
        }
    }
}
