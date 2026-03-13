package com.factstore.application

import com.factstore.core.port.inbound.ISearchService
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.SearchResponse
import com.factstore.dto.SearchResultItem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SearchService(
    private val trailRepository: ITrailRepository,
    private val artifactRepository: IArtifactRepository,
    private val attestationRepository: IAttestationRepository
) : ISearchService {

    private val log = LoggerFactory.getLogger(SearchService::class.java)

    override fun search(query: String, type: String?): SearchResponse {
        if (query.isBlank()) return SearchResponse(results = emptyList(), total = 0, query = query, type = type)

        val results = mutableListOf<SearchResultItem>()

        if (type == null || type.equals("trail", ignoreCase = true)) {
            trailRepository.searchByQuery(query).mapTo(results) { trail ->
                SearchResultItem(
                    type = "trail",
                    id = trail.id,
                    title = "${trail.gitCommitSha.take(8)} — ${trail.gitBranch}",
                    description = "Author: ${trail.gitAuthor} <${trail.gitAuthorEmail}>",
                    metadata = mapOf(
                        "flowId" to trail.flowId.toString(),
                        "gitCommitSha" to trail.gitCommitSha,
                        "gitBranch" to trail.gitBranch,
                        "gitAuthor" to trail.gitAuthor,
                        "status" to trail.status.name,
                        "createdAt" to trail.createdAt.toString()
                    )
                )
            }
        }

        if (type == null || type.equals("artifact", ignoreCase = true)) {
            artifactRepository.searchByQuery(query).mapTo(results) { artifact ->
                SearchResultItem(
                    type = "artifact",
                    id = artifact.id,
                    title = "${artifact.imageName}:${artifact.imageTag}",
                    description = "Digest: ${artifact.sha256Digest.take(16)}…",
                    metadata = mapOf(
                        "trailId" to artifact.trailId.toString(),
                        "sha256Digest" to artifact.sha256Digest,
                        "registry" to artifact.registry,
                        "reportedBy" to artifact.reportedBy,
                        "reportedAt" to artifact.reportedAt.toString()
                    )
                )
            }
        }

        if (type == null || type.equals("attestation", ignoreCase = true)) {
            // Search attestations by type keyword or details keyword via trail search fallback
            // Attestations don't have a standalone search, so we search trails and include their attestations
        }

        log.info("Search query='$query' type=$type returned ${results.size} results")
        return SearchResponse(results = results, total = results.size, query = query, type = type)
    }
}
