package com.factstore.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * Defines the [BCryptPasswordEncoder] bean in its own configuration class so that it
 * can be injected into [com.factstore.application.ApiKeyService] without creating a
 * circular dependency through [SecurityConfig] → [com.factstore.adapter.inbound.web.ApiKeyAuthFilter]
 * → [com.factstore.application.ApiKeyService] → BCryptPasswordEncoder.
 */
@Configuration
class PasswordEncoderConfig {

    @Bean
    fun passwordEncoder(): BCryptPasswordEncoder = BCryptPasswordEncoder()
}
