package com.factstore.adapter.outbound.persistence.read

import com.factstore.core.domain.Trail
import com.factstore.core.port.outbound.read.ITrailReadRepository
import com.factstore.adapter.outbound.persistence.TrailRepositoryJpa
import com.factstore.dto.query.TrailView
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class TrailReadRepositoryAdapter(private val jpa: TrailRepositoryJpa) : ITrailReadRepository {

    override fun findById(id: UUID): TrailView? = jpa.findById(id).orElse(null)?.toView()

    override fun findAll(): List<TrailView> = jpa.findAll().map { it.toView() }

    override fun findByFlowId(flowId: UUID): List<TrailView> =
        jpa.findByFlowId(flowId).map { it.toView() }
}

fun Trail.toView() = TrailView(
    id = id,
    flowId = flowId,
    gitCommitSha = gitCommitSha,
    gitBranch = gitBranch,
    gitAuthor = gitAuthor,
    gitAuthorEmail = gitAuthorEmail,
    pullRequestId = pullRequestId,
    pullRequestReviewer = pullRequestReviewer,
    deploymentActor = deploymentActor,
    status = status,
    orgSlug = orgSlug,
    templateYaml = templateYaml,
    buildUrl = buildUrl,
    createdAt = createdAt,
    updatedAt = updatedAt
)
