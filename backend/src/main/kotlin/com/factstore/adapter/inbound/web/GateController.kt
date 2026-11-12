package com.factstore.adapter.inbound.web

import com.factstore.application.gates.GateDecision
import com.factstore.application.gates.GateEvaluationService
import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Deployment gates with risk-based fallback (#193). */
@RestController
@Tag(name = "Gates", description = "May this artifact be deployed to this environment?")
class GateController(private val service: GateEvaluationService) {

    @PostMapping("/api/v1/queries/gates/evaluate")
    @PreAuthorize("@authz.has('assert:execute')")
    @Operation(
        summary = "Evaluate a deployment gate",
        description = "Body: artifact_sha256, target_environment, flow_id, optional required_policies (attestation types). " +
            "APPROVED or REJECTED, with fallback_mode NONE, or FAIL_OPEN / FAIL_CLOSED by the environment's risk tier " +
            "when the evidence cannot be evaluated, in which case audit_alert_id names the alert raised."
    )
    fun evaluate(@RequestBody body: JsonNode): ResponseEntity<GateDecision> = ResponseEntity.ok(service.evaluate(body))

    @PostMapping("/api/v1/commands/gates/fallback-reports")
    @PreAuthorize("@authz.has('assert:execute')")
    @Operation(
        summary = "Report a gate decision the client made by risk tier",
        description = "Sent by the CLI once Factstore is reachable again, for each gate it decided by the environment's " +
            "tier while Factstore was unreachable. 201 with audit_alert_id, or 200 for a report already recorded " +
            "(same report_id)."
    )
    fun report(@RequestBody body: JsonNode): ResponseEntity<Map<String, Any>> {
        val result = service.reportClientFallback(body)
        val status = if (result.created) org.springframework.http.HttpStatus.CREATED else org.springframework.http.HttpStatus.OK
        return ResponseEntity.status(status).body(mapOf("audit_alert_id" to result.auditAlertId))
    }
}
