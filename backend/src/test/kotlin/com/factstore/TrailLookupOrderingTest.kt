package com.factstore

import com.factstore.application.FlowService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * #220 — "the most recent trail for a commit" must not depend on the clock ticking between two
 * runs. With the clock frozen, two runs for one commit are created in the same instant; the later
 * one must still be found, and creation times must still be strictly ordered.
 */
@SpringBootTest
@Transactional
@Import(TrailLookupOrderingTest.FrozenClock::class)
class TrailLookupOrderingTest {

    @TestConfiguration
    class FrozenClock {
        @Bean
        @Primary
        fun frozenClock(): Clock = Clock.fixed(Instant.parse("2026-11-12T09:00:00Z"), ZoneOffset.UTC)
    }

    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: ITrailService

    private fun request(flowId: UUID, sha: String) = CreateTrailRequest(
        flowId = flowId, gitCommitSha = sha, gitBranch = "main", gitAuthor = "a", gitAuthorEmail = "a@example.com"
    )

    @Test
    fun `#220 the later of two runs created in the same instant is the most recent`() {
        val flow = flowService.createFlow(CreateFlowRequest("flow-${System.nanoTime()}", "desc", listOf("junit")))
        val sha = "sha-${System.nanoTime()}"
        val first = trailService.createTrail(request(flow.id, sha))
        val second = trailService.createTrail(request(flow.id, sha))

        assertTrue(second.createdAt.isAfter(first.createdAt), "creation times must be strictly ordered")
        assertEquals(second.id, trailService.lookupTrail(flow.id, externalId = null, name = null, gitCommitSha = sha).id)
    }
}
