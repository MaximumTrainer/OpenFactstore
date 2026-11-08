package com.factstore.security

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * #205 (#155 FR-1.4) — Prometheus keeps scraping once enforcement is on.
 *
 * `/actuator/prometheus` needs a credential carrying `metrics:read`, and a scrape key supplied by
 * configuration (`security.bootstrap.metrics-api-key`) carries exactly that and nothing else, so
 * the monitoring stack's secret cannot read or change any fact.
 */
@SpringBootTest(
    properties = [
        "security.enforce-auth=true",
        "security.bootstrap.metrics-api-key=" + MetricsScrapeAuthTest.SCRAPE_KEY,
        "management.endpoints.web.exposure.include=health,info,prometheus,metrics,env"
    ]
)
@AutoConfigureMockMvc
// Spring Boot tests switch metrics export off unless asked; the scrape endpoint needs it.
@AutoConfigureObservability
class MetricsScrapeAuthTest {

    companion object {
        const val SCRAPE_KEY = "fss_a1b2c3d4a1b2c3d4a1b2c3d4a1b2c3d4a1b2c3d4a1b2c3d4a1b2c3d4a1b2c3d4"
    }

    @Autowired lateinit var mockMvc: MockMvc

    @BeforeEach
    fun clear() {
        SecurityContextHolder.clearContext()
        TestSecurityContextHolder.clearContext()
    }

    @Test
    fun `the scrape endpoint refuses a request with no credential`() {
        mockMvc.get("/actuator/prometheus").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `the configured scrape key can scrape`() {
        mockMvc.get("/actuator/prometheus") { header("Authorization", "Bearer $SCRAPE_KEY") }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("jvm_memory")) }
        }
    }

    @Test
    fun `the scrape key reads nothing else`() {
        mockMvc.get("/api/v1/flows") { header("Authorization", "Bearer $SCRAPE_KEY") }
            .andExpect { status { isForbidden() } }
        mockMvc.get("/actuator/env") { header("Authorization", "Bearer $SCRAPE_KEY") }
            .andExpect { status { isForbidden() } }
    }
}
