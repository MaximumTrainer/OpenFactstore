package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IDashboardService
import com.factstore.dto.DashboardStatsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Aggregate dashboard statistics")
class DashboardController(private val dashboardService: IDashboardService) {

    @GetMapping("/stats")
    @Operation(summary = "Get aggregate dashboard statistics")
    fun getStats(): ResponseEntity<DashboardStatsResponse> =
        ResponseEntity.ok(dashboardService.getStats())
}
