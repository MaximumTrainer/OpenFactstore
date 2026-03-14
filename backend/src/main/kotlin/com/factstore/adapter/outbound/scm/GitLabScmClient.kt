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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

@Component
class GitLabScmClient(private val restTemplate: RestTemplate) : IScmClient {

    private val log = LoggerFactory.getLogger(GitLabScmClient::class.java)

    override val provider: ScmProvider = ScmProvider.GITLAB

    override fun findPullRequestForCommit(repository: String, commitSha: String, token: String): PullRequestInfo? {
        val encodedRepo = URLEncoder.encode(repository, StandardCharsets.UTF_8)
        val url = "https://gitlab.com/api/v4/projects/$encodedRepo/repository/commits/$commitSha/merge_requests"
        val headers = HttpHeaders().apply {
            set("PRIVATE-TOKEN", token)
        }
        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity<Void>(headers), List::class.java)
            val mrs = response.body as? List<*> ?: return null
            if (mrs.isEmpty()) return null
            val mr = mrs[0] as? Map<*, *> ?: return null
            PullRequestInfo(
                id = mr["iid"]?.toString() ?: "",
                title = mr["title"]?.toString() ?: "",
                author = (mr["author"] as? Map<*, *>)?.get("username")?.toString() ?: "",
                reviewers = extractGitLabReviewers(mr),
                mergedAt = (mr["merged_at"] as? String)?.let { runCatching { Instant.parse(it) }.getOrNull() },
                url = mr["web_url"]?.toString() ?: ""
            )
        } catch (ex: HttpClientErrorException.NotFound) {
            log.debug("GitLab: no MRs found for $repository@$commitSha")
            null
        } catch (ex: HttpClientErrorException.Unauthorized) {
            throw IllegalStateException("GitLab authentication failed: check your token for repository $repository", ex)
        } catch (ex: HttpClientErrorException.Forbidden) {
            throw IllegalStateException("GitLab access forbidden: insufficient permissions for repository $repository", ex)
        } catch (ex: Exception) {
            log.error("GitLab API error for $repository@$commitSha: ${ex.message}")
            throw ex
        }
    }

    private fun extractGitLabReviewers(mr: Map<*, *>): List<String> =
        (mr["reviewers"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.mapNotNull { it["username"]?.toString() }
            ?: emptyList()
}
