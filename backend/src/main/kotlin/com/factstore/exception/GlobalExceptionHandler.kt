package com.factstore.exception

import com.factstore.application.observability.FailureMetrics
import com.factstore.config.CorrelationIdFilter
import com.factstore.core.domain.NotificationSeverity
import com.factstore.core.domain.TriggerEvent
import com.factstore.core.port.inbound.INotificationService
import com.factstore.dto.NotificationEvent
import com.factstore.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler(
    private val failureMetrics: FailureMetrics,
    private val notificationService: INotificationService
) {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * Every error response carries the correlation id, and every handled error is counted (#185).
     * A 5xx body never carries the exception's own text: that stays in the log line, which the
     * same correlation id finds.
     */
    private fun respond(ex: Throwable, status: HttpStatus, error: String, message: String): ResponseEntity<ErrorResponse> {
        failureMetrics.error(ex, status.value())
        com.factstore.adapter.inbound.web.SiemRequestFilter.detail(error)
        MDC.put(CorrelationIdFilter.MDC_STATUS, status.value().toString())
        return ResponseEntity.status(status)
            .body(ErrorResponse(status.value(), error, message, correlationId = CorrelationIdFilter.current()))
    }

    /**
     * Raises an operational notification (#185), so an operator can route system failures to
     * Slack or a webhook through the ordinary notification rules. The message names the
     * correlation id and route, never the exception text. A failure to notify must not turn into a
     * second error on top of the first.
     */
    private fun notifyOperators(trigger: TriggerEvent, title: String) {
        val correlationId = CorrelationIdFilter.current() ?: "unknown"
        val route = "${MDC.get(CorrelationIdFilter.MDC_METHOD) ?: "?"} ${MDC.get(CorrelationIdFilter.MDC_PATH) ?: "?"}"
        try {
            notificationService.publishEvent(
                NotificationEvent(
                    triggerEvent = trigger,
                    title = title,
                    message = "$title on $route (correlationId=$correlationId, service=${failureMetrics.service})",
                    severity = NotificationSeverity.CRITICAL,
                    extraPayload = mapOf("correlationId" to correlationId, "service" to failureMetrics.service)
                )
            )
        } catch (e: Exception) {
            log.warn("Could not raise {} notification [correlationId={}]: {}", trigger, correlationId, e.message)
        }
    }

    /** #188: the fact exists, but no checkpoint covers it yet. */
    @ExceptionHandler(com.factstore.application.ledger.ProofPendingException::class)
    fun handleProofPending(ex: com.factstore.application.ledger.ProofPendingException): ResponseEntity<Map<String, Any?>> {
        failureMetrics.error(ex, HttpStatus.NOT_FOUND.value())
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to "proof_pending", "message" to ex.message, "correlationId" to CorrelationIdFilter.current()))
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Not found: ${ex.message}")
        return respond(ex, HttpStatus.NOT_FOUND, "Not Found", ex.message ?: "Resource not found")
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ResponseEntity<ErrorResponse> {
        log.warn("Conflict: ${ex.message}")
        return respond(ex, HttpStatus.CONFLICT, "Conflict", ex.message ?: "Conflict")
    }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<ErrorResponse> {
        log.warn("Forbidden: ${ex.message}")
        return respond(ex, HttpStatus.FORBIDDEN, "Forbidden", ex.message ?: "Not permitted")
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun handleAccessDenied(
        ex: org.springframework.security.access.AccessDeniedException
    ): ResponseEntity<ErrorResponse> {
        // Raised by @PreAuthorize. Reported as 403 rather than 500, and without echoing the
        // expression that failed.
        log.warn("Access denied: ${ex.message}")
        return respond(ex, HttpStatus.FORBIDDEN, "Forbidden", "You do not have permission to perform this action")
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        log.warn("Malformed request body: ${ex.message}")
        return respond(ex, HttpStatus.BAD_REQUEST, "Bad Request", "Malformed or unreadable request body")
    }

    /** #196: a refused fact says which rule it broke and where, so the producer can fix it. */
    @ExceptionHandler(FactRejectedException::class)
    fun handleFactRejected(ex: FactRejectedException): ResponseEntity<Map<String, Any?>> {
        log.warn("Fact refused at ingress: {}", ex.message)
        failureMetrics.error(ex, HttpStatus.BAD_REQUEST.value())
        MDC.put(CorrelationIdFilter.MDC_STATUS, HttpStatus.BAD_REQUEST.value().toString())
        return ResponseEntity.badRequest().body(
            mapOf(
                "error" to ex.error.also { com.factstore.adapter.inbound.web.SiemRequestFilter.detail(it) },
                "schemaRef" to ex.schemaRef,
                "violations" to ex.violations,
                "correlationId" to CorrelationIdFilter.current()
            )
        )
    }

    /** #190: a workload emitting facts for another service. */
    @ExceptionHandler(CrossWorkloadInjectionException::class)
    fun handleCrossWorkloadInjection(ex: CrossWorkloadInjectionException): ResponseEntity<Map<String, Any?>> {
        log.warn("Cross-workload fact injection refused: {}", ex.message)
        failureMetrics.error(ex, HttpStatus.FORBIDDEN.value())
        MDC.put(CorrelationIdFilter.MDC_STATUS, HttpStatus.FORBIDDEN.value().toString())
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            mapOf(
                "error" to "cross_workload_injection".also { com.factstore.adapter.inbound.web.SiemRequestFilter.detail(it) },
                "message" to ex.message,
                "correlationId" to CorrelationIdFilter.current()
            )
        )
    }

    /** #187: a fact whose source signature is missing or does not verify never reaches the ledger. */
    @ExceptionHandler(SignatureRejectedException::class)
    fun handleSignatureRejected(ex: SignatureRejectedException): ResponseEntity<Map<String, Any?>> {
        log.warn("Fact signature refused ({}): {}", ex.error, ex.message)
        failureMetrics.error(ex, HttpStatus.UNAUTHORIZED.value())
        failureMetrics.ingestDenial(ex.error.uppercase())
        MDC.put(CorrelationIdFilter.MDC_STATUS, HttpStatus.UNAUTHORIZED.value().toString())
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .header(org.springframework.http.HttpHeaders.WWW_AUTHENTICATE, "Signature realm=\"factstore\", algorithm=\"ed25519\"")
            .body(mapOf("error" to ex.error.also { com.factstore.adapter.inbound.web.SiemRequestFilter.detail(it) }, "message" to ex.message, "correlationId" to CorrelationIdFilter.current()))
    }

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException): ResponseEntity<ErrorResponse> {
        log.warn("Bad request: ${ex.message}")
        return respond(ex, HttpStatus.BAD_REQUEST, "Bad Request", ex.message ?: "Bad request")
    }

    /**
     * #184: an event could not be signed, so nothing was written. The key service is unreachable
     * or refused; the caller can retry. Counted as an integrity failure so it pages.
     */
    @ExceptionHandler(EventSigningException::class)
    fun handleEventSigning(ex: EventSigningException): ResponseEntity<ErrorResponse> {
        log.error("Event signing failed [correlationId={}]: {}", CorrelationIdFilter.current(), ex.message, ex)
        failureMetrics.integrityFailure(FailureMetrics.IntegritySource.SIGNING)
        return respond(ex, HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
            "The record could not be signed, so nothing was stored. Retry later.")
    }

    @ExceptionHandler(IntegrityException::class)
    fun handleIntegrity(ex: IntegrityException): ResponseEntity<ErrorResponse> {
        // The most serious signal this product produces: counted so it pages, and its detail kept
        // out of the response, where it would tell a caller exactly what was tampered with.
        log.error("Integrity error [correlationId={}]: {}", CorrelationIdFilter.current(), ex.message, ex)
        failureMetrics.integrityFailure(FailureMetrics.IntegritySource.API)
        notifyOperators(TriggerEvent.INTEGRITY_FAILURE, "Integrity check failed")
        return respond(ex, HttpStatus.INTERNAL_SERVER_ERROR, "Integrity Error",
            "A data integrity check failed. Quote the correlation id to an operator.")
    }

    @ExceptionHandler(PullRequestNotFoundException::class)
    fun handlePrNotFound(ex: PullRequestNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Pull request not found: ${ex.message}")
        return respond(ex, HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.message ?: "No pull request found for commit")
    }

    /**
     * Bean-validation failures on a `@Valid @RequestBody`.
     *
     * Without this, they fell through to the catch-all below and every one came back as a
     * **500** — so a client that sent a bad field could not tell its own mistake from the
     * server breaking, and a CI pipeline retried a request that would never succeed. The
     * offending fields are named, because "Bad Request" with nothing else is barely better
     * than the 500 it replaces.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors.joinToString("; ") {
            "${it.field}: ${it.defaultMessage ?: "is invalid"}"
        }.ifBlank { "Request validation failed" }
        log.warn("Validation failed: $details")
        return respond(ex, HttpStatus.BAD_REQUEST, "Bad Request", details)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ErrorResponse> {
        // Spring MVC's own client errors (unknown route, wrong method, missing parameter, wrong
        // media type) carry their status. Without this they fell into the 500 branch below, so
        // a scanner probing for paths would page on-call and fire SYSTEM_ERROR (#185).
        if (ex is org.springframework.web.ErrorResponse && ex.statusCode.is4xxClientError) {
            val status = HttpStatus.valueOf(ex.statusCode.value())
            log.warn("Client error {} on request: {}", status.value(), ex.message)
            return respond(ex, status, status.reasonPhrase, ex.body.detail ?: status.reasonPhrase)
        }
        // ex.message used to go straight into the body, so SQL, class names and internal state
        // reached the client. It now stays in the log, under the correlation id.
        log.error("Unexpected error [correlationId={}]: {}", CorrelationIdFilter.current(), ex.message, ex)
        notifyOperators(TriggerEvent.SYSTEM_ERROR, "Unexpected server error")
        return respond(ex, HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
            "An unexpected error occurred. Quote the correlation id to an operator.")
    }
}
