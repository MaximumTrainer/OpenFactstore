package com.factstore.security

import com.factstore.adapter.inbound.web.PublicEndpoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.io.File

/**
 * #155 FR-4.1 and FR-4.3 — every endpoint states who may call it.
 *
 * A handler with neither `@PreAuthorize` (on the method or its controller) nor an explicit
 * [PublicEndpoint] fails the build. Silently-open endpoints are the defect class #155 exists to
 * remove, and a check against a hand-kept allowlist drifts; this one has no allowlist.
 *
 * The full endpoint-to-authorisation table lives in `docs/api-authorisation-matrix.md`, and this
 * test fails when it no longer matches the code. Regenerate it with
 * `./gradlew test --tests "*EveryEndpointIsAuthorisedTest*" -Dfactstore.update-matrix=true`.
 */
@SpringBootTest
class EveryEndpointIsAuthorisedTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    lateinit var handlerMapping: RequestMappingHandlerMapping

    private data class Row(val verb: String, val path: String, val rule: String, val handler: String)

    private fun rules(): List<Row> = handlerMapping.handlerMethods.entries
        .filter { (_, h) -> h.beanType.packageName.startsWith("com.factstore") }
        .flatMap { (info, h) ->
            val verbs = info.methodsCondition.methods.map { it.name }.ifEmpty { listOf("ANY") }
            val paths = info.pathPatternsCondition?.patternValues ?: info.patternValues
            verbs.flatMap { v -> paths.map { p -> Row(v, p, ruleFor(h), "${h.beanType.simpleName}#${h.method.name}") } }
        }
        .sortedWith(compareBy({ it.path }, { it.verb }))

    private fun ruleFor(h: HandlerMethod): String {
        AnnotatedElementUtils.findMergedAnnotation(h.method, PublicEndpoint::class.java)?.let { return "public: ${it.reason}" }
        val pre = AnnotatedElementUtils.findMergedAnnotation(h.method, PreAuthorize::class.java)
            ?: AnnotatedElementUtils.findMergedAnnotation(h.beanType, PreAuthorize::class.java)
        return pre?.value ?: "NONE"
    }

    @Test
    fun `every endpoint carries an explicit authorisation rule`() {
        val unannotated = rules().filter { it.rule == "NONE" }.map { "${it.verb} ${it.path} (${it.handler})" }

        assertThat(unannotated).describedAs("endpoints with no @PreAuthorize or @PublicEndpoint").isEmpty()
    }

    @Test
    fun `no mutating endpoint is open to any authenticated caller by accident`() {
        // Reads may be open to any signed-in principal; a write must name the scope it needs,
        // unless it only acts on the caller's own session or notifications.
        val selfService = setOf(
            "AuthController#refresh", "AuthController#revokeMySession", "AuthController#switchOrganisation",
            "NotificationController#markAsRead", "NotificationController#markAllAsRead",
            // POSTs that compute and return, changing nothing.
            "SearchController#globalSearch", "LedgerController#verifyFact", "LedgerController#verifyChain",
            "HubController#compose"
        )
        val loose = rules().filter {
            it.verb != "GET" && it.rule in setOf("isAuthenticated()", "@authz.authenticated()") && it.handler !in selfService
        }.map { "${it.verb} ${it.path} (${it.handler})" }

        assertThat(loose).isEmpty()
    }

    @Test
    fun `the documented matrix matches the annotations`() {
        val doc = File("../docs/api-authorisation-matrix.md")
        val table = buildString {
            appendLine("| Method | Path | Rule |")
            appendLine("|---|---|---|")
            rules().forEach { appendLine("| `${it.verb}` | `${it.path}` | `${it.rule}` |") }
        }
        val begin = "<!-- matrix:begin -->"
        val end = "<!-- matrix:end -->"
        if (System.getProperty("factstore.update-matrix") == "true") {
            val text = doc.readText()
            doc.writeText(text.substringBefore(begin) + begin + "\n" + table + end + text.substringAfter(end))
        }

        // Line endings are normalised: Windows checkouts get CRLF via core.autocrlf.
        val documented = doc.readText().replace("\r\n", "\n").substringAfter(begin).substringBefore(end).trim()
        assertThat(documented)
            .describedAs("docs/api-authorisation-matrix.md is stale; regenerate it (see this test's KDoc)")
            .isEqualTo(table.trim())
    }
}
