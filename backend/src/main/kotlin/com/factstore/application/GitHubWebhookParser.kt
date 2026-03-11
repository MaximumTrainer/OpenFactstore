package com.factstore.application

import com.factstore.core.domain.AttestationStatus
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class GitHubWebhookParser(private val objectMapper: ObjectMapper) {

    fun parse(payload: String): WebhookEvent {
        val root = objectMapper.readTree(payload)
        val action = root.path("action").asText("")
        val workflowRun = root.path("workflow_run")
        val conclusion = workflowRun.path("conclusion").asText("")
        val commitSha = workflowRun.path("head_sha").asText(null)
        val branch = workflowRun.path("head_branch").asText(null)
        val actor = extractActor(root)

        val (eventType, attestationType, attestationStatus) = mapGitHubEvent(action, conclusion)

        return WebhookEvent(
            eventType = eventType,
            commitSha = commitSha,
            branch = branch,
            actor = actor,
            actorEmail = null,
            attestationType = attestationType,
            attestationStatus = attestationStatus,
            details = "GitHub Actions workflow_run: action=$action conclusion=$conclusion",
            trailId = null
        )
    }

    private fun extractActor(root: JsonNode): String? {
        val sender = root.path("sender").path("login").asText(null)
        if (sender != null) return sender
        return root.path("workflow_run").path("actor").path("login").asText(null)
    }

    private fun mapGitHubEvent(action: String, conclusion: String): Triple<String, String?, AttestationStatus?> {
        if (action == "in_progress" || action == "requested") {
            return Triple("build.started", null, null)
        }
        if (action == "completed") {
            return when (conclusion) {
                "success" -> Triple("build.succeeded", "BUILD", AttestationStatus.PASSED)
                "failure" -> Triple("build.failed", "BUILD", AttestationStatus.FAILED)
                else -> Triple("build.failed", "BUILD", AttestationStatus.FAILED)
            }
        }
        return Triple("unknown", null, null)
    }
}
