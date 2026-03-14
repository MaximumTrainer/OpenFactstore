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

@Component
class GitHubScmClient(private val restTemplate: RestTemplate) : IScmClient {

    private val log = LoggerFactory.getLogger(GitHubScmClient::class.java)

    override val provider: ScmProvider = ScmProvider.GITHUB

    override fun findPullRequestForCommit(repository: String, commitSha: String, token: String): PullRequestInfo? {
        val url = "https://api.github.com/repos/$repository/commits/$commitSha/pulls"
        val headers = HttpHeaders().apply {
            set("Authorization", "Bearer $token")
            set("Accept", "application/vnd.github+json")
            set("X-GitHub-Api-Version", "2022-11-28")
        }
        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity<Void>(headers), List::class.java)
            val prs = response.body as? List<*> ?: return null
            if (prs.isEmpty()) return null
            val pr = prs[0] as? Map<*, *> ?: return null
            PullRequestInfo(
                id = pr["number"]?.toString() ?: "",
                title = pr["title"]?.toString() ?: "",
                author = (pr["user"] as? Map<*, *>)?.get("login")?.toString() ?: "",
                reviewers = extractGitHubReviewers(pr),
                mergedAt = (pr["merged_at"] as? String)?.let { Instant.parse(it) },
                url = pr["html_url"]?.toString() ?: ""
            )
        } catch (ex: HttpClientErrorException.NotFound) {
            log.debug("GitHub: no PRs found for $repository@$commitSha")
            null
        } catch (ex: HttpClientErrorException.Unauthorized) {
            throw IllegalStateException("GitHub authentication failed: check your token for repository $repository", ex)
        } catch (ex: HttpClientErrorException.Forbidden) {
            throw IllegalStateException("GitHub access forbidden: insufficient permissions for repository $repository", ex)
        } catch (ex: Exception) {
            log.error("GitHub API error for $repository@$commitSha: ${ex.message}")
            throw ex
        }
    }

    private fun extractGitHubReviewers(pr: Map<*, *>): List<String> {
        val requested = (pr["requested_reviewers"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.mapNotNull { it["login"]?.toString() }
            ?: emptyList()
        return requested
    }
}
