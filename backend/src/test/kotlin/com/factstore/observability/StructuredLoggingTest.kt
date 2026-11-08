package com.factstore.observability

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import com.factstore.config.logging.FactstoreJsonLogFormatter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.logging.LoggingInitializationContext
import org.springframework.boot.logging.LoggingSystem
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

/**
 * #185 — structured logging.
 *
 * Outside local development every log line is one JSON object carrying the correlation id and
 * the service role, so the command and query services can be told apart and one request can be
 * followed across both. The field names are the schema SIEM shipping (#199) will consume.
 */
@ExtendWith(OutputCaptureExtension::class)
class StructuredLoggingTest {

    private val mapper = jacksonObjectMapper()

    private fun environment(vararg props: Pair<String, Any>) = StandardEnvironment().apply {
        propertySources.addFirst(MapPropertySource("test", mapOf(*props)))
    }

    private fun event(message: String, level: Level = Level.INFO, throwable: Throwable? = null): LoggingEvent {
        val logger = (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger("com.factstore.Example")
        return LoggingEvent("fqcn", logger, level, message, throwable, null)
    }

    @AfterEach
    fun clearMdc() = MDC.clear()

    @Test
    fun `a log line is one JSON object with the agreed fields`() {
        MDC.put("correlationId", "corr-json-1")
        MDC.put("path", "/api/v1/flows")
        MDC.put("method", "POST")
        MDC.put("status", "500")
        val formatter = FactstoreJsonLogFormatter(environment("factstore.cqrs.role" to "command"))

        val line = formatter.format(event("flow creation failed"))

        assertThat(line).endsWith("\n").doesNotContain("\n\n")
        val json = mapper.readTree(line)
        assertThat(json["timestamp"].asText()).isNotBlank()
        assertThat(json["level"].asText()).isEqualTo("INFO")
        assertThat(json["logger"].asText()).isEqualTo("com.factstore.Example")
        assertThat(json["message"].asText()).isEqualTo("flow creation failed")
        assertThat(json["correlationId"].asText()).isEqualTo("corr-json-1")
        assertThat(json["service"].asText()).isEqualTo("command")
        assertThat(json["path"].asText()).isEqualTo("/api/v1/flows")
        assertThat(json["method"].asText()).isEqualTo("POST")
        assertThat(json["status"].asText()).isEqualTo("500")
    }

    @Test
    fun `the command and query roles are told apart by the service field`() {
        val query = FactstoreJsonLogFormatter(environment("factstore.cqrs.role" to "query"))
        val unsplit = FactstoreJsonLogFormatter(environment())

        assertThat(mapper.readTree(query.format(event("x")))["service"].asText()).isEqualTo("query")
        assertThat(mapper.readTree(unsplit.format(event("x")))["service"].asText()).isEqualTo("all")
    }

    @Test
    fun `a stack trace stays inside the single JSON line`() {
        val formatter = FactstoreJsonLogFormatter(environment())

        val line = formatter.format(event("boom", Level.ERROR, IllegalStateException("inner detail")))

        assertThat(line.trimEnd('\n')).doesNotContain("\n")
        assertThat(mapper.readTree(line)["stackTrace"].asText()).contains("IllegalStateException").contains("inner detail")
    }

    @Test
    fun `logback-spring emits JSON unless the plain format is chosen`(output: CapturedOutput) {
        val loggingSystem = LoggingSystem.get(javaClass.classLoader)
        try {
            loggingSystem.cleanUp()
            loggingSystem.initialize(LoggingInitializationContext(environment("factstore.cqrs.role" to "query")), null, null)
            MDC.put("correlationId", "corr-logback-json")

            LoggerFactory.getLogger("com.factstore.LogbackCheck").info("structured hello")

            val line = output.out.lines().single { it.contains("structured hello") }
            val json = mapper.readTree(line)
            assertThat(json["correlationId"].asText()).isEqualTo("corr-logback-json")
            assertThat(json["service"].asText()).isEqualTo("query")
        } finally {
            loggingSystem.cleanUp()
            loggingSystem.initialize(LoggingInitializationContext(environment("factstore.logging.format" to "plain")), null, null)
        }
    }
}
