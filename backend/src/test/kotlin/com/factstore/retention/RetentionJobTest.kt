package com.factstore.retention

import com.factstore.adapter.mock.InMemoryRetentionArchive
import com.factstore.application.ArtifactService
import com.factstore.application.FlowService
import com.factstore.application.schema.SchemaRegistryService
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.EnvironmentSnapshot
import com.factstore.core.domain.EnvironmentType
import com.factstore.core.domain.LegalHoldScope
import com.factstore.core.domain.Notification
import com.factstore.core.domain.RetentionDataClass
import com.factstore.core.domain.SnapshotArtifact
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.inbound.IEnvironmentService
import com.factstore.core.port.inbound.IRetentionService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.core.port.outbound.IEnvironmentSnapshotRepository
import com.factstore.core.port.outbound.IEventStore
import com.factstore.core.port.outbound.INotificationRepository
import com.factstore.core.port.outbound.ISnapshotArtifactRepository
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.PlaceLegalHoldRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * #251: the retention job, driven by a clock the test controls. It archives environment snapshots,
 * prunes notifications and shreds fact content past each organisation's retention period, skips
 * anything under legal hold, audits each batch, counts what it did, and can run dry. Ledger
 * records are never touched, so ledger proofs still verify after a retention shred.
 */
@SpringBootTest(properties = ["security.enforce-auth=true", "factstore.ingest.require-signature=false"])
@AutoConfigureMockMvc
@Transactional
@Import(RetentionJobTest.Fixtures::class)
class RetentionJobTest {

    class MovableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    @TestConfiguration
    class Fixtures {
        @Bean @Primary fun movableClock() = MovableClock(START)
        @Bean @Primary fun inMemoryRetentionArchive() = InMemoryRetentionArchive()
    }

    companion object {
        val START: Instant = Instant.parse("2026-03-15T12:00:00Z")
        val SUBJECT = "4".repeat(64)
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var clock: MovableClock
    @Autowired lateinit var archive: InMemoryRetentionArchive
    @Autowired lateinit var retention: IRetentionService
    @Autowired lateinit var environments: IEnvironmentService
    @Autowired lateinit var snapshots: IEnvironmentSnapshotRepository
    @Autowired lateinit var snapshotArtifacts: ISnapshotArtifactRepository
    @Autowired lateinit var notifications: INotificationRepository
    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: ITrailService
    @Autowired lateinit var artifactService: ArtifactService
    @Autowired lateinit var schemas: SchemaRegistryService
    @Autowired lateinit var eventStore: IEventStore
    @Autowired lateinit var audit: IAuditService
    @Autowired lateinit var meters: MeterRegistry

    private val mapper = jacksonObjectMapper()
    private val admin = user("alice@example.com").authorities(SimpleGrantedAuthority("SCOPE_admin"))
    private val pipeline = user("pipeline").authorities(SimpleGrantedAuthority("SCOPE_attestations:write"), SimpleGrantedAuthority("SCOPE_trails:read"))

    @BeforeEach
    fun setUp() {
        clock.now = START
        archive.entries.clear()
        SecurityContextHolder.clearContext()
    }

    // --- fixtures -------------------------------------------------------------------

    private fun environmentWithSnapshots(digest: String = "a".repeat(64)): Pair<UUID, List<EnvironmentSnapshot>> {
        val env = environments.createEnvironment(CreateEnvironmentRequest(name = "ret-${System.nanoTime()}", type = EnvironmentType.K8S))
        val old = snapshots.save(EnvironmentSnapshot(environmentId = env.id, snapshotIndex = 1, recordedAt = Instant.parse("2025-01-01T00:00:00Z"), recordedBy = "ci"))
        val latest = snapshots.save(EnvironmentSnapshot(environmentId = env.id, snapshotIndex = 2, recordedAt = Instant.parse("2025-02-01T00:00:00Z"), recordedBy = "ci"))
        snapshotArtifacts.saveAll(listOf(SnapshotArtifact(snapshotId = old.id, artifactSha256 = digest, artifactName = "svc", artifactTag = "1")))
        snapshotArtifacts.saveAll(listOf(SnapshotArtifact(snapshotId = latest.id, artifactSha256 = digest, artifactName = "svc", artifactTag = "1")))
        return env.id to listOf(old, latest)
    }

    private fun trailWithArtifact(digest: String): UUID {
        val flow = flowService.createFlow(CreateFlowRequest("ret-flow-${System.nanoTime()}", "d", emptyList()))
        val trail = trailService.createTrail(CreateTrailRequest(flowId = flow.id, gitCommitSha = "sha", gitBranch = "main", gitAuthor = "a", gitAuthorEmail = "a@example.com"))
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("svc", "1", "sha256:$digest", reportedBy = "ci"))
        return trail.id
    }

    private fun setPolicy(dataClass: RetentionDataClass, days: Int) = retention.setPolicy("default", dataClass, days)

    private fun batch(report: com.factstore.dto.RetentionRunReport, dataClass: RetentionDataClass) =
        report.batches.single { it.tenant == "default" && it.dataClass == dataClass }

    // --- snapshots ------------------------------------------------------------------

    @Test
    fun `snapshots past the period are archived and removed, but an environment keeps its latest one`() {
        val (envId, snaps) = environmentWithSnapshots()
        setPolicy(RetentionDataClass.ENVIRONMENT_SNAPSHOTS, 90)

        val report = retention.run(dryRun = false)

        val b = batch(report, RetentionDataClass.ENVIRONMENT_SNAPSHOTS)
        assertThat(b.applied).isGreaterThanOrEqualTo(1)
        assertThat(b.cutoff).isEqualTo(START.minusSeconds(90L * 86400))
        assertThat(snapshots.findById(snaps[0].id)).isNull()
        assertThat(snapshots.findById(snaps[1].id)).describedAs("the latest snapshot is current state, not history").isNotNull()
        val archived = archive.entries.entries.single { it.key.contains(snaps[0].id.toString()) }
        assertThat(archived.key).startsWith("environment-snapshots/default/$envId/")
        assertThat(mapper.readTree(archived.value)["artifacts"][0]["artifactSha256"].asText()).isEqualTo("a".repeat(64))
    }

    @Test
    fun `nothing happens without a policy`() {
        val (_, snaps) = environmentWithSnapshots()

        val report = retention.run(dryRun = false)

        assertThat(report.batches.none { it.tenant == "default" && it.dataClass == RetentionDataClass.ENVIRONMENT_SNAPSHOTS }).isTrue()
        assertThat(snapshots.findById(snaps[0].id)).isNotNull()
    }

    @Test
    fun `a dry run reports what would happen and changes nothing`() {
        val (_, snaps) = environmentWithSnapshots()
        setPolicy(RetentionDataClass.ENVIRONMENT_SNAPSHOTS, 90)
        val auditBefore = audit.queryEvents(eventType = AuditEventType.RETENTION_BATCH_APPLIED, size = 500).events.size

        val report = mockMvc.post("/api/v1/admin/retention/runs?dryRun=true") { with(admin) }.andExpect {
            status { isOk() }
            jsonPath("$.dryRun") { value(true) }
        }.andReturn().response.contentAsString

        val b = mapper.readTree(report)["batches"].single { it["dataClass"].asText() == "ENVIRONMENT_SNAPSHOTS" && it["tenant"].asText() == "default" }
        assertThat(b["eligible"].asInt()).isGreaterThanOrEqualTo(1)
        assertThat(b["applied"].asInt()).isEqualTo(0)
        assertThat(snapshots.findById(snaps[0].id)).isNotNull()
        assertThat(archive.entries).isEmpty()
        assertThat(audit.queryEvents(eventType = AuditEventType.RETENTION_BATCH_APPLIED, size = 500).events).hasSize(auditBefore)
    }

    @Test
    fun `each applied batch is audited and counted`() {
        environmentWithSnapshots()
        setPolicy(RetentionDataClass.ENVIRONMENT_SNAPSHOTS, 90)
        val counter = { meters.find("factstore.retention.items").tags("data_class", "ENVIRONMENT_SNAPSHOTS", "action", "ARCHIVE").counter()?.count() ?: 0.0 }
        val before = counter()

        val applied = batch(retention.run(dryRun = false), RetentionDataClass.ENVIRONMENT_SNAPSHOTS).applied

        assertThat(counter() - before).isEqualTo(applied.toDouble())
        assertThat(audit.queryEvents(eventType = AuditEventType.RETENTION_BATCH_APPLIED, size = 500).events).anySatisfy {
            assertThat(it.payload).contains("ENVIRONMENT_SNAPSHOTS").contains("ARCHIVE").contains("\"applied\":$applied")
        }
    }

    // --- legal hold -----------------------------------------------------------------

    @Test
    fun `snapshots of an artifact under legal hold are skipped`() {
        val digest = "b".repeat(64)
        val trailId = trailWithArtifact(digest)
        val (_, snaps) = environmentWithSnapshots(digest)
        retention.placeHold(PlaceLegalHoldRequest(LegalHoldScope.TRAIL, trailId, "litigation"))
        setPolicy(RetentionDataClass.ENVIRONMENT_SNAPSHOTS, 90)

        val b = batch(retention.run(dryRun = false), RetentionDataClass.ENVIRONMENT_SNAPSHOTS)

        assertThat(snapshots.findById(snaps[0].id)).isNotNull()
        assertThat(b.skippedForLegalHold).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `a hold on a flow covers its trails, and releasing it lets retention proceed`() {
        val digest = "c".repeat(64)
        val trailId = trailWithArtifact(digest)
        val flowId = trailService.getTrail(trailId).flowId
        val (_, snaps) = environmentWithSnapshots(digest)
        val hold = retention.placeHold(PlaceLegalHoldRequest(LegalHoldScope.FLOW, flowId, "audit"))
        setPolicy(RetentionDataClass.ENVIRONMENT_SNAPSHOTS, 90)

        retention.run(dryRun = false)
        assertThat(snapshots.findById(snaps[0].id)).isNotNull()

        retention.releaseHold(hold.id)
        retention.run(dryRun = false)
        assertThat(snapshots.findById(snaps[0].id)).isNull()
    }

    // --- notifications --------------------------------------------------------------

    @Test
    fun `old notifications are pruned unless they concern something under legal hold`() {
        val heldTrail = trailWithArtifact("d".repeat(64))
        retention.placeHold(PlaceLegalHoldRequest(LegalHoldScope.TRAIL, heldTrail, "hold"))
        val old = Instant.parse("2025-01-01T00:00:00Z")
        val plain = notifications.save(Notification(title = "t", message = "m", createdAt = old))
        val held = notifications.save(Notification(title = "t", message = "m", entityType = "TRAIL", entityId = heldTrail, createdAt = old))
        val recent = notifications.save(Notification(title = "t", message = "m", createdAt = START.minusSeconds(86400)))
        setPolicy(RetentionDataClass.NOTIFICATIONS, 30)

        val b = batch(retention.run(dryRun = false), RetentionDataClass.NOTIFICATIONS)

        assertThat(notifications.findById(plain.id)).isNull()
        assertThat(notifications.findById(held.id)).isNotNull()
        assertThat(notifications.findById(recent.id)).isNotNull()
        assertThat(b.skippedForLegalHold).isGreaterThanOrEqualTo(1)
    }

    // --- fact content ---------------------------------------------------------------

    private fun ingestFact(marker: String): UUID {
        val schemaName = "ret-${System.nanoTime()}"
        schemas.publish(schemaName, 1, mapper.readTree("""{"type":"object"}"""))
        SecurityContextHolder.clearContext()
        val factId = UUID.randomUUID()
        mockMvc.post("/api/v1/commands/facts") {
            with(pipeline)
            contentType = MediaType.APPLICATION_JSON
            content = """{"fact_id":"$factId","subject":{"sha256":"$SUBJECT"},"governance":{"schema_ref":"urn:schema:$schemaName:v1"},"tool":{"note":"$marker"}}"""
        }.andExpect { status { isAccepted() } }
        return factId
    }

    @Test
    fun `fact content past the period is shredded and the ledger still verifies`() {
        val factId = ingestFact("retention-marker")
        mockMvc.post("/api/v1/admin/ledger/checkpoints") { with(admin) }
        setPolicy(RetentionDataClass.FACT_CONTENT, 365)

        clock.now = Instant.parse("2027-06-01T00:00:00Z")
        val b = batch(retention.run(dryRun = false), RetentionDataClass.FACT_CONTENT)

        assertThat(b.applied).isGreaterThanOrEqualTo(1)
        mockMvc.get("/api/v1/queries/facts/$factId") { with(pipeline) }.andExpect { status { isGone() } }
        val certificate = eventStore.findAll().last { it.eventType == "DataKeyShredded" }
        val seq = eventStore.findByAggregateId(factId).single().sequenceNumber
        mockMvc.post("/api/v1/admin/events/verify") {
            with(admin)
            contentType = MediaType.APPLICATION_JSON
            content = """{"from":$seq,"to":${certificate.sequenceNumber}}"""
        }.andExpect { jsonPath("$.valid") { value(true) } }
        mockMvc.get("/api/v1/queries/ledger/proof/$factId") { with(pipeline) }.andExpect {
            status { isOk() }
            jsonPath("$.raw_signature_verified") { value(true) }
        }
        assertThat(audit.queryEvents(eventType = AuditEventType.DATA_KEY_SHREDDED, size = 500).events).anySatisfy {
            assertThat(it.payload).contains("retention")
        }
    }

    @Test
    fun `a month still inside the period is not shredded`() {
        val factId = ingestFact("young")
        setPolicy(RetentionDataClass.FACT_CONTENT, 365)

        clock.now = Instant.parse("2027-03-15T00:00:00Z") // the month ended less than 365 days ago
        retention.run(dryRun = false)

        mockMvc.get("/api/v1/queries/facts/$factId") { with(pipeline) }.andExpect { status { isOk() } }
    }

    @Test
    fun `fact content about an artifact under legal hold is not shredded`() {
        val factId = ingestFact("held")
        val trailId = trailWithArtifact(SUBJECT)
        retention.placeHold(PlaceLegalHoldRequest(LegalHoldScope.TRAIL, trailId, "hold"))
        setPolicy(RetentionDataClass.FACT_CONTENT, 365)

        clock.now = Instant.parse("2027-06-01T00:00:00Z")
        val b = batch(retention.run(dryRun = false), RetentionDataClass.FACT_CONTENT)

        assertThat(b.skippedForLegalHold).isGreaterThanOrEqualTo(1)
        mockMvc.get("/api/v1/queries/facts/$factId") { with(pipeline) }.andExpect { status { isOk() } }
    }
}
