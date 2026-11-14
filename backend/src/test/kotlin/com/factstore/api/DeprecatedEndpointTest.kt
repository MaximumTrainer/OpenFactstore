package com.factstore.api

import com.factstore.adapter.inbound.web.DeprecatedEndpoint
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * #250 — superseded endpoints are deprecated, not silently kept. A deprecated endpoint still works,
 * but every response tells the caller so: `Deprecation` (RFC 9745), `Sunset` (RFC 8594) and a
 * `Link` to the endpoint that replaces it. The OpenAPI document marks the operation `deprecated`.
 */
@SpringBootTest(properties = ["security.enforce-auth=false", "springdoc.api-docs.path=/api-docs"])
@AutoConfigureMockMvc
@WithMockUser(authorities = ["SCOPE_admin"])
class DeprecatedEndpointTest {

    @Autowired lateinit var mockMvc: MockMvc

    private val mapper = jacksonObjectMapper()

    data class Route(val method: String, val path: String, val deprecation: DeprecatedEndpoint?)

    /** Every handler method of every controller, with its `@DeprecatedEndpoint`, if any. */
    private fun routes(): List<Route> {
        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AnnotationTypeFilter(RestController::class.java))
        return scanner.findCandidateComponents("com.factstore").flatMap { candidate ->
            val type = Class.forName(candidate.beanClassName)
            val prefix = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping::class.java)?.path?.firstOrNull().orEmpty()
            type.declaredMethods.flatMap { method ->
                val mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping::class.java) ?: return@flatMap emptyList()
                val deprecation = method.getAnnotation(DeprecatedEndpoint::class.java)
                val paths = mapping.path.ifEmpty { arrayOf("") }
                mapping.method.flatMap { m -> paths.map { Route(m.name, prefix + it, deprecation) } }
            }
        }
    }

    private val deprecatedRoutes get() = routes().filter { it.deprecation != null }

    @Test
    fun `#250 every v1 endpoint with a v2 twin is deprecated`() {
        // The 22 v1 endpoints with a v2 twin of the same method and path (decision on #250).
        assertThat(deprecatedRoutes.map { "${it.method} ${it.path}" }).containsExactlyInAnyOrder(
            "GET /api/v1/artifacts",
            "GET /api/v1/artifacts/{sha256}/provenance",
            "GET /api/v1/flows",
            "POST /api/v1/flows",
            "GET /api/v1/flows/{flowId}/trails",
            "DELETE /api/v1/flows/{id}",
            "GET /api/v1/flows/{id}",
            "PUT /api/v1/flows/{id}",
            "GET /api/v1/flows/{id}/template",
            "GET /api/v1/trails",
            "POST /api/v1/trails",
            "GET /api/v1/trails/{id}",
            "GET /api/v1/trails/{id}/audit",
            "GET /api/v1/trails/{trailId}/artifacts",
            "POST /api/v1/trails/{trailId}/artifacts",
            "GET /api/v1/trails/{trailId}/artifacts/{artifactId}/provenance",
            "POST /api/v1/trails/{trailId}/artifacts/{artifactId}/provenance",
            "POST /api/v1/trails/{trailId}/artifacts/{artifactId}/provenance/verify",
            "GET /api/v1/trails/{trailId}/attestations",
            "POST /api/v1/trails/{trailId}/attestations",
            "POST /api/v1/trails/{trailId}/attestations/pull-request",
            "POST /api/v1/trails/{trailId}/attestations/{attestationId}/evidence",
        )
    }

    @Test
    fun `#250 every deprecation names a successor that exists, and a sunset after it was deprecated`() {
        val all = routes().map { "${it.method} ${it.path}" }.toSet()
        deprecatedRoutes.forEach { route ->
            val d = route.deprecation!!
            assertThat(all).describedAs("successor of ${route.method} ${route.path}").contains("${route.method} ${d.successor}")
            assertThat(LocalDate.parse(d.sunset)).isAfter(LocalDate.parse(d.since))
        }
    }

    @Test
    fun `#250 a deprecated endpoint answers with Deprecation, Sunset and successor Link headers`() {
        val id = UUID.randomUUID()
        val route = deprecatedRoutes.first { it.path == "/api/v1/trails/{id}/audit" }.deprecation!!
        val since = LocalDate.parse(route.since).atStartOfDay(ZoneOffset.UTC).toEpochSecond()

        mockMvc.get("/api/v1/trails/$id/audit").andExpect {
            header { string("Deprecation", "@$since") }
            header { string("Sunset", "Fri, 14 May 2027 00:00:00 GMT") }
            header { string("Link", "</api/v2/trails/$id/audit>; rel=\"successor-version\"") }
        }
    }

    @Test
    fun `#250 the headers are sent even when the deprecated call fails`() {
        val trail = UUID.randomUUID()
        val artifact = UUID.randomUUID()
        mockMvc.post("/api/v1/trails/$trail/artifacts/$artifact/provenance/verify") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
            header { exists("Deprecation") }
            header { exists("Sunset") }
            header { string("Link", "</api/v2/trails/$trail/artifacts/$artifact/provenance/verify>; rel=\"successor-version\"") }
        }
    }

    @Test
    fun `#250 the successor and other endpoints carry no deprecation headers`() {
        mockMvc.get("/api/v2/trails/${UUID.randomUUID()}/audit").andExpect {
            header { doesNotExist("Deprecation") }
            header { doesNotExist("Sunset") }
        }
        mockMvc.get("/api/v1/audit").andExpect {
            header { doesNotExist("Deprecation") }
        }
    }

    @Test
    fun `#250 the OpenAPI document marks exactly the deprecated operations deprecated`() {
        val api = mapper.readTree(mockMvc.get("/api-docs").andReturn().response.contentAsString)
        val marked = api["paths"].fields().asSequence().flatMap { (path, item) ->
            item.fields().asSequence()
                .filter { (_, op) -> op["deprecated"]?.asBoolean() == true }
                .map { (method, _) -> "${method.uppercase()} $path" }
        }.toSet()

        assertThat(marked).isEqualTo(deprecatedRoutes.map { "${it.method} ${it.path}" }.toSet())
        val audit = api["paths"]["/api/v1/trails/{id}/audit"]["get"]
        assertThat(audit["description"].asText()).contains("/api/v2/trails/{id}/audit").contains("2027-05-14")
    }
}
