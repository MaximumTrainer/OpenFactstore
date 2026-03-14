package com.factstore.config

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
class RestTemplateConfig {

    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate = builder.build()

    /**
     * Dedicated [RestTemplate] for SSO OIDC outbound calls (discovery + token exchange).
     * Uses conservative timeouts to prevent slow/unresponsive IdP endpoints from blocking
     * request threads indefinitely.
     */
    @Bean("ssoRestTemplate")
    fun ssoRestTemplate(builder: RestTemplateBuilder): RestTemplate = builder
        .setConnectTimeout(Duration.ofSeconds(10))
        .setReadTimeout(Duration.ofSeconds(30))
        .build()
}
