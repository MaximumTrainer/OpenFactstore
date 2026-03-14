package com.factstore.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.factstore.adapter.mock.InMemoryAttestationRepository
import com.factstore.adapter.mock.InMemoryScmIntegrationRepository
import com.factstore.adapter.mock.InMemoryTrailRepository
import com.factstore.application.PullRequestAttestationService
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.ScmIntegration
import com.factstore.core.domain.ScmProvider
import com.factstore.core.domain.Trail
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IScmClient
import com.factstore.core.port.outbound.PullRequestInfo
import com.factstore.dto.AuditEventPage
import com.factstore.dto.AuditEventResponse
import com.factstore.dto.CreatePrAttestationRequest
import com.factstore.exception.NotFoundException
import com.factstore.exception.PullRequestNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Unit tests for PullRequestAttestationService, run without a Spring context.
 * Uses in-memory mock adapters and a stub SCM client.
 */
class PullRequestAttestationServiceTest {

    private lateinit var attestationRepository: InMemoryAttestationRepository
    private lateinit var trailRepository: InMemoryTrailRepository
    private lateinit var scmIntegrationRepository: InMemoryScmIntegrationRepository
    private lateinit var mockScmClient: StubScmClient
    private lateinit var service: PullRequestAttestationService

    private val objectMapper = ObjectMapper().findAndRegisterModules()

    @BeforeEach
    fun setUp() {
        attestationRepository = InMemoryAttestationRepository()
        trailRepository = InMemoryTrailRepository()
        scmIntegrationRepository = InMemoryScmIntegrationRepository()
        mockScmClient = StubScmClient(ScmProvider.GITHUB)
        service = PullRequestAttestationService(
            scmClients = listOf(mockScmClient),
            attestationRepository = attestationRepository,
            trailRepository = trailRepository,
            scmIntegrationRepository = scmIntegrationRepository,
            auditService = NoOpAuditService(),
            objectMapper = objectMapper
        )
    }

    private fun makeTrail(): Trail {
        val trail = Trail(
            flowId = UUID.randomUUID(),
            gitCommitSha = "abc123",
            gitBranch = "main",
            gitAuthor = "author",
            gitAuthorEmail = "author@example.com",
            status = TrailStatus.PENDING
        )
        return trailRepository.save(trail)
    }

    private fun storeIntegration(orgSlug: String, provider: ScmProvider, token: String = "secret-token") {
        val encoded = Base64.getEncoder().encodeToString(token.toByteArray())
        scmIntegrationRepository.save(
            ScmIntegration(orgSlug = orgSlug, provider = provider, tokenEncrypted = encoded)
        )
    }

    @Test
    fun `PR found results in PASSED attestation with details`() {
        val trail = makeTrail()
        storeIntegration("my-org", ScmProvider.GITHUB)
        mockScmClient.pullRequestInfo = PullRequestInfo(
            id = "42",
            title = "feat: add cool feature",
            author = "alice",
            reviewers = listOf("bob", "charlie"),
            mergedAt = Instant.parse("2024-01-15T12:00:00Z"),
            url = "https://github.com/my-org/backend/pull/42"
        )

        val result = service.attestPullRequest(
            trail.id,
            CreatePrAttestationRequest(
                provider = ScmProvider.GITHUB,
                repository = "my-org/backend",
                commitSha = "abc123",
                assertOnMissing = false,
                orgSlug = "my-org"
            )
        )

        assertEquals(AttestationStatus.PASSED, result.status)
        assertEquals("pull-request", result.type)
        assertNotNull(result.details)
        assertTrue(result.details!!.contains("feat: add cool feature"))
        assertTrue(result.details!!.contains("alice"))
    }

    @Test
    fun `PR not found results in FAILED attestation`() {
        val trail = makeTrail()
        storeIntegration("my-org", ScmProvider.GITHUB)
        mockScmClient.pullRequestInfo = null

        val result = service.attestPullRequest(
            trail.id,
            CreatePrAttestationRequest(
                provider = ScmProvider.GITHUB,
                repository = "my-org/backend",
                commitSha = "deadbeef",
                assertOnMissing = false,
                orgSlug = "my-org"
            )
        )

        assertEquals(AttestationStatus.FAILED, result.status)
        assertEquals("pull-request", result.type)
        assertNotNull(result.details)
    }

    @Test
    fun `PR not found with assertOnMissing=true throws PullRequestNotFoundException`() {
        val trail = makeTrail()
        storeIntegration("my-org", ScmProvider.GITHUB)
        mockScmClient.pullRequestInfo = null

        assertThrows<PullRequestNotFoundException> {
            service.attestPullRequest(
                trail.id,
                CreatePrAttestationRequest(
                    provider = ScmProvider.GITHUB,
                    repository = "my-org/backend",
                    commitSha = "deadbeef",
                    assertOnMissing = true,
                    orgSlug = "my-org"
                )
            )
        }
    }

    @Test
    fun `unknown trail throws NotFoundException`() {
        assertThrows<NotFoundException> {
            service.attestPullRequest(
                UUID.randomUUID(),
                CreatePrAttestationRequest(
                    provider = ScmProvider.GITHUB,
                    repository = "my-org/backend",
                    commitSha = "abc123",
                    orgSlug = "my-org"
                )
            )
        }
    }

    @Test
    fun `missing SCM integration throws NotFoundException`() {
        val trail = makeTrail()
        // No integration registered for this org

        assertThrows<NotFoundException> {
            service.attestPullRequest(
                trail.id,
                CreatePrAttestationRequest(
                    provider = ScmProvider.GITHUB,
                    repository = "my-org/backend",
                    commitSha = "abc123",
                    orgSlug = "unknown-org"
                )
            )
        }
    }

    @Test
    fun `GitHub API error propagates as exception`() {
        val trail = makeTrail()
        storeIntegration("my-org", ScmProvider.GITHUB)
        mockScmClient.throwException = RuntimeException("Network error: connection refused")

        assertThrows<RuntimeException> {
            service.attestPullRequest(
                trail.id,
                CreatePrAttestationRequest(
                    provider = ScmProvider.GITHUB,
                    repository = "my-org/backend",
                    commitSha = "abc123",
                    orgSlug = "my-org"
                )
            )
        }
    }

    @Test
    fun `FAILED attestation marks trail as NON_COMPLIANT`() {
        val trail = makeTrail()
        storeIntegration("my-org", ScmProvider.GITHUB)
        mockScmClient.pullRequestInfo = null

        service.attestPullRequest(
            trail.id,
            CreatePrAttestationRequest(
                provider = ScmProvider.GITHUB,
                repository = "my-org/backend",
                commitSha = "abc123",
                assertOnMissing = false,
                orgSlug = "my-org"
            )
        )

        val updatedTrail = trailRepository.findById(trail.id)!!
        assertEquals(TrailStatus.NON_COMPLIANT, updatedTrail.status)
    }
}

/** Stub SCM client that returns a configurable PullRequestInfo or throws a configured exception. */
class StubScmClient(override val provider: ScmProvider) : IScmClient {
    var pullRequestInfo: PullRequestInfo? = null
    var throwException: Exception? = null

    override fun findPullRequestForCommit(repository: String, commitSha: String, token: String): PullRequestInfo? {
        throwException?.let { throw it }
        return pullRequestInfo
    }
}

/** No-op audit service for unit tests — records nothing. */
class NoOpAuditService : IAuditService {
    override fun record(
        eventType: AuditEventType,
        actor: String,
        payload: Map<String, Any?>,
        trailId: UUID?,
        artifactSha256: String?,
        environmentId: UUID?
    ): AuditEventResponse = AuditEventResponse(
        id = UUID.randomUUID(),
        eventType = eventType,
        actor = actor,
        payload = payload,
        trailId = trailId,
        artifactSha256 = artifactSha256,
        environmentId = environmentId,
        createdAt = Instant.now()
    )

    override fun getEvent(id: UUID): AuditEventResponse =
        throw UnsupportedOperationException("Not used in tests")

    override fun queryEvents(
        eventType: AuditEventType?,
        trailId: UUID?,
        actor: String?,
        from: Instant?,
        to: Instant?,
        page: Int,
        size: Int,
        sortDesc: Boolean
    ): AuditEventPage = throw UnsupportedOperationException("Not used in tests")

    override fun getEventsForTrail(trailId: UUID): List<AuditEventResponse> = emptyList()
}
