package com.factstore.application.siem

import com.factstore.core.domain.AuditEvent
import com.factstore.core.domain.AuditEventType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * #199 — the OCSF shapes Factstore streams to a SIEM. Field names and ids are the contract a
 * Splunk or Datadog pipeline is built on, so they are pinned here.
 */
class OcsfMappingTest {

    private val mapper = jacksonObjectMapper()
    private val at = Instant.parse("2026-11-12T08:00:00Z")

    private fun json(event: Map<String, Any?>) = mapper.readTree(mapper.writeValueAsString(event))

    @Test
    fun `#199 a successful API-key authentication is OCSF Authentication 3002`() {
        val event = json(
            Ocsf.authentication(
                time = at, success = true, user = "api-key:owner-1", authMethod = "api_key",
                detail = null, sourceIp = "10.0.0.5", correlationId = "req-1"
            )
        )
        assertThat(event["class_uid"].asInt()).isEqualTo(3002)
        assertThat(event["category_uid"].asInt()).isEqualTo(3)
        assertThat(event["activity_id"].asInt()).isEqualTo(1)
        assertThat(event["type_uid"].asInt()).isEqualTo(300201)
        assertThat(event["status_id"].asInt()).isEqualTo(1)
        assertThat(event["status"].asText()).isEqualTo("Success")
        assertThat(event["time"].asLong()).isEqualTo(at.toEpochMilli())
        assertThat(event["user"]["name"].asText()).isEqualTo("api-key:owner-1")
        assertThat(event["auth_protocol"].asText()).isEqualTo("api_key")
        assertThat(event["src_endpoint"]["ip"].asText()).isEqualTo("10.0.0.5")
        assertThat(event["metadata"]["product"]["name"].asText()).isEqualTo("OpenFactstore")
        assertThat(event["metadata"]["version"].asText()).isEqualTo("1.3.0")
        assertThat(event["metadata"]["correlation_uid"].asText()).isEqualTo("req-1")
    }

    @Test
    fun `#199 a failed authentication says why and is more severe`() {
        val event = json(Ocsf.authentication(at, false, null, "api_key", "REVOKED", "10.0.0.5", "req-2"))
        assertThat(event["status_id"].asInt()).isEqualTo(2)
        assertThat(event["status"].asText()).isEqualTo("Failure")
        assertThat(event["status_detail"].asText()).isEqualTo("REVOKED")
        assertThat(event["severity_id"].asInt()).isGreaterThan(1)
    }

    @Test
    fun `#199 API calls are OCSF API Activity 6003, with the activity from the HTTP method`() {
        fun activity(method: String) = json(
            Ocsf.apiActivity(at, method, "/api/v1/flows", 200, "alice@example.com", "session", null, "10.0.0.1", "req", 12)
        )["activity_id"].asInt()
        assertThat(activity("POST")).isEqualTo(1)
        assertThat(activity("GET")).isEqualTo(2)
        assertThat(activity("PUT")).isEqualTo(3)
        assertThat(activity("PATCH")).isEqualTo(3)
        assertThat(activity("DELETE")).isEqualTo(4)
        assertThat(activity("OPTIONS")).isEqualTo(99)

        val event = json(Ocsf.apiActivity(at, "PUT", "/api/v1/commands/schemas/x/v1", 403, "api-key:o", "api_key", "Forbidden", "10.0.0.1", "req", 5))
        assertThat(event["class_uid"].asInt()).isEqualTo(6003)
        assertThat(event["category_uid"].asInt()).isEqualTo(6)
        assertThat(event["type_uid"].asInt()).isEqualTo(600303)
        assertThat(event["status_id"].asInt()).isEqualTo(2)
        assertThat(event["status_detail"].asText()).isEqualTo("Forbidden")
        assertThat(event["http_request"]["http_method"].asText()).isEqualTo("PUT")
        assertThat(event["http_request"]["url"]["path"].asText()).isEqualTo("/api/v1/commands/schemas/x/v1")
        assertThat(event["http_response"]["code"].asInt()).isEqualTo(403)
        assertThat(event["actor"]["user"]["name"].asText()).isEqualTo("api-key:o")
        assertThat(event["duration"].asLong()).isEqualTo(5)
    }

    @Test
    fun `#199 audit records keep their own type, and sign-ins are authentications`() {
        val signIn = AuditEvent(eventType = AuditEventType.USER_SIGNED_IN, actor = "alice@example.com", payload = """{"method":"sso"}""", occurredAt = at)
        val failed = AuditEvent(eventType = AuditEventType.USER_SIGN_IN_FAILED, actor = "bob@example.com", payload = "{}", occurredAt = at)
        val blocked = AuditEvent(eventType = AuditEventType.GATE_BLOCKED, actor = "api-key:o", payload = """{"reason":"missing approval"}""", occurredAt = at)
        val published = AuditEvent(eventType = AuditEventType.SCHEMA_PUBLISHED, actor = "admin", payload = "{}", occurredAt = at)

        assertThat(json(Ocsf.auditRecord(signIn, "req"))["class_uid"].asInt()).isEqualTo(3002)
        assertThat(json(Ocsf.auditRecord(failed, "req"))["status_id"].asInt()).isEqualTo(2)
        json(Ocsf.auditRecord(blocked, "req")).let {
            assertThat(it["class_uid"].asInt()).isEqualTo(6003)
            assertThat(it["status_id"].asInt()).isEqualTo(2)
            assertThat(it["unmapped"]["factstore_event_type"].asText()).isEqualTo("GATE_BLOCKED")
            assertThat(it["unmapped"]["factstore_payload"]["reason"].asText()).isEqualTo("missing approval")
        }
        json(Ocsf.auditRecord(published, "req")).let {
            assertThat(it["activity_id"].asInt()).isEqualTo(1)
            assertThat(it["status_id"].asInt()).isEqualTo(1)
        }
    }
}
