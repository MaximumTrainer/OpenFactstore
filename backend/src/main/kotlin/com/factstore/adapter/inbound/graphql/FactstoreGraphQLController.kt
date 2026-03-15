package com.factstore.adapter.inbound.graphql

import com.factstore.core.domain.AttestationStatus
import com.factstore.core.port.inbound.IArtifactService
import com.factstore.core.port.inbound.IAttestationService
import com.factstore.core.port.inbound.IEnvironmentService
import com.factstore.core.port.inbound.IFlowService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.dto.ArtifactResponse
import com.factstore.dto.AttestationResponse
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.EnvironmentResponse
import com.factstore.dto.FlowResponse
import com.factstore.dto.TrailResponse
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class FactstoreGraphQLController(
    private val flowService: IFlowService,
    private val trailService: ITrailService,
    private val attestationService: IAttestationService,
    private val environmentService: IEnvironmentService,
    private val artifactService: IArtifactService
) {

    @QueryMapping
    fun flow(@Argument id: String): FlowResponse? =
        try { flowService.getFlow(UUID.fromString(id)) } catch (e: Exception) { null }

    @QueryMapping
    fun flows(@Argument orgSlug: String): List<FlowResponse> =
        flowService.listFlowsByOrg(orgSlug)

    @QueryMapping
    fun trail(@Argument id: String): TrailResponse? =
        try { trailService.getTrail(UUID.fromString(id)) } catch (e: Exception) { null }

    @QueryMapping
    fun trails(@Argument flowId: String): List<TrailResponse> =
        trailService.listTrailsForFlow(UUID.fromString(flowId))

    @QueryMapping
    fun artifact(@Argument sha256: String): ArtifactGql? =
        artifactService.findBySha256(sha256).firstOrNull()?.toGql()

    @QueryMapping
    fun attestations(@Argument trailId: String): List<AttestationResponse> =
        attestationService.listAttestations(UUID.fromString(trailId))

    @QueryMapping
    fun environments(@Argument orgSlug: String): List<EnvironmentResponse> =
        environmentService.listEnvironments().filter { it.orgSlug == orgSlug }

    @MutationMapping
    fun createAttestation(@Argument input: CreateAttestationInput): AttestationResponse =
        attestationService.recordAttestation(
            trailId = UUID.fromString(input.trailId),
            request = CreateAttestationRequest(
                type = input.type,
                status = AttestationStatus.valueOf(input.status),
                details = input.details
            )
        )

    // Trail.name is not a field on TrailResponse; map gitCommitSha as the display name
    @SchemaMapping(typeName = "Trail", field = "name")
    fun trailName(trail: TrailResponse): String? = trail.gitCommitSha
}

data class CreateAttestationInput(
    val trailId: String,
    val type: String,
    val status: String,
    val details: String? = null
)

data class ArtifactGql(
    val sha256: String,
    val name: String?,
    val type: String?,
    val createdAt: String?
)

fun ArtifactResponse.toGql() = ArtifactGql(
    sha256 = sha256Digest,
    name = imageName,
    type = null,
    createdAt = reportedAt.toString()
)
