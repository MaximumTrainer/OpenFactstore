package com.factstore.security

import com.factstore.config.AuthorisationRules
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

/**
 * #155 FR-4 and the rollout in §5 — the rule every endpoint annotation evaluates.
 *
 * Method security is always on, so a plain `hasAuthority` on an endpoint would refuse anonymous
 * callers even on a deployment that has not turned enforcement on yet. That would be the
 * enforcement flip by the back door. These rules keep the rollout order the issue prescribes:
 *
 * - enforcement **off**: an anonymous caller is let through, as the route rules already do; a
 *   caller that *did* present a credential is held to its scopes, so least privilege applies to
 *   keys from day one.
 * - enforcement **on**: a credential carrying the scope is required.
 * - **warn mode**: a refusal is logged and the request allowed, so operators can find every
 *   caller that would break before switching enforcement on.
 */
@ExtendWith(OutputCaptureExtension::class)
class AuthorisationRulesTest {

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    private fun anonymous() {
        SecurityContextHolder.getContext().authentication =
            AnonymousAuthenticationToken("key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")))
    }

    private fun caller(vararg scopes: String) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "ci", null, scopes.map { SimpleGrantedAuthority("SCOPE_$it") }
        )
    }

    private fun rules(enforce: Boolean, warn: Boolean = false) = AuthorisationRules(enforce, warn)

    @Test
    fun `with enforcement off an anonymous caller passes, as the route rules already allow`() {
        anonymous()
        assertThat(rules(enforce = false).has("flows:write")).isTrue()
        assertThat(rules(enforce = false).authenticated()).isTrue()
    }

    @Test
    fun `with enforcement off a credential is still held to its scopes`() {
        caller("attestations:write")
        assertThat(rules(enforce = false).has("attestations:write")).isTrue()
        assertThat(rules(enforce = false).has("flows:write")).isFalse()
    }

    @Test
    fun `with enforcement on an anonymous caller is refused`() {
        anonymous()
        assertThat(rules(enforce = true).has("trails:write")).isFalse()
        assertThat(rules(enforce = true).authenticated()).isFalse()
    }

    @Test
    fun `with enforcement on the scope decides`() {
        caller("trails:write")
        assertThat(rules(enforce = true).has("trails:write")).isTrue()
        assertThat(rules(enforce = true).has("admin")).isFalse()
        assertThat(rules(enforce = true).hasAny("approvals:write", "trails:write")).isTrue()
        assertThat(rules(enforce = true).authenticated()).isTrue()
    }

    @Test
    fun `no security context at all counts as anonymous`() {
        SecurityContextHolder.clearContext()
        assertThat(rules(enforce = true).has("flows:read")).isFalse()
        assertThat(rules(enforce = false).has("flows:read")).isTrue()
    }

    @Test
    fun `warn mode logs what would have been refused and allows it`(output: CapturedOutput) {
        caller("attestations:write")

        assertThat(rules(enforce = true, warn = true).has("flows:write")).isTrue()
        assertThat(output.out).contains("WARN-MODE").contains("flows:write")
    }
}
