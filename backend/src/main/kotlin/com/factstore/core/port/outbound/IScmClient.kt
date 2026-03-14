package com.factstore.core.port.outbound

import com.factstore.core.domain.ScmProvider
import java.time.Instant

data class PullRequestInfo(
    val id: String,
    val title: String,
    val author: String,
    val reviewers: List<String>,
    val mergedAt: Instant?,
    val url: String
)

interface IScmClient {
    val provider: ScmProvider

    /**
     * Finds the pull request associated with the given commit SHA in the given repository.
     * Returns null if no PR is found (404) or if the commit has no associated PR.
     * Throws on authentication failure (401/403) or network errors.
     */
    fun findPullRequestForCommit(repository: String, commitSha: String, token: String): PullRequestInfo?
}
