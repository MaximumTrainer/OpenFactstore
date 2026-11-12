package com.factstore.tools

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.io.File

/**
 * #197 — the API is specified by a committed OpenAPI document, `docs/openapi.json`. This test fails
 * when the served API and the committed document differ, so every API change shows up in review as
 * a diff to that file. CI then runs `oasdiff breaking` on it against the base branch.
 *
 * Regenerate after an intended change with `./gradlew test --tests '*OpenApiSnapshotTest' -Dfactstore.update-openapi=true`.
 */
// The test configuration replaces application.yml, so the served path is set as in production.
@SpringBootTest(properties = ["security.enforce-auth=false", "springdoc.api-docs.path=/api-docs"])
@AutoConfigureMockMvc
class OpenApiSnapshotTest {

    @Autowired lateinit var mockMvc: MockMvc

    private val mapper = jacksonObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

    @Test
    fun `#197 the committed OpenAPI document matches the served API`() {
        val served = mapper.readTree(mockMvc.get("/api-docs").andReturn().response.contentAsString) as ObjectNode
        // The server URL depends on where the test runs, not on the API.
        served.remove("servers")
        // Sorted keys and LF line endings, so the file is the same on every machine.
        val text = mapper.writeValueAsString(mapper.treeToValue(served, Any::class.java)).replace("\r\n", "\n") + "\n"

        val doc = File("../docs/openapi.json")
        if (System.getProperty("factstore.update-openapi") == "true") doc.writeText(text)

        assertThat(doc).describedAs("docs/openapi.json is missing; regenerate it (see this test's KDoc)").exists()
        assertThat(doc.readText().replace("\r\n", "\n"))
            .describedAs("docs/openapi.json is stale; regenerate it (see this test's KDoc)")
            .isEqualTo(text)
    }
}
