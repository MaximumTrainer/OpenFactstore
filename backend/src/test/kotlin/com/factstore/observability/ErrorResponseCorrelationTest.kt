package com.factstore.observability

import com.factstore.exception.IntegrityException
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * #185 — error responses and correlation.
 *
 * A 5xx must tell the client *that* something broke and give it a handle to quote to an operator
 * (the correlation id), but never *what* broke: exception text, class names and SQL belong in the
 * log, where the same correlation id finds them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ErrorResponseCorrelationTest.FailingEndpoints::class)
@ExtendWith(OutputCaptureExtension::class)
class ErrorResponseCorrelationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var meterRegistry: MeterRegistry

    @TestConfiguration
    class FailingEndpoints {
        @RestController
        class Boom {
            @GetMapping("/test-only/boom")
            fun boom(): String = throw IllegalStateException("SELECT secret FROM internal_table failed")

            @GetMapping("/test-only/integrity")
            fun integrity(): String = throw IntegrityException("hash mismatch at seq=42 table=domain_events")

            @GetMapping("/test-only/ok")
            fun ok(): String = "ok"
        }
    }

    @Test
    fun `a request without X-Request-Id is given a generated correlation id`() {
        val result = mockMvc.get("/test-only/ok").andReturn()

        val id = result.response.getHeader("X-Request-Id")
        assertThat(id).isNotBlank()
    }

    @Test
    fun `a valid incoming X-Request-Id is reused as the correlation id`() {
        val result = mockMvc.get("/test-only/ok") { header("X-Request-Id", "pipeline-run-1234.attempt-1") }.andReturn()

        assertThat(result.response.getHeader("X-Request-Id")).isEqualTo("pipeline-run-1234.attempt-1")
    }

    @Test
    fun `an invalid incoming X-Request-Id is replaced rather than echoed into logs`() {
        val forged = "abc\r\nlevel=ERROR forged log line"
        val result = mockMvc.get("/test-only/ok") { header("X-Request-Id", forged) }.andReturn()

        val id = result.response.getHeader("X-Request-Id")
        assertThat(id).isNotBlank().isNotEqualTo(forged).doesNotContain("\n")
    }

    @Test
    fun `a 500 hides the exception text and carries the correlation id that is also logged`(output: CapturedOutput) {
        val result = mockMvc.get("/test-only/boom") { header("X-Request-Id", "corr-500-test") }.andReturn()

        assertThat(result.response.status).isEqualTo(500)
        val body = result.response.contentAsString
        assertThat(body).contains("\"correlationId\":\"corr-500-test\"")
        assertThat(body).doesNotContain("SELECT").doesNotContain("internal_table")
            .doesNotContain("IllegalStateException").doesNotContain("at com.")
        assertThat(output.out).contains("corr-500-test").contains("SELECT secret FROM internal_table failed")
    }

    @Test
    fun `an integrity failure hides its detail and carries the correlation id`(output: CapturedOutput) {
        val result = mockMvc.get("/test-only/integrity") { header("X-Request-Id", "corr-integrity") }.andReturn()

        assertThat(result.response.status).isEqualTo(500)
        val body = result.response.contentAsString
        assertThat(body).contains("\"correlationId\":\"corr-integrity\"")
        assertThat(body).doesNotContain("hash mismatch").doesNotContain("domain_events")
        assertThat(output.out).contains("corr-integrity").contains("hash mismatch at seq=42")
    }

    @Test
    fun `each handled error is counted by bounded type and status`() {
        fun count(type: String, status: String) =
            meterRegistry.find("factstore.errors").tags("type", type, "status", status).counter()?.count() ?: 0.0

        val unexpectedBefore = count("Unexpected", "500")
        val integrityBefore = count("IntegrityException", "500")
        val notFoundBefore = count("NotFoundException", "404")

        mockMvc.get("/test-only/boom")
        mockMvc.get("/test-only/integrity")
        mockMvc.get("/api/v1/flows/00000000-0000-0000-0000-000000000000")

        assertThat(count("Unexpected", "500")).isEqualTo(unexpectedBefore + 1)
        assertThat(count("IntegrityException", "500")).isEqualTo(integrityBefore + 1)
        assertThat(count("NotFoundException", "404")).isEqualTo(notFoundBefore + 1)
        // The arbitrary exception class must not leak into a tag value: that is unbounded cardinality.
        assertThat(meterRegistry.find("factstore.errors").tags("type", "IllegalStateException").counter()).isNull()
    }

    @Test
    fun `a framework-level client error keeps its own status`() {
        fun unexpected() = meterRegistry.find("factstore.errors").tags("type", "Unexpected", "status", "500").counter()?.count() ?: 0.0
        val before = unexpected()

        val notFound = mockMvc.get("/api/v1/no-such-route").andReturn()
        val wrongMethod = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/auth/config")
        ).andReturn()

        assertThat(notFound.response.status).isEqualTo(404)
        assertThat(wrongMethod.response.status).isEqualTo(405)
        assertThat(notFound.response.contentAsString).contains("correlationId")
        assertThat(unexpected()).isEqualTo(before)
    }

    @Test
    fun `an integrity failure is counted as an integrity failure from the api`() {
        fun count() = meterRegistry.find("factstore.integrity.failures").tags("source", "api").counter()?.count() ?: 0.0
        val before = count()

        mockMvc.get("/test-only/integrity")

        assertThat(count()).isEqualTo(before + 1)
    }
}
