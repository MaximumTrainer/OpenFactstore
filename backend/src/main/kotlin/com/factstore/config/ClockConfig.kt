package com.factstore.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** The application clock, injected so tests can freeze it (#220). */
@Configuration
class ClockConfig {
    @Bean
    @ConditionalOnMissingBean
    fun clock(): Clock = Clock.systemUTC()
}
