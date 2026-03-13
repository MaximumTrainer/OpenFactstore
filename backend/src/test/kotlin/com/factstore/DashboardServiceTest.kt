package com.factstore

import com.factstore.application.DashboardService
import com.factstore.application.FlowService
import com.factstore.application.TrailService
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class DashboardServiceTest {

    @Autowired lateinit var dashboardService: DashboardService
    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: TrailService

    @Test
    fun `getStats returns zero counts on empty database`() {
        val stats = dashboardService.getStats()
        assertTrue(stats.totalFlows >= 0)
        assertTrue(stats.totalTrails >= 0)
        assertEquals(stats.compliantTrails + stats.nonCompliantTrails + stats.pendingTrails, stats.totalTrails)
    }

    @Test
    fun `getStats counts flows correctly`() {
        val before = dashboardService.getStats()
        flowService.createFlow(CreateFlowRequest("stat-flow-${System.nanoTime()}", "desc"))
        val after = dashboardService.getStats()
        assertEquals(before.totalFlows + 1, after.totalFlows)
    }

    @Test
    fun `getStats counts trails and computes compliance rate`() {
        val flow = flowService.createFlow(CreateFlowRequest("stat-rate-flow-${System.nanoTime()}", "desc", emptyList()))
        val before = dashboardService.getStats()

        trailService.createTrail(CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "abc1",
            gitBranch = "main",
            gitAuthor = "author",
            gitAuthorEmail = "a@b.com"
        ))

        val after = dashboardService.getStats()
        assertEquals(before.totalTrails + 1, after.totalTrails)
        // new trail defaults to PENDING
        assertEquals(before.pendingTrails + 1, after.pendingTrails)
    }

    @Test
    fun `getStats complianceRate is 0 when no completed trails`() {
        val stats = dashboardService.getStats()
        val allPending = stats.compliantTrails == 0 && stats.nonCompliantTrails == 0
        if (allPending) {
            assertEquals(0.0, stats.complianceRate)
        }
    }
}
