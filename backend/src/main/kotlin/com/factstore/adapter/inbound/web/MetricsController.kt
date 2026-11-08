package com.factstore.adapter.inbound.web

import org.springframework.security.access.prepost.PreAuthorize
import com.factstore.application.ComplianceMetricsService
import com.factstore.application.DeliveryMetricsService
import com.factstore.dto.ComplianceMetricsSummary
import com.factstore.dto.DeliveryMetricsResponse
import com.factstore.dto.SecurityMetricsSummary
import org.springframework.http.ResponseEntity
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/metrics")
@Tag(name = "Metrics", description = "Compliance, security and delivery metrics")
class MetricsController(
    private val complianceMetricsService: ComplianceMetricsService,
    private val deliveryMetricsService: DeliveryMetricsService
) {

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/delivery")
    @Operation(
        summary = "Delivery metrics: DORA where the data supports it, gates throughout",
        description = "A metric that cannot be derived from what Factstore records reports " +
            "available=false with a basis explaining why, rather than a misleading zero."
    )
    fun getDeliveryMetrics(
        @RequestParam(defaultValue = "30") days: Int
    ): ResponseEntity<DeliveryMetricsResponse> =
        ResponseEntity.ok(deliveryMetricsService.getDeliveryMetrics(days))

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/compliance")
    fun getComplianceMetrics(): ResponseEntity<ComplianceMetricsSummary> =
        ResponseEntity.ok(complianceMetricsService.getComplianceMetrics())

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/security")
    fun getSecurityMetrics(): ResponseEntity<SecurityMetricsSummary> =
        ResponseEntity.ok(complianceMetricsService.getSecurityMetrics())
}
