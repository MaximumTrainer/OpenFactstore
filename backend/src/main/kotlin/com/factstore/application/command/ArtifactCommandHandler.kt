package com.factstore.application.command

import com.factstore.core.domain.Artifact
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.port.inbound.command.IArtifactCommandHandler
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.command.CommandResult
import com.factstore.dto.command.ReportArtifactCommand
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ArtifactCommandHandler(
    private val artifactRepository: IArtifactRepository,
    private val trailRepository: ITrailRepository,
    private val auditService: IAuditService,
    private val eventAppender: EventAppender
) : IArtifactCommandHandler {

    private val log = LoggerFactory.getLogger(ArtifactCommandHandler::class.java)

    override fun reportArtifact(command: ReportArtifactCommand): CommandResult {
        if (!trailRepository.existsById(command.trailId)) throw NotFoundException("Trail not found: ${command.trailId}")
        val artifact = Artifact(
            trailId = command.trailId,
            imageName = command.imageName,
            imageTag = command.imageTag,
            sha256Digest = command.sha256Digest,
            registry = command.registry,
            reportedBy = command.reportedBy,
            orgSlug = command.orgSlug
        )
        val saved = artifactRepository.save(artifact)
        eventAppender.append(DomainEvent.ArtifactReported(
            aggregateId = saved.id,
            trailId = saved.trailId,
            imageName = saved.imageName,
            imageTag = saved.imageTag,
            sha256Digest = saved.sha256Digest,
            registry = saved.registry,
            reportedBy = saved.reportedBy,
            orgSlug = saved.orgSlug
        ))
        auditService.record(
            eventType = AuditEventType.ARTIFACT_DEPLOYED,
            actor = command.reportedBy,
            payload = mapOf(
                "artifactId" to saved.id.toString(),
                "trailId" to command.trailId.toString(),
                "imageName" to saved.imageName,
                "imageTag" to saved.imageTag,
                "sha256Digest" to saved.sha256Digest,
                "registry" to saved.registry
            ),
            trailId = command.trailId,
            artifactSha256 = saved.sha256Digest
        )
        log.info("Reported artifact: ${saved.id} digest=${saved.sha256Digest}")
        return CommandResult(id = saved.id, status = "created")
    }
}
