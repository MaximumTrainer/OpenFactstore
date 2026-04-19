package com.factstore.adapter.inbound.web.command

import com.factstore.application.CiContextResolver
import com.factstore.application.DryRunContext
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.inbound.command.ITrailCommandHandler
import com.factstore.dto.command.CommandResult
import com.factstore.dto.command.CreateTrailCommand
import com.factstore.dto.DryRunResponse
import com.factstore.dto.TrailResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@Tag(name = "Trails – Commands", description = "Trail write operations (CQRS command path)")
class TrailCommandController(private val commandHandler: ITrailCommandHandler) {

    @PostMapping("/api/v2/trails")
    @Operation(summary = "Create/begin a trail")
    fun createTrail(
        @RequestBody command: CreateTrailCommand,
        @RequestHeader(value = "X-Factstore-CI-Context", required = false) ciContext: String?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<*> {
        val enriched = CiContextResolver.resolve(ciContext)?.let { provider ->
            CiContextResolver.enrich(command, provider)
        } ?: command
        if (DryRunContext.isDryRun(httpRequest)) {
            val now = Instant.now()
            val wouldBe = TrailResponse(
                id = UUID.randomUUID(),
                flowId = enriched.flowId,
                gitCommitSha = enriched.gitCommitSha ?: "",
                gitBranch = enriched.gitBranch ?: "",
                gitAuthor = enriched.gitAuthor,
                gitAuthorEmail = enriched.gitAuthorEmail,
                pullRequestId = enriched.pullRequestId,
                pullRequestReviewer = enriched.pullRequestReviewer,
                deploymentActor = enriched.deploymentActor,
                status = TrailStatus.PENDING,
                orgSlug = enriched.orgSlug,
                templateYaml = enriched.templateYaml,
                buildUrl = enriched.buildUrl,
                createdAt = now,
                updatedAt = now
            )
            return ResponseEntity.ok(DryRunResponse(wouldCreate = wouldBe))
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(commandHandler.createTrail(enriched))
    }
}
