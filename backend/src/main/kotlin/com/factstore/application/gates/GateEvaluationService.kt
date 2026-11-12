package com.factstore.application.gates

import com.factstore.application.observability.FailureMetrics
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.RiskTier
import com.factstore.core.port.inbound.IAssertService
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IEnvironmentRepository
import com.factstore.dto.AssertRequest
import com.factstore.dto.ComplianceStatus
import com.factstore.exception.BadRequestException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.TransientDataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.CannotCreateTransactionException
import java.util.UUID

enum class GateStatus { APPROVED, REJECTED }
enum class FallbackMode { NONE, FAIL_OPEN, FAIL_CLOSED }

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DecisionTree(
    val environment: String,
    val tier: RiskTier,
    @get:JsonProperty("flow_id") val flowId: UUID,
    @get:JsonProperty("trail_id") val trailId: UUID?,
    /** `EVALUATED` from the evidence, or `FALLBACK` by the environment's tier. */
    val outcome: String,
    val reason: String,
    @get:JsonProperty("required_policies") val requiredPolicies: List<String>,
    val missing: List<String>,
    val failed: List<String>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GateDecision(
    val status: GateStatus,
    @get:JsonProperty("fallback_mode") val fallbackMode: FallbackMode,
    /** The audit record raised for a fallback decision; absent when there was none. */
    @get:JsonProperty("audit_alert_id") val auditAlertId: UUID?,
    @get:JsonProperty("decision_tree") val decisionTree: DecisionTree,
    @get:JsonProperty("missing_attestations") val missingAttestations: List<String>
)

/**
 * Deployment gates with risk-based fallback (#193).
 *
 * A gate decides from the evidence whenever it can. When it cannot, because a dependency such as the
 * evidence database is unreachable, the environment's stored [RiskTier] decides, deterministically:
 *
 * - `STANDARD` fails **open**: the deployment is approved, and an audit alert is raised;
 * - `TIER1` fails **closed**: the deployment is rejected, and an audit alert is raised.
 *
 * If even the environment cannot be read, its tier is unknown and the gate fails closed. The
 * decision tree records the outcome and why; `factstore_gate_fallbacks_total` counts fallbacks for
 * alerting. A fact store that cannot be reached at all is handled by the client the same way,
 * from the tier it was given in advance; see `docs/gates.md`.
 */
@Service
class GateEvaluationService(
    private val environments: IEnvironmentRepository,
    private val assertService: IAssertService,
    private val attestations: IAttestationRepository,
    private val auditService: IAuditService,
    private val failureMetrics: FailureMetrics,
    private val registry: MeterRegistry,
    private val reports: com.factstore.core.port.outbound.IGateFallbackReports
) {
    private val log = LoggerFactory.getLogger(GateEvaluationService::class.java)

    fun evaluate(body: JsonNode): GateDecision {
        val request = GateRequest.parse(body)
        val (environment, tier) = try {
            val env = environments.findByName(request.targetEnvironment)
                ?: throw NotFoundException("No environment named '${request.targetEnvironment}'")
            env.name to env.tier
        } catch (e: Exception) {
            if (!isOutage(e)) throw e
            return fallback(request, request.targetEnvironment, RiskTier.TIER1, "the environment's tier could not be read (${e.javaClass.simpleName}); treated as TIER1")
        }

        val verdict = try {
            assertService.assertCompliance(AssertRequest(sha256Digest = request.artifactSha256, flowId = request.flowId))
        } catch (e: Exception) {
            if (!isOutage(e)) throw e
            return fallback(request, environment, tier, "evidence could not be evaluated: ${e.message ?: e.javaClass.simpleName}")
        }

        val policyGaps = verdict.trailId?.let { trail ->
            val passed = attestations.findByTrailId(trail).filter { it.status == AttestationStatus.PASSED }.map { it.type }.toSet()
            request.requiredPolicies.filterNot { it in passed }
        } ?: request.requiredPolicies
        val missing = (verdict.missingAttestationTypes + policyGaps).distinct()
        val failed = verdict.failedAttestationTypes
        val approved = verdict.status == ComplianceStatus.COMPLIANT && policyGaps.isEmpty()
        return GateDecision(
            status = if (approved) GateStatus.APPROVED else GateStatus.REJECTED,
            fallbackMode = FallbackMode.NONE,
            auditAlertId = null,
            decisionTree = DecisionTree(
                environment, tier, request.flowId, verdict.trailId, "EVALUATED", verdict.details,
                request.requiredPolicies, missing, failed
            ),
            missingAttestations = missing
        )
    }

    private fun fallback(request: GateRequest, environment: String, tier: RiskTier, reason: String): GateDecision {
        val mode = if (tier == RiskTier.STANDARD) FallbackMode.FAIL_OPEN else FallbackMode.FAIL_CLOSED
        countFallback(mode, "server")
        val alertId = raiseAlert(request, environment, tier, mode, reason)
        log.error("Gate fallback {} for {} to {} ({}): {} [alert {}]", mode, request.artifactSha256, environment, tier, reason, alertId)
        return GateDecision(
            status = if (mode == FallbackMode.FAIL_OPEN) GateStatus.APPROVED else GateStatus.REJECTED,
            fallbackMode = mode,
            auditAlertId = alertId,
            decisionTree = DecisionTree(
                environment, tier, request.flowId, null, "FALLBACK",
                "$reason; $tier fails ${if (mode == FallbackMode.FAIL_OPEN) "open" else "closed"}",
                request.requiredPolicies, emptyList(), emptyList()
            ),
            missingAttestations = emptyList()
        )
    }

    /** The audit record is the alert; when the database is the outage, the log line carries the id instead. */
    private fun raiseAlert(request: GateRequest, environment: String, tier: RiskTier, mode: FallbackMode, reason: String): UUID =
        try {
            auditService.record(
                AuditEventType.GATE_FALLBACK, "gate",
                mapOf(
                    "mode" to mode.name, "tier" to tier.name, "environment" to environment,
                    "artifactSha256" to request.artifactSha256, "flowId" to request.flowId.toString(), "reason" to reason
                ),
                artifactSha256 = request.artifactSha256
            ).id
        } catch (e: Exception) {
            UUID.randomUUID().also { log.error("Could not record gate fallback alert {} in the audit log: {}", it, e.message) }
        }

    /** A report is new (created) or a resend of one already recorded. */
    data class ReportResult(val auditAlertId: UUID, val created: Boolean)

    /**
     * Records a gate decision the client made by risk tier while Factstore was unreachable (#193),
     * so it is audited and alerted on like a server-side fallback. Idempotent by `report_id`.
     */
    @org.springframework.transaction.annotation.Transactional
    fun reportClientFallback(body: JsonNode): ReportResult {
        if (!body.isObject) throw BadRequestException("The request body must be a JSON object")
        val reportId = runCatching { UUID.fromString(body.path("report_id").asText()) }.getOrNull()
            ?: throw BadRequestException("report_id must be a UUID")
        reports.find(reportId)?.let { return ReportResult(it, created = false) }
        val mode = runCatching { FallbackMode.valueOf(body.path("fallback_mode").asText()) }.getOrNull()
            ?.takeIf { it != FallbackMode.NONE }
            ?: throw BadRequestException("fallback_mode must be FAIL_OPEN or FAIL_CLOSED")
        val tier = runCatching { RiskTier.valueOf(body.path("tier").asText()) }.getOrNull()
            ?: throw BadRequestException("tier must be TIER1 or STANDARD")
        val fields = listOf("artifact_sha256", "target_environment", "flow_id", "reason", "decided_at")
            .associateWith { body.path(it).asText("") }
        val alert = auditService.record(
            AuditEventType.GATE_FALLBACK, "gate-client",
            mapOf(
                "source" to "client", "reportId" to reportId.toString(), "mode" to mode.name, "tier" to tier.name,
                "environment" to fields["target_environment"], "artifactSha256" to fields["artifact_sha256"],
                "flowId" to fields["flow_id"], "reason" to fields["reason"], "decidedAt" to fields["decided_at"]
            )
        ).id
        reports.record(reportId, alert)
        countFallback(mode, "client")
        log.error(
            "Client gate fallback {} reported for {} to {} ({}): {} [alert {}]",
            mode, fields["artifact_sha256"], fields["target_environment"], tier, fields["reason"], alert
        )
        return ReportResult(alert, created = true)
    }

    private fun countFallback(mode: FallbackMode, source: String) =
        Counter.builder("factstore.gate.fallbacks")
            .description("Gate decisions made by risk tier because the evidence could not be evaluated")
            .tag("mode", mode.name)
            .tag("source", source)
            .register(registry)
            .increment()

    private fun isOutage(e: Throwable): Boolean = generateSequence(e) { it.cause }.any {
        it is TransientDataAccessException || it is DataAccessResourceFailureException || it is CannotCreateTransactionException
    }

    private data class GateRequest(
        val artifactSha256: String,
        val targetEnvironment: String,
        val flowId: UUID,
        val requiredPolicies: List<String>
    ) {
        companion object {
            private val FIELDS = setOf("artifact_sha256", "target_environment", "flow_id", "required_policies")
            private val DIGEST = Regex("^sha256:[a-fA-F0-9]{64}$")

            fun parse(body: JsonNode): GateRequest {
                if (!body.isObject) throw BadRequestException("The request body must be a JSON object")
                body.fieldNames().asSequence().firstOrNull { it !in FIELDS }?.let {
                    // In particular the tier: it is the environment's, not the caller's, to choose.
                    throw BadRequestException("Unknown field '$it'; the risk tier is a property of the environment")
                }
                val digest = body.path("artifact_sha256").asText("")
                if (!DIGEST.matches(digest)) throw BadRequestException("artifact_sha256 must be sha256:<64 hex characters>")
                val environment = body.path("target_environment").asText("").ifBlank { throw BadRequestException("target_environment is required") }
                val flowId = runCatching { UUID.fromString(body.path("flow_id").asText()) }.getOrNull()
                    ?: throw BadRequestException("flow_id must be a UUID")
                val policies = body.path("required_policies").takeIf { it.isArray }?.map { it.asText() } ?: emptyList()
                return GateRequest(digest, environment, flowId, policies)
            }
        }
    }
}
