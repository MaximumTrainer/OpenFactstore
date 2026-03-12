package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.ISlackService
import com.factstore.dto.ApprovalRequestedNotificationRequest
import com.factstore.dto.ConfigureSlackRequest
import com.factstore.dto.SlackCommandResponse
import com.factstore.dto.SlackConfigResponse
import com.factstore.dto.SlackNotification
import com.factstore.dto.TrailNonCompliantNotificationRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@RestController
@Tag(name = "Slack Integration", description = "Slack app configuration and slash command handling")
class SlackController(private val slackService: ISlackService) {

    // ── Organisation-scoped configuration ──────────────────────────────────

    @PostMapping("/api/v1/organisations/{slug}/slack")
    @Operation(summary = "Configure Slack integration for an organisation")
    fun configureSlack(
        @PathVariable slug: String,
        @RequestBody request: ConfigureSlackRequest
    ): ResponseEntity<SlackConfigResponse> =
        ResponseEntity.ok(slackService.configureSlack(slug, request))

    @DeleteMapping("/api/v1/organisations/{slug}/slack")
    @Operation(summary = "Remove Slack integration for an organisation")
    fun removeSlack(@PathVariable slug: String): ResponseEntity<Void> {
        slackService.removeSlack(slug)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/api/v1/organisations/{slug}/slack")
    @Operation(summary = "Get Slack configuration for an organisation")
    fun getSlackConfig(@PathVariable slug: String): ResponseEntity<SlackConfigResponse> =
        ResponseEntity.ok(slackService.getConfig(slug))

    // ── Slack slash command handler ─────────────────────────────────────────
    // Slack sends application/x-www-form-urlencoded. We read the raw body so
    // we can verify the X-Slack-Signature before delegating to the service.

    @PostMapping(
        "/api/v1/organisations/{slug}/slack/commands",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE]
    )
    @Operation(summary = "Handle Slack slash commands for an organisation")
    fun handleSlashCommand(
        @PathVariable slug: String,
        @RequestHeader("X-Slack-Signature", required = false) signature: String?,
        @RequestHeader("X-Slack-Request-Timestamp", required = false) timestamp: String?,
        request: HttpServletRequest
    ): ResponseEntity<SlackCommandResponse> {
        val rawBody = request.inputStream.readBytes().toString(Charsets.UTF_8)
        slackService.verifySlackRequest(slug, timestamp, signature, rawBody)
        val params = parseFormParams(rawBody)
        return ResponseEntity.ok(
            slackService.handleSlashCommand(
                slug,
                params["text"] ?: "",
                params["user_id"] ?: "",
                params["user_name"] ?: ""
            )
        )
    }

    // ── Slack interactive actions (button clicks) ───────────────────────────
    // Slack sends application/x-www-form-urlencoded with a single "payload"
    // field containing a JSON string.

    @PostMapping(
        "/api/v1/organisations/{slug}/slack/actions",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE]
    )
    @Operation(summary = "Handle Slack interactive actions (button clicks) for an organisation")
    fun handleInteractiveAction(
        @PathVariable slug: String,
        @RequestHeader("X-Slack-Signature", required = false) signature: String?,
        @RequestHeader("X-Slack-Request-Timestamp", required = false) timestamp: String?,
        request: HttpServletRequest
    ): ResponseEntity<SlackCommandResponse> {
        val rawBody = request.inputStream.readBytes().toString(Charsets.UTF_8)
        slackService.verifySlackRequest(slug, timestamp, signature, rawBody)
        val params = parseFormParams(rawBody)
        val payloadJson = params["payload"]
            ?: return ResponseEntity.badRequest()
                .body(SlackCommandResponse(text = "Missing payload parameter"))
        return ResponseEntity.ok(slackService.handleInteractiveAction(slug, payloadJson))
    }

    // ── Outbound notification triggers ─────────────────────────────────────

    @PostMapping("/api/v1/organisations/{slug}/slack/notify/trail-non-compliant")
    @Operation(summary = "Send a trail non-compliant notification to Slack")
    fun notifyTrailNonCompliant(
        @PathVariable slug: String,
        @RequestBody request: TrailNonCompliantNotificationRequest
    ): ResponseEntity<Void> {
        slackService.sendNotification(
            slug,
            SlackNotification.TrailNonCompliant(
                trailId = request.trailId,
                flowName = request.flowName,
                missingAttestationTypes = request.missingAttestationTypes,
                failedAttestationTypes = request.failedAttestationTypes,
                trailUrl = request.trailUrl
            )
        )
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/api/v1/organisations/{slug}/slack/notify/approval-requested")
    @Operation(summary = "Send an approval-requested notification to Slack")
    fun notifyApprovalRequested(
        @PathVariable slug: String,
        @RequestBody request: ApprovalRequestedNotificationRequest
    ): ResponseEntity<Void> {
        slackService.sendNotification(
            slug,
            SlackNotification.ApprovalRequested(
                approvalId = request.approvalId,
                artifactSha = request.artifactSha,
                targetEnvironment = request.targetEnvironment,
                requiredApprovers = request.requiredApprovers
            )
        )
        return ResponseEntity.accepted().build()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun parseFormParams(rawBody: String): Map<String, String> =
        rawBody.split("&").mapNotNull { param ->
            val eq = param.indexOf('=')
            if (eq < 0) null
            else {
                val key = URLDecoder.decode(param.substring(0, eq), StandardCharsets.UTF_8)
                val value = URLDecoder.decode(param.substring(eq + 1), StandardCharsets.UTF_8)
                key to value
            }
        }.toMap()
}
