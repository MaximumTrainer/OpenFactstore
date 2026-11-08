package com.factstore.application

import com.factstore.core.port.inbound.ISearchService
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.GlobalSearchRequest
import com.factstore.dto.GlobalSearchResponse
import com.factstore.dto.SearchResponse
import com.factstore.dto.SearchResultItem
import com.factstore.exception.BadRequestException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val ALLOWED_TYPES = setOf("trail", "artifact")

@Service
@Transactional(readOnly = true)
class SearchService(
    private val trailRepository: ITrailRepository,
    private val artifactRepository: IArtifactRepository,
    private val flowRepository: IFlowRepository,
    private val tenantScope: TenantScope = TenantScope.UNSCOPED
) : ISearchService {

    private val log = LoggerFactory.getLogger(SearchService::class.java)

    // Search never reaches past the caller's organisation (#155 FR-5.3).
    private fun visible(trail: com.factstore.core.domain.Trail): Boolean =
        tenantScope.visible(trail.orgSlug ?: flowRepository.findById(trail.flowId)?.orgSlug)

    private fun visible(artifact: com.factstore.core.domain.Artifact): Boolean =
        tenantScope.visible(artifact.orgSlug)

    override fun search(query: String, type: String?): SearchResponse {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return SearchResponse(results = emptyList(), total = 0, query = trimmed, type = type)

        if (type != null && type.lowercase() !in ALLOWED_TYPES) {
            throw BadRequestException("Invalid type '$type'. Allowed values: ${ALLOWED_TYPES.joinToString(", ")}")
        }

        val results = mutableListOf<SearchResultItem>()

        if (type == null || type.equals("trail", ignoreCase = true)) {
            trailRepository.searchByQuery(trimmed).filter { visible(it) }.mapTo(results) { trail ->
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
            artifactRepository.searchByQuery(trimmed).filter { visible(it) }.mapTo(results) { artifact ->
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

        log.debug("Search type={} queryLength={} results={}", type, trimmed.length, results.size)
        return SearchResponse(results = results, total = results.size, query = trimmed, type = type)
    }

    override fun search(request: GlobalSearchRequest): GlobalSearchResponse {
        val query = request.query?.trim()

        val flows = flowRepository.findAll()
            .filter { flow ->
                tenantScope.visible(flow.orgSlug) &&
                (request.orgSlug == null || flow.orgSlug == request.orgSlug) &&
                (query.isNullOrBlank() || flow.name.contains(query, ignoreCase = true) || flow.description.contains(query, ignoreCase = true)) &&
                request.tags.all { (k, v) -> flow.tags[k] == v }
            }
            .map { it.toResponse() }

        val trails = trailRepository.findAll()
            .filter { trail ->
                visible(trail) &&
                (request.orgSlug == null || trail.orgSlug == request.orgSlug) &&
                (query.isNullOrBlank() || trail.gitCommitSha.contains(query, ignoreCase = true) ||
                    trail.name?.contains(query, ignoreCase = true) == true ||
                    trail.gitBranch.contains(query, ignoreCase = true) ||
                    trail.gitAuthor.contains(query, ignoreCase = true)) &&
                request.tags.all { (k, v) -> trail.tags[k] == v }
            }
            .map { it.toResponse() }

        val artifacts = artifactRepository.findAll()
            .filter { artifact ->
                visible(artifact) &&
                (request.orgSlug == null || artifact.orgSlug == request.orgSlug) &&
                (query.isNullOrBlank() || artifact.imageName.contains(query, ignoreCase = true) ||
                    artifact.imageTag.contains(query, ignoreCase = true)) &&
                request.tags.all { (k, v) -> artifact.tags[k] == v }
            }
            .map { it.toResponse() }

        log.debug("GlobalSearch orgSlug={} queryLength={} flows={} trails={} artifacts={}",
            request.orgSlug, query?.length, flows.size, trails.size, artifacts.size)
        return GlobalSearchResponse(flows = flows, trails = trails, artifacts = artifacts)
    }
}
