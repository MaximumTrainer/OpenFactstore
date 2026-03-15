package com.factstore.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

/**
 * Enables Spring Method Security (@PreAuthorize, @PostAuthorize) only when
 * authentication enforcement is active. This prevents @PreAuthorize annotations
 * from blocking requests in environments where security.enforce-auth=false
 * (e.g., local development, tests).
 */
@Configuration
@ConditionalOnProperty(name = ["security.enforce-auth"], havingValue = "true")
@EnableMethodSecurity(prePostEnabled = true)
class MethodSecurityConfig
