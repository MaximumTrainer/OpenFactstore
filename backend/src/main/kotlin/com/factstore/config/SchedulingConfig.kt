package com.factstore.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Turns on `@Scheduled` work, currently the event outbox relay (#185).
 *
 * Switched off in the test profile, where tests drive the relay directly instead of racing a
 * background thread.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = ["factstore.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
class SchedulingConfig
