package com.factstore.security

import com.factstore.application.ApiKeyRateLimiter
import com.factstore.application.ApiKeyValidationCache
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * #205 (#155 §5 step 2) — warn mode is how an operator finds every caller that enforcement would
 * break, before switching it on. So every would-be refusal is let through, logged and counted as
 * `factstore_warn_mode_refusals_total`, whether enforcement is already on or not yet.
 */
class WarnModeTest {

    abstract inner class Base {
        @Autowired lateinit var mockMvc: MockMvc
        @Autowired lateinit var meterRegistry: MeterRegistry
        @Autowired lateinit var rateLimiter: ApiKeyRateLimiter
        @Autowired lateinit var validationCache: ApiKeyValidationCache

        fun refusals(source: String) =
            meterRegistry.find("factstore.warn.mode.refusals").tags("source", source).counter()?.count() ?: 0.0

        @BeforeEach
        fun clear() {
            SecurityContextHolder.clearContext()
            TestSecurityContextHolder.clearContext()
            rateLimiter.reset()
            validationCache.clear()
        }
    }

    @Nested
    @SpringBootTest(properties = ["security.enforce-auth=false", "security.warn-mode=true"])
    @AutoConfigureMockMvc
    inner class BeforeTheFlip : Base() {
        @Test
        fun `an anonymous caller is allowed but counted as one enforcement would refuse`() {
            val before = refusals("rule")

            mockMvc.get("/api/v1/flows").andExpect { status { isOk() } }

            assertThat(refusals("rule")).isEqualTo(before + 1)
        }
    }

    @Nested
    @SpringBootTest(properties = ["security.enforce-auth=true", "security.warn-mode=true"])
    @AutoConfigureMockMvc
    inner class WithEnforcementOn : Base() {
        @Test
        fun `an anonymous caller is allowed through the credential gate and counted`() {
            val before = refusals("rule")

            mockMvc.get("/api/v1/flows").andExpect { status { isOk() } }

            assertThat(refusals("rule")).isEqualTo(before + 1)
        }

        @Test
        fun `a bad credential is allowed and counted`() {
            val before = refusals("credential")

            mockMvc.get("/api/v1/flows") { header("Authorization", "Bearer fsp_${"0".repeat(64)}") }
                .andExpect { status { isOk() } }

            assertThat(refusals("credential")).isEqualTo(before + 1)
        }
    }
}
