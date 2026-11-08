package com.factstore.observability

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.core.io.FileSystemResource
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.Properties

/**
 * #185 — liveness and readiness probe groups.
 *
 * The test suite's own application.yml replaces the main one on the classpath, so the
 * `management.*` settings are lifted from the real `src/main/resources/application.yml` here.
 * That way the test exercises the configuration that ships, not a copy of it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = [HealthProbesTest.ShippedManagementConfig::class])
class HealthProbesTest {

    @Autowired lateinit var mockMvc: MockMvc

    class ShippedManagementConfig : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(context: ConfigurableApplicationContext) {
            val management = Properties().apply {
                load("src/main/resources/application.yml").forEach { (k, v) ->
                    if (k.toString().startsWith("management.")) put(k, v)
                }
            }
            context.environment.propertySources.addFirst(PropertiesPropertySource("shipped-management", management))
        }
    }

    companion object {
        fun load(path: String): Properties =
            YamlPropertiesFactoryBean().apply { setResources(FileSystemResource(path)) }.getObject()!!
    }

    @Test
    fun `the liveness probe is served and up`() {
        mockMvc.get("/actuator/health/liveness").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
        }
    }

    @Test
    fun `the readiness probe includes the database`() {
        mockMvc.get("/actuator/health/readiness").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
            jsonPath("$.components.db.status") { value("UP") }
        }
    }

    @Test
    fun `with the message broker enabled readiness also includes the broker`() {
        // The prod profile is the one that publishes through RabbitMQ.
        val prod = load("src/main/resources/application-prod.yml")

        assertThat(prod.getProperty("factstore.events.publisher")).isEqualTo("rabbitmq")
        assertThat(prod.getProperty("management.health.rabbit.enabled")).isEqualTo("true")
        assertThat(prod.getProperty("management.endpoint.health.group.readiness.include"))
            .contains("db").contains("rabbit")
    }
}
