package com.factstore.adapter.outbound.scm

import com.factstore.core.domain.ScmProvider
import com.factstore.core.port.outbound.IScmClient
import com.factstore.core.port.outbound.PullRequestInfo
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.Base64

@Component
class AzureDevOpsScmClient(private val restTemplate: RestTemplate) : IScmClient {

    private val log = LoggerFactory.getLogger(AzureDevOpsScmClient::class.java)

    override val provider: ScmProvider = ScmProvider.AZURE_DEVOPS

    override fun findPullRequestForCommit(repository: String, commitSha: String, token: String): PullRequestInfo? {
        // repository format: organization/project/repository
        val parts = repository.split("/")
        if (parts.size < 3) {
            throw IllegalArgumentException("Azure DevOps repository must be in format 'organization/project/repository', got: $repository")
        }
        val (org, project, repo) = parts
        // Search for PRs associated with the commit via the commits endpoint
        val url = "https://dev.azure.com/$org/$project/_apis/git/repositories/$repo/commits/$commitSha/pullRequests?api-version=7.1"

        // Azure DevOps uses Basic auth with Base64(":token")
        val basicAuth = Base64.getEncoder().encodeToString(":$token".toByteArray())
        val headers = HttpHeaders().apply {
            set("Authorization", "Basic $basicAuth")
        }
        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity<Void>(headers), Map::class.java)
            val body = response.body ?: return null
            val value = body["value"] as? List<*> ?: return null
            if (value.isEmpty()) return null
            val pr = value[0] as? Map<*, *> ?: return null
            val prId = pr["pullRequestId"]?.toString() ?: ""
            val prUrl = "https://dev.azure.com/$org/$project/_git/$repo/pullrequest/$prId"
            PullRequestInfo(
                id = prId,
                title = pr["title"]?.toString() ?: "",
                author = (pr["createdBy"] as? Map<*, *>)?.get("displayName")?.toString() ?: "",
                reviewers = extractAzureReviewers(pr),
                mergedAt = (pr["closedDate"] as? String)?.let { runCatching { Instant.parse(it) }.getOrNull() },
                url = prUrl
            )
        } catch (ex: HttpClientErrorException.NotFound) {
            log.debug("Azure DevOps: no PRs found for $repository@$commitSha")
            null
        } catch (ex: HttpClientErrorException.Unauthorized) {
            throw IllegalStateException("Azure DevOps authentication failed: check your token for repository $repository", ex)
        } catch (ex: HttpClientErrorException.Forbidden) {
            throw IllegalStateException("Azure DevOps access forbidden: insufficient permissions for repository $repository", ex)
        } catch (ex: Exception) {
            log.error("Azure DevOps API error for $repository@$commitSha: ${ex.message}")
            throw ex
        }
    }

    private fun extractAzureReviewers(pr: Map<*, *>): List<String> =
        (pr["reviewers"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.mapNotNull { it["displayName"]?.toString() }
            ?: emptyList()
}
