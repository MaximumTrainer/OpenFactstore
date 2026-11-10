package com.factstore.benchmark

import com.factstore.application.ApiKeyRateLimiter
import com.factstore.application.ApiKeyService
import com.factstore.application.ApiKeyValidationCache
import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.User
import com.factstore.core.domain.security.Permission
import com.factstore.core.port.inbound.IFlowService
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.CreateApiKeyRequest
import com.factstore.dto.CreateFlowRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.io.File
import java.util.UUID

/**
 * #208 (#155 FR-9.3, acceptance criterion 13) — what authenticating a request costs.
 *
 * Measures `GET /api/v1/flows/{id}` with a valid API key (warm validation cache, the CI-pipeline
 * steady state) against the same request made anonymously, interleaved so both see the same JVM
 * conditions. The difference is the authentication overhead: the key lookup, the cache hit, the
 * security context and the authorisation rule.
 *
 * Not part of `./gradlew test`: run `./gradlew authBenchmark`. It writes
 * `build/reports/auth-benchmark.json` and never fails on the budget; CI reads that file and warns.
 * The budget is documented in `docs/api-authorisation.md`.
 */
@Tag("benchmark")
@SpringBootTest(properties = ["security.enforce-auth=false"])
@AutoConfigureMockMvc
class AuthOverheadBenchmark {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var apiKeyService: ApiKeyService
    @Autowired lateinit var users: IUserRepository
    @Autowired lateinit var flows: IFlowService
    @Autowired lateinit var rateLimiter: ApiKeyRateLimiter
    @Autowired lateinit var validationCache: ApiKeyValidationCache

    companion object {
        const val WARMUP = 1_000
        const val MEASURED = 5_000
        /** Overhead budget per request, in milliseconds. */
        const val BUDGET_P50_MS = 1.0
        const val BUDGET_P99_MS = 5.0
    }

    private fun <T> asAdmin(block: () -> T): T {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "admin", null, listOf(SimpleGrantedAuthority(Permission.ADMIN.authority))
        )
        return try { block() } finally {
            SecurityContextHolder.clearContext(); TestSecurityContextHolder.clearContext()
        }
    }

    private fun percentile(sortedNanos: LongArray, p: Double): Double =
        sortedNanos[((sortedNanos.size - 1) * p).toInt()] / 1_000_000.0

    @Test
    fun `measure authentication overhead per request`() {
        rateLimiter.reset(); validationCache.clear()
        val flowId = asAdmin { flows.createFlow(CreateFlowRequest(name = "bench-${UUID.randomUUID()}")).id }
        val key = asAdmin {
            val owner = users.save(User(email = "bench-${System.nanoTime()}@example.com", name = "Bench"))
            apiKeyService.createApiKey(
                CreateApiKeyRequest(ownerId = owner.id, label = "bench", ownerType = OwnerType.USER, scopes = listOf("flows:read"))
            ).plainTextKey
        }
        val path = "/api/v1/flows/$flowId"

        fun anonymous(): Long {
            val start = System.nanoTime()
            val status = mockMvc.get(path).andReturn().response.status
            val elapsed = System.nanoTime() - start
            check(status == 200) { "anonymous request returned $status" }
            return elapsed
        }
        fun authenticated(): Long {
            val start = System.nanoTime()
            val status = mockMvc.get(path) { header("Authorization", "Bearer $key") }.andReturn().response.status
            val elapsed = System.nanoTime() - start
            check(status == 200) { "authenticated request returned $status" }
            return elapsed
        }

        repeat(WARMUP) { anonymous(); authenticated() }
        val anon = LongArray(MEASURED)
        val auth = LongArray(MEASURED)
        for (i in 0 until MEASURED) {
            anon[i] = anonymous()
            auth[i] = authenticated()
        }
        anon.sort(); auth.sort()

        val overheadP50 = percentile(auth, 0.50) - percentile(anon, 0.50)
        val overheadP99 = percentile(auth, 0.99) - percentile(anon, 0.99)
        val within = overheadP50 <= BUDGET_P50_MS && overheadP99 <= BUDGET_P99_MS
        val report = """
            {
              "requests": $MEASURED,
              "anonymousMs": {"p50": ${"%.3f".format(percentile(anon, 0.50))}, "p99": ${"%.3f".format(percentile(anon, 0.99))}},
              "authenticatedMs": {"p50": ${"%.3f".format(percentile(auth, 0.50))}, "p99": ${"%.3f".format(percentile(auth, 0.99))}},
              "overheadMs": {"p50": ${"%.3f".format(overheadP50)}, "p99": ${"%.3f".format(overheadP99)}},
              "budgetMs": {"p50": $BUDGET_P50_MS, "p99": $BUDGET_P99_MS},
              "withinBudget": $within
            }
        """.trimIndent()

        val out = File(System.getProperty("factstore.benchmark.out", "build/reports/auth-benchmark.json"))
        out.parentFile.mkdirs()
        out.writeText(report)
        println("AUTH BENCHMARK\n$report")

        assertThat(out).exists()
    }
}
