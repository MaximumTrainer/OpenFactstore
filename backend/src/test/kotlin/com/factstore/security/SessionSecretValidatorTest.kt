package com.factstore.security

import com.factstore.application.auth.SessionTokenService
import com.factstore.config.SessionSecretValidator
import com.factstore.core.domain.SsoConfig
import com.factstore.core.port.outbound.ISsoConfigRepository
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * #209 / #156 FR-3.1 — the session secret is required exactly when someone can sign in.
 *
 * A session can only be issued through an interactive sign-in path: organisation SSO, GitHub
 * OAuth or development sign-in. With none of them configured, enforcement alone (API keys only)
 * needs no session secret, so a fresh deployment with enforcement on by default still starts.
 * The moment a sign-in path exists, a missing or placeholder secret is a startup failure.
 */
class SessionSecretValidatorTest {

    private val placeholder = SessionTokenService("changeme-in-production", "openfactstore", 60)

    private fun validator(
        enforce: Boolean = true,
        ssoConfigured: Boolean = false,
        githubClientId: String = "",
        devLogin: Boolean = false
    ): SessionSecretValidator {
        val sso = mock<ISsoConfigRepository>()
        whenever(sso.findAll()).thenReturn(if (ssoConfigured) listOf(mock<SsoConfig>()) else emptyList())
        return SessionSecretValidator(placeholder, sso, enforce, false, githubClientId, devLogin)
    }

    @Test
    fun `enforcement with API keys only starts without a session secret`() {
        assertThatCode { validator(enforce = true).validate() }.doesNotThrowAnyException()
    }

    @Test
    fun `organisation SSO without a real secret refuses to start`() {
        assertThatThrownBy { validator(ssoConfigured = true).validate() }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `GitHub sign-in without a real secret refuses to start`() {
        assertThatThrownBy { validator(githubClientId = "gh-client").validate() }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `development sign-in without a real secret refuses to start`() {
        assertThatThrownBy { validator(devLogin = true).validate() }.isInstanceOf(IllegalStateException::class.java)
    }
}
