package com.factstore.tools

import com.factstore.core.domain.AuditEventType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * #197 / #200 — audit event types are an open set: each new capability adds one (#200 added
 * DATA_KEY_SHRED_REQUESTED and DATA_KEY_SHREDDED). Published as a closed `enum`, every addition
 * is a breaking change to the API contract (`oasdiff breaking`, response-property-enum-value-added),
 * so the contract declares them `x-extensible-enum` instead and clients must tolerate new values.
 */
@SpringBootTest(properties = ["security.enforce-auth=false", "springdoc.api-docs.path=/api-docs"])
@AutoConfigureMockMvc
class ExtensibleEnumContractTest {

    @Autowired lateinit var mockMvc: MockMvc

    private val auditTypes = AuditEventType.entries.map { it.name }.toSet()

    private fun schemas(node: JsonNode): Sequence<JsonNode> = sequence {
        if (node.isObject) {
            yield(node)
            node.fields().forEach { (_, child) -> yieldAll(schemas(child)) }
        } else if (node.isArray) {
            node.forEach { yieldAll(schemas(it)) }
        }
    }

    @Test
    fun `audit event types are published as an extensible enum, never a closed one`() {
        val spec = jacksonObjectMapper().readTree(mockMvc.get("/api-docs").andReturn().response.contentAsString)
        val all = schemas(spec).toList()

        val closed = all.filter { it.has("enum") && it["enum"].map { v -> v.asText() }.toSet() == auditTypes }
        val extensible = all.filter { it.has("x-extensible-enum") && it["x-extensible-enum"].map { v -> v.asText() }.toSet() == auditTypes }

        assertThat(closed).describedAs("AuditEventType published as a closed enum").isEmpty()
        assertThat(extensible).describedAs("AuditEventType published as x-extensible-enum").isNotEmpty()
    }
}
