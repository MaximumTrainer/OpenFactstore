package com.factstore.config.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.logging.structured.StructuredLogFormatter
import org.springframework.core.env.Environment
import java.time.Instant

/**
 * One JSON object per log line (#185), used by `logback-spring.xml` outside local development.
 *
 * Fields: `timestamp`, `level`, `logger`, `message`, `correlationId`, `service`, `path`,
 * `method`, `status`, plus `thread` and `stackTrace` when there is one. `service` is the CQRS
 * role (`command` or `query`, or `all` for an unsplit deployment), so the two services that run
 * the same JAR can be told apart. The request fields come from the MDC that
 * [com.factstore.config.CorrelationIdFilter] populates.
 *
 * These names are the schema SIEM shipping (#199) is to consume, so changing one is a breaking
 * change for whatever reads the logs downstream.
 */
class FactstoreJsonLogFormatter(environment: Environment) : StructuredLogFormatter<ILoggingEvent> {

    private val service: String = environment.getProperty("factstore.cqrs.role")
        ?.takeIf { it in setOf("command", "query") } ?: "all"

    private val mapper = ObjectMapper()

    override fun format(event: ILoggingEvent): String {
        val mdc = event.mdcPropertyMap ?: emptyMap()
        val fields = LinkedHashMap<String, Any?>()
        fields["timestamp"] = Instant.ofEpochMilli(event.timeStamp).toString()
        fields["level"] = event.level.toString()
        fields["logger"] = event.loggerName
        if (event.loggerName == "factstore.audit") {
            // #199: the SIEM stream. The OCSF event is the payload; the envelope stays the same.
            val ocsf = runCatching { mapper.readTree(event.formattedMessage) }.getOrNull()
            fields["message"] = ocsf?.get("class_name")?.asText() ?: event.formattedMessage
            fields["ocsf"] = ocsf
        } else {
            fields["message"] = event.formattedMessage
        }
        fields["correlationId"] = mdc["correlationId"]
        fields["service"] = service
        fields["path"] = mdc["path"]
        fields["method"] = mdc["method"]
        fields["status"] = mdc["status"]
        fields["thread"] = event.threadName
        event.throwableProxy?.let { fields["stackTrace"] = ThrowableProxyUtil.asString(it) }
        return mapper.writeValueAsString(fields.filterValues { it != null }) + "\n"
    }
}
