package com.factstore.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * #189 — static keys can be retired for ingest. With `factstore.ingest.require-workload-identity`
 * on, a static credential is refused at the fact endpoint even when it holds the scope; only a
 * verified workload identity (see [WorkloadIdentityTest]) may ingest.
 */
@SpringBootTest(properties = ["security.enforce-auth=true", "factstore.ingest.require-workload-identity=true"])
@AutoConfigureMockMvc
@Transactional
class IngestRequiresWorkloadIdentityTest {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    @WithMockUser(username = "key-owner", authorities = ["SCOPE_attestations:write", "ROLE_API_USER"])
    fun `#189 a static API key cannot ingest facts once workload identity is required`() {
        mockMvc.post("/api/v1/commands/facts") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"fact_id":"${UUID.randomUUID()}","subject":{"sha256":"${"d".repeat(64)}"},
                 "governance":{"schema_ref":"urn:schema:anything:v1"},"tool":{}}
            """.trimIndent()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.message") { value(org.hamcrest.Matchers.containsString("workload identity")) }
        }
    }
}
