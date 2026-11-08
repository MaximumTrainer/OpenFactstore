package com.factstore.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Keeps dev sign-in (#156 FR-8.4) out of production.
 *
 * Dev sign-in lets anyone who can reach it become any user with any role. The application refuses
 * to start with it switched on under the `prod` profile, and logs a warning whenever it is on.
 */
@Component
class DevSignInGuard(private val environment: Environment) {

    private val log = LoggerFactory.getLogger(DevSignInGuard::class.java)

    @PostConstruct
    fun check() {
        val enabled = environment.getProperty("security.dev-login.enabled", Boolean::class.java, false)
        if (!enabled) return
        check(!environment.activeProfiles.contains("prod")) {
            "security.dev-login.enabled=true is not allowed under the prod profile: it lets anyone " +
                "sign in as any user with any role. Turn it off, or use GitHub or organisation SSO."
        }
        log.warn(
            "DEV SIGN-IN IS ENABLED (security.dev-login.enabled=true). Anyone who can reach " +
                "POST /api/v1/auth/dev-login can sign in as any user and role. Never use this outside local development."
        )
    }
}
