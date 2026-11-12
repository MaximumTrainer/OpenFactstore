package com.factstore.application.siem

import com.factstore.config.CorrelationIdFilter
import com.factstore.core.domain.AuditEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * The SIEM stream (#199): one OCSF event per line on the `factstore.audit` logger.
 *
 * Factstore never sends to the SIEM itself and never waits on it. The platform's log collector
 * (Fluent Bit, Vector or the Datadog Agent) reads stdout and forwards these lines to Splunk HEC or
 * Datadog Logs; if the SIEM is down, the collector buffers. See `docs/integrations/siem.md`.
 */
@Component
class SiemStream(private val objectMapper: ObjectMapper) {
    private val stream = LoggerFactory.getLogger(LOGGER)
    private val log = LoggerFactory.getLogger(SiemStream::class.java)

    fun emit(event: Map<String, Any?>) {
        try {
            stream.info(objectMapper.writeValueAsString(event))
        } catch (e: Exception) {
            // Never fail a request because an audit line could not be written; say so loudly.
            log.error("Could not write OCSF event {}: {}", event["class_uid"], e.message)
        }
    }

    /** Streams an audit record once it is committed: a rolled-back record never happened. */
    fun auditRecorded(event: AuditEvent) {
        val correlationId = CorrelationIdFilter.current()
        val line = Ocsf.auditRecord(event, correlationId)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = emit(line)
            })
        } else {
            emit(line)
        }
    }

    companion object {
        const val LOGGER = "factstore.audit"
    }
}
