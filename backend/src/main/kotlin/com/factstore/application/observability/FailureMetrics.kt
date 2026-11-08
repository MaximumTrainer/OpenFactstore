package com.factstore.application.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * The failure counters that alert rules are written against (#185).
 *
 * Every tag takes a value from a fixed set. An exception class name, a fact id or a user id as a
 * tag value would create a new time series per value — the classic way to take a Prometheus
 * server down — so anything outside the known set is folded into a single bucket.
 *
 * The `service` tag comes from `factstore.cqrs.role` (`command` or `query`); an unsplit
 * deployment reports `all`.
 */
@Component
class FailureMetrics(
    private val registry: MeterRegistry,
    @Value("\${factstore.cqrs.role:all}") role: String
) {

    val service: String = if (role in KNOWN_ROLES) role else "all"

    companion object {
        private val KNOWN_ROLES = setOf("command", "query", "all")

        /** Error types reported as themselves; anything else is `Unexpected`. */
        val KNOWN_ERROR_TYPES = setOf(
            "NotFoundException",
            "ConflictException",
            "ForbiddenException",
            "AccessDeniedException",
            "BadRequestException",
            "HttpMessageNotReadableException",
            "MethodArgumentNotValidException",
            "PullRequestNotFoundException",
            "IntegrityException"
        )
        const val UNEXPECTED = "Unexpected"
    }

    /** Where an integrity failure was detected. */
    enum class IntegritySource(val tag: String) {
        API("api"),
        LEDGER("ledger"),
        PROJECTION("projection")
    }

    fun error(exception: Throwable, status: Int) {
        val type = exception::class.simpleName?.takeIf { it in KNOWN_ERROR_TYPES } ?: UNEXPECTED
        Counter.builder("factstore.errors")
            .description("Errors returned by the API, by bounded exception type")
            .tag("type", type)
            .tag("service", service)
            .tag("status", status.toString())
            .register(registry)
            .increment()
    }

    fun integrityFailure(source: IntegritySource) {
        Counter.builder("factstore.integrity.failures")
            .description("Integrity failures: the most serious signal Factstore produces")
            .tag("source", source.tag)
            .tag("service", service)
            .register(registry)
            .increment()
    }

    fun eventPublishFailure() {
        Counter.builder("factstore.event.publish.failures")
            .description("Post-commit domain event publishes that failed and were left in the outbox")
            .tag("service", service)
            .register(registry)
            .increment()
    }

    fun projectionFailure() {
        Counter.builder("factstore.projection.failures")
            .description("Domain events the read-model projector could not apply")
            .tag("service", service)
            .register(registry)
            .increment()
    }

    fun notificationDeadLetter(channel: String) {
        Counter.builder("factstore.notification.dead.letters")
            .description("Notification deliveries that exhausted their retries")
            .tag("channel", channel)
            .tag("service", service)
            .register(registry)
            .increment()
    }

    fun authFailure(reason: String) {
        Counter.builder("factstore.auth.failures")
            .description("Rejected credentials, by bounded reason")
            .tag("reason", reason)
            .tag("service", service)
            .register(registry)
            .increment()
    }
}
