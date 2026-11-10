package com.factstore.schema

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

/**
 * #195 — a central schema registry for fact payloads.
 *
 * A schema is published as `PUT /api/v1/commands/schemas/{name}/v{version}` and named by its URN,
 * `urn:schema:<name>:v<version>`, which a fact carries in `governance.schema_ref`. A published
 * version is immutable, and a new version must stay backwards compatible with the one before it.
 */
@SpringBootTest(properties = ["security.enforce-auth=true"])
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = ["SCOPE_admin"])
class SchemaRegistryTest {

    @Autowired lateinit var mockMvc: MockMvc

    private val name = "junit-results-${System.nanoTime()}"

    private val v1 = """
        {
          "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "required": ["tool", "passed"],
          "properties": {
            "tool": {"type": "string"},
            "passed": {"type": "integer"},
            "failed": {"type": "integer"}
          }
        }
    """.trimIndent()

    private val v2Compatible = v1.replace(
        "\"failed\": {\"type\": \"integer\"}",
        "\"failed\": {\"type\": \"integer\"},\n    \"skipped\": {\"type\": \"integer\"}"
    )

    private val v2Breaking = v1.replace(",\n    \"failed\": {\"type\": \"integer\"}", "")

    private fun publish(version: Int, schema: String) = mockMvc.put("/api/v1/commands/schemas/$name/v$version") {
        contentType = MediaType.APPLICATION_JSON
        content = schema
    }

    @Test
    fun `publishing a first version creates it under its URN`() {
        publish(1, v1).andExpect {
            status { isCreated() }
            jsonPath("$.ref") { value("urn:schema:$name:v1") }
            jsonPath("$.version") { value(1) }
        }
    }

    @Test
    fun `republishing the same version with the same content is idempotent`() {
        publish(1, v1).andExpect { status { isCreated() } }
        publish(1, v1).andExpect { status { isOk() } }
    }

    @Test
    fun `a published version cannot be changed`() {
        publish(1, v1).andExpect { status { isCreated() } }
        publish(1, v2Compatible).andExpect {
            status { isConflict() }
            jsonPath("$.message") { value(org.hamcrest.Matchers.containsString("immutable")) }
        }
    }

    @Test
    fun `a compatible next version is accepted`() {
        publish(1, v1).andExpect { status { isCreated() } }
        publish(2, v2Compatible).andExpect { status { isCreated() } }
    }

    @Test
    fun `a breaking next version is refused with the reason`() {
        publish(1, v1).andExpect { status { isCreated() } }
        publish(2, v2Breaking).andExpect {
            status { isConflict() }
            jsonPath("$.message") { value(org.hamcrest.Matchers.containsString("failed")) }
        }
    }

    @Test
    fun `versions are published in order`() {
        publish(2, v1).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a body that is not a JSON Schema is refused`() {
        publish(1, """{"type": 42}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a published schema can be read back by its reference`() {
        publish(1, v1).andExpect { status { isCreated() } }

        mockMvc.get("/api/v1/queries/schemas/$name/v1").andExpect {
            status { isOk() }
            jsonPath("$.ref") { value("urn:schema:$name:v1") }
            jsonPath("$.schema.required[0]") { value("tool") }
        }
        mockMvc.get("/api/v1/queries/schemas/$name").andExpect {
            status { isOk() }
            jsonPath("$[0].version") { value(1) }
        }
    }

    @Test
    @WithMockUser(authorities = ["SCOPE_policies:read", "SCOPE_policies:write"])
    fun `only an administrator may publish a schema`() {
        publish(1, v1).andExpect { status { isForbidden() } }
    }

    @Test
    @WithMockUser(authorities = ["SCOPE_policies:read"])
    fun `a policies reader may read schemas`() {
        mockMvc.get("/api/v1/queries/schemas/$name").andExpect { status { isOk() } }
    }
}
