package com.factstore.application.siem

import com.factstore.core.domain.AuditEvent
import com.factstore.core.domain.AuditEventType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant

/**
 * OCSF 1.3.0 events for the SIEM stream (#199). [OCSF](https://schema.ocsf.io) is a vendor-neutral
 * schema that Splunk, Datadog, AWS Security Lake and others ingest directly. Factstore uses two
 * classes:
 *
 * - **Authentication (3002)** for every credential presented (an API key, a workload token or a
 *   session) and for every interactive sign-in or sign-out;
 * - **API Activity (6003)** for every API call, with `activity_id` taken from the HTTP method
 *   (1 Create, 2 Read, 3 Update, 4 Delete), and for every audit record. A refusal (a 4xx or 5xx,
 *   a blocked gate, a denied authorisation) has `status_id` 2 (Failure) and a `status_detail`.
 *
 * Factstore-specific values go under `unmapped`, as OCSF requires.
 */
object Ocsf {
    const val VERSION = "1.3.0"

    private const val AUTHENTICATION = 3002
    private const val IAM = 3
    private const val API_ACTIVITY = 6003
    private const val APPLICATION = 6

    private const val SUCCESS = 1
    private const val FAILURE = 2
    private const val INFORMATIONAL = 1
    private const val MEDIUM = 3

    private val mapper = jacksonObjectMapper()

    fun authentication(
        time: Instant,
        success: Boolean,
        user: String?,
        authMethod: String,
        detail: String?,
        sourceIp: String?,
        correlationId: String?,
        logoff: Boolean = false,
        unmapped: Map<String, Any?>? = null
    ): Map<String, Any?> {
        val activity = if (logoff) 2 else 1
        return linkedMapOf<String, Any?>(
            "class_uid" to AUTHENTICATION,
            "class_name" to "Authentication",
            "category_uid" to IAM,
            "category_name" to "Identity & Access Management",
            "activity_id" to activity,
            "activity_name" to if (logoff) "Logoff" else "Logon",
            "type_uid" to AUTHENTICATION * 100 + activity,
            "time" to time.toEpochMilli(),
            "severity_id" to if (success) INFORMATIONAL else MEDIUM,
            "status_id" to if (success) SUCCESS else FAILURE,
            "status" to if (success) "Success" else "Failure",
            "status_detail" to detail,
            "user" to mapOf("name" to (user ?: "unknown")),
            "auth_protocol" to authMethod,
            "src_endpoint" to sourceIp?.let { mapOf("ip" to it) },
            "metadata" to metadata(correlationId),
            "unmapped" to unmapped
        ).withoutNulls()
    }

    fun apiActivity(
        time: Instant,
        method: String,
        path: String,
        statusCode: Int,
        actor: String?,
        authMethod: String?,
        detail: String?,
        sourceIp: String?,
        correlationId: String?,
        durationMs: Long
    ): Map<String, Any?> {
        val activity = activityFor(method)
        val success = statusCode < 400
        return linkedMapOf<String, Any?>(
            "class_uid" to API_ACTIVITY,
            "class_name" to "API Activity",
            "category_uid" to APPLICATION,
            "category_name" to "Application Activity",
            "activity_id" to activity,
            "activity_name" to activityName(activity),
            "type_uid" to API_ACTIVITY * 100 + activity,
            "time" to time.toEpochMilli(),
            "duration" to durationMs,
            "severity_id" to if (success) INFORMATIONAL else MEDIUM,
            "status_id" to if (success) SUCCESS else FAILURE,
            "status" to if (success) "Success" else "Failure",
            "status_detail" to (detail ?: if (success) null else "HTTP $statusCode"),
            "actor" to mapOf("user" to mapOf("name" to (actor ?: "anonymous"))),
            "api" to mapOf("operation" to "$method $path", "service" to mapOf("name" to "OpenFactstore")),
            "http_request" to mapOf("http_method" to method, "url" to mapOf("path" to path)),
            "http_response" to mapOf("code" to statusCode),
            "src_endpoint" to sourceIp?.let { mapOf("ip" to it) },
            "metadata" to metadata(correlationId),
            "unmapped" to authMethod?.let { mapOf("factstore_auth_method" to it) }
        ).withoutNulls()
    }

    /** An audit record (`audit_events`), with its Factstore type and payload under `unmapped`. */
    fun auditRecord(event: AuditEvent, correlationId: String?): Map<String, Any?> {
        val unmapped = mapOf(
            "factstore_event_type" to event.eventType.name,
            "factstore_audit_id" to event.id.toString(),
            "factstore_payload" to runCatching { mapper.readTree(event.payload) }.getOrNull()
        )
        val type = event.eventType
        if (type == AuditEventType.USER_SIGNED_IN || type in SIGN_IN_FAILURES || type == AuditEventType.USER_SIGNED_OUT) {
            return authentication(
                time = event.occurredAt,
                success = type !in SIGN_IN_FAILURES,
                user = event.actor,
                authMethod = "interactive",
                detail = if (type in SIGN_IN_FAILURES) type.name else null,
                sourceIp = null,
                correlationId = correlationId,
                logoff = type == AuditEventType.USER_SIGNED_OUT,
                unmapped = unmapped
            )
        }
        val failed = type in REFUSALS
        val activity = when {
            failed -> 99
            CREATE_SUFFIXES.any { type.name.endsWith(it) } -> 1
            DELETE_SUFFIXES.any { type.name.endsWith(it) } -> 4
            type == AuditEventType.CROSS_TENANT_READ -> 2
            else -> 3
        }
        return linkedMapOf<String, Any?>(
            "class_uid" to API_ACTIVITY,
            "class_name" to "API Activity",
            "category_uid" to APPLICATION,
            "category_name" to "Application Activity",
            "activity_id" to activity,
            "activity_name" to activityName(activity),
            "type_uid" to API_ACTIVITY * 100 + activity,
            "time" to event.occurredAt.toEpochMilli(),
            "severity_id" to if (failed) MEDIUM else INFORMATIONAL,
            "status_id" to if (failed) FAILURE else SUCCESS,
            "status" to if (failed) "Failure" else "Success",
            "status_detail" to if (failed) type.name else null,
            "actor" to mapOf("user" to mapOf("name" to event.actor)),
            "api" to mapOf("operation" to type.name, "service" to mapOf("name" to "OpenFactstore")),
            "metadata" to metadata(correlationId),
            "unmapped" to unmapped
        ).withoutNulls()
    }

    private val SIGN_IN_FAILURES = setOf(AuditEventType.USER_SIGN_IN_FAILED, AuditEventType.AUTH_FAILED)

    /** Audit records that are a policy rejection: something was refused. */
    private val REFUSALS = setOf(AuditEventType.AUTH_DENIED, AuditEventType.GATE_BLOCKED, AuditEventType.APPROVAL_REJECTED)

    private val CREATE_SUFFIXES = listOf("CREATED", "RECORDED", "PUBLISHED", "REGISTERED", "DEPLOYED", "GRANTED", "ALLOWED", "EVALUATED")
    private val DELETE_SUFFIXES = listOf("DELETED", "REVOKED", "REMOVED")

    private fun activityFor(method: String): Int = when (method.uppercase()) {
        "POST" -> 1
        "GET", "HEAD" -> 2
        "PUT", "PATCH" -> 3
        "DELETE" -> 4
        else -> 99
    }

    private fun activityName(activity: Int) = when (activity) {
        1 -> "Create"
        2 -> "Read"
        3 -> "Update"
        4 -> "Delete"
        else -> "Other"
    }

    private fun metadata(correlationId: String?): Map<String, Any?> = linkedMapOf<String, Any?>(
        "version" to VERSION,
        "product" to mapOf("name" to "OpenFactstore", "vendor_name" to "OpenFactstore"),
        "log_name" to "factstore.audit",
        "correlation_uid" to correlationId
    ).withoutNulls()

    private fun Map<String, Any?>.withoutNulls(): Map<String, Any?> = filterValues { it != null }
}
