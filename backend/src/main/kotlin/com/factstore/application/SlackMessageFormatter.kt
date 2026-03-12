package com.factstore.application

import com.factstore.core.domain.AttestationStatus
import com.factstore.dto.ArtifactResponse
import com.factstore.dto.AttestationResponse
import com.factstore.dto.FlowResponse
import com.factstore.dto.TrailResponse
import java.util.UUID

class SlackMessageFormatter {

    fun formatHelp(): String = """
        *Factstore Slash Commands*
        • `/factstore help` — List available commands
        • `/factstore search <sha-prefix>` — Search artifact by SHA-256 prefix
        • `/factstore trail <trailId>` — Show trail details and compliance status
        • `/factstore env <name>` — Show latest environment snapshot
        • `/factstore approve <approvalId> [comment]` — Approve a pending release approval
        • `/factstore reject <approvalId> [comment]` — Reject a pending release approval
    """.trimIndent()

    fun formatArtifactSearch(shaPrefix: String, artifacts: List<ArtifactResponse>): String {
        if (artifacts.isEmpty()) return "No artifacts found matching SHA prefix: `$shaPrefix`"
        return buildString {
            appendLine("*Artifacts matching `$shaPrefix`:*")
            artifacts.take(10).forEach { artifact ->
                appendLine("• *${artifact.imageName}:${artifact.imageTag}*")
                appendLine("  SHA: `${artifact.sha256Digest}`")
                appendLine("  Reported by: ${artifact.reportedBy}")
            }
            if (artifacts.size > 10) appendLine("_… and ${artifacts.size - 10} more_")
        }.trim()
    }

    fun formatTrailDetails(
        trail: TrailResponse,
        flow: FlowResponse,
        attestations: List<AttestationResponse>
    ): String {
        return buildString {
            appendLine("*Trail Details*")
            appendLine("• *ID:* `${trail.id}`")
            appendLine("• *Flow:* ${flow.name}")
            appendLine("• *Commit:* `${trail.gitCommitSha}`")
            appendLine("• *Branch:* ${trail.gitBranch}")
            appendLine("• *Author:* ${trail.gitAuthor}")
            appendLine("• *Status:* ${trail.status}")
            appendLine()
            appendLine("*Attestations (${attestations.size}):*")
            if (attestations.isEmpty()) {
                appendLine("_No attestations recorded_")
            } else {
                attestations.forEach { att ->
                    val icon = when (att.status) {
                        AttestationStatus.PASSED -> "✅"
                        AttestationStatus.FAILED -> "❌"
                        AttestationStatus.PENDING -> "⏳"
                    }
                    appendLine("• $icon ${att.type}")
                }
            }
        }.trim()
    }

    fun formatTrailNonCompliant(
        trailId: UUID,
        flowName: String,
        missingTypes: List<String>,
        failedTypes: List<String>,
        trailUrl: String?
    ): String {
        return buildString {
            appendLine("🚨 *Trail Non-Compliant*")
            appendLine("• *Trail ID:* `$trailId`")
            appendLine("• *Flow:* $flowName")
            if (missingTypes.isNotEmpty()) {
                appendLine("• *Missing attestations:* ${missingTypes.joinToString(", ")}")
            }
            if (failedTypes.isNotEmpty()) {
                appendLine("• *Failed attestations:* ${failedTypes.joinToString(", ")}")
            }
            if (trailUrl != null) appendLine("• <$trailUrl|View Trail>")
        }.trim()
    }

    fun formatApprovalRequested(
        approvalId: String,
        artifactSha: String,
        targetEnvironment: String,
        requiredApprovers: List<String>
    ): String {
        return buildString {
            appendLine("✅ *Approval Requested*")
            appendLine("• *Approval ID:* `$approvalId`")
            appendLine("• *Artifact SHA:* `$artifactSha`")
            appendLine("• *Target Environment:* $targetEnvironment")
            appendLine("• *Required Approvers:* ${requiredApprovers.joinToString(", ")}")
            appendLine()
            appendLine("Use `/factstore approve $approvalId` or `/factstore reject $approvalId` to respond.")
        }.trim()
    }

    fun formatApprovalDecision(
        approvalId: String,
        decision: String,
        decidedBy: String,
        comment: String?
    ): String {
        val icon = if (decision == "approved") "✅" else "❌"
        return buildString {
            appendLine("$icon *Approval ${decision.replaceFirstChar { it.uppercase() }}*")
            appendLine("• *Approval ID:* `$approvalId`")
            appendLine("• *Decision by:* $decidedBy")
            if (comment != null) appendLine("• *Comment:* $comment")
        }.trim()
    }

    fun formatUnknownCommand(input: String): String =
        "Unknown command: `$input`. Type `/factstore help` for available commands."
}
