package com.factstore.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.core.io.FileSystemResource
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.Properties

/**
 * #209 (#155 FR-1.1, acceptance criterion 1) — the shipped configuration enforces authentication.
 *
 * The test suite's own application.yml replaces the main one on the classpath, so the shipped
 * `security.*` settings are lifted from `src/main/resources/application.yml` here, with no
 * environment overrides: what a fresh deployment gets.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = [EnforcedByDefaultTest.ShippedSecurityConfig::class])
class EnforcedByDefaultTest {

    @Autowired lateinit var mockMvc: MockMvc

    class ShippedSecurityConfig : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(context: ConfigurableApplicationContext) {
            val shipped = Properties().apply {
                shippedYaml().forEach { (k, v) ->
                    val key = k.toString()
                    if (key.startsWith("security.enforce-auth") || key.startsWith("security.warn-mode") ||
                        key.startsWith("security.tenancy.")) put(key, v)
                }
            }
            context.environment.propertySources.addFirst(PropertiesPropertySource("shipped-security", shipped))
        }
    }

    companion object {
        fun shippedYaml(): Properties =
            YamlPropertiesFactoryBean().apply { setResources(FileSystemResource("src/main/resources/application.yml")) }.getObject()!!
    }

    @BeforeEach
    fun clear() {
        SecurityContextHolder.clearContext()
        TestSecurityContextHolder.clearContext()
    }

    @Test
    fun `a fresh deployment refuses an unauthenticated request with a Bearer challenge`() {
        mockMvc.get("/api/v1/flows").andExpect {
            status { isUnauthorized() }
            header { string("WWW-Authenticate", "Bearer realm=\"factstore\"") }
        }
    }

    @Test
    fun `the shipped defaults enforce, do not merely warn, and require operator for cross-organisation reads`() {
        val yaml = shippedYaml()
        assertThat(yaml.getProperty("security.enforce-auth")).isEqualTo("\${SECURITY_ENFORCE_AUTH:true}")
        assertThat(yaml.getProperty("security.warn-mode")).isEqualTo("\${SECURITY_WARN_MODE:false}")
        assertThat(yaml.getProperty("security.tenancy.unbound-requires-operator"))
            .isEqualTo("\${SECURITY_TENANCY_UNBOUND_REQUIRES_OPERATOR:true}")
    }
}
