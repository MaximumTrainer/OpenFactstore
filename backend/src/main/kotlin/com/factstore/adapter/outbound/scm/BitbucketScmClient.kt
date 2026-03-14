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
class BitbucketScmClient(private val restTemplate: RestTemplate) : IScmClient {

    private val log = LoggerFactory.getLogger(BitbucketScmClient::class.java)

    override val provider: ScmProvider = ScmProvider.BITBUCKET

    override fun findPullRequestForCommit(repository: String, commitSha: String, token: String): PullRequestInfo? {
        // repository format: workspace/repo-slug
        val url = "https://api.bitbucket.org/2.0/repositories/$repository/commits/$commitSha/pullrequests"
        val headers = HttpHeaders().apply {
            set("Authorization", "Bearer $token")
        }
        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity<Void>(headers), Map::class.java)
            val body = response.body ?: return null
            val values = body["values"] as? List<*> ?: return null
            if (values.isEmpty()) return null
            val pr = values[0] as? Map<*, *> ?: return null
            PullRequestInfo(
                id = pr["id"]?.toString() ?: "",
                title = pr["title"]?.toString() ?: "",
                author = (pr["author"] as? Map<*, *>)?.get("display_name")?.toString() ?: "",
                reviewers = extractBitbucketReviewers(pr),
                mergedAt = null,
                url = (pr["links"] as? Map<*, *>)?.let { links ->
                    (links["html"] as? Map<*, *>)?.get("href")?.toString()
                } ?: ""
            )
        } catch (ex: HttpClientErrorException.NotFound) {
            log.debug("Bitbucket: no PRs found for $repository@$commitSha")
            null
        } catch (ex: HttpClientErrorException.Unauthorized) {
            throw IllegalStateException("Bitbucket authentication failed: check your token for repository $repository", ex)
        } catch (ex: HttpClientErrorException.Forbidden) {
            throw IllegalStateException("Bitbucket access forbidden: insufficient permissions for repository $repository", ex)
        } catch (ex: Exception) {
            log.error("Bitbucket API error for $repository@$commitSha: ${ex.message}")
            throw ex
        }
    }

    private fun extractBitbucketReviewers(pr: Map<*, *>): List<String> =
        (pr["reviewers"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.mapNotNull { it["display_name"]?.toString() }
            ?: emptyList()
}
