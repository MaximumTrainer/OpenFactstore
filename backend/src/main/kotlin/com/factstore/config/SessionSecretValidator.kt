package com.factstore.config

import com.factstore.application.auth.SessionTokenService
import com.factstore.core.port.outbound.ISsoConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Refuses to run with an unusable session signing secret (#156 FR-3.1).
 *
 * The previous behaviour was a startup `WARN` while continuing to run on
 * `changeme-in-production`. A warning is not a control: anyone who knew the default could forge
 * a session token for any user, and the deployment would look healthy. So when sign-in is
 * actually reachable, an unset, placeholder or too-short secret is a startup failure.
 *
 * "Sign-in is reachable" means an interactive sign-in path exists: organisation SSO, GitHub
 * OAuth, or development sign-in. Enforcement alone does not need a session secret (#209): a
 * deployment used only through API keys never issues a session, and [SessionTokenService]
 * refuses to issue one without a usable secret anyway. So a fresh deployment, with enforcement on
 * by default, starts without one.
 */
@Component
class SessionSecretValidator(
    private val tokenService: SessionTokenService,
    private val ssoConfigRepository: ISsoConfigRepository,
    @Value("\${security.enforce-auth:false}") private val enforceAuth: Boolean,
    @Value("\${security.session.allow-unconfigured-secret:false}") private val allowUnconfigured: Boolean,
    @Value("\${spring.security.oauth2.client.registration.github.client-id:}") private val githubClientId: String = "",
    @Value("\${security.dev-login.enabled:false}") private val devLoginEnabled: Boolean = false
) {

    private val log = LoggerFactory.getLogger(SessionSecretValidator::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun validate() {
        if (tokenService.isConfigured()) {
            log.info("Session signing secret configured")
            return
        }

        val problem = tokenService.secretProblem() ?: "session signing secret is unusable"
        val ssoConfigured = runCatching { ssoConfigRepository.findAll().isNotEmpty() }
            .getOrElse { false }

        val signInReachable = ssoConfigured || githubClientId.isNotBlank() || devLoginEnabled
        if (!signInReachable) {
            log.warn(
                "No session signing secret is configured, so interactive sign-in is unavailable" +
                    (if (enforceAuth) "; API keys still work. " else ". ") +
                    "Set SSO_JWT_SECRET before configuring SSO, GitHub sign-in or development sign-in. ($problem)"
            )
            return
        }

        if (allowUnconfigured) {
            log.error(
                "Running with an unusable session signing secret because " +
                    "security.session.allow-unconfigured-secret=true. Sessions issued by this " +
                    "instance cannot be trusted. ($problem)"
            )
            return
        }

        // Fail fast rather than start an instance whose sessions can be forged.
        throw IllegalStateException(
            "Refusing to start: $problem " +
                "Interactive sign-in (SSO, GitHub or development sign-in) is configured, so sessions must be signed with a " +
                "real secret. Set SSO_JWT_SECRET, or set " +
                "security.session.allow-unconfigured-secret=true to override (not for production)."
        )
    }
}
