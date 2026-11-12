package com.factstore.observability

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * #185 — the alert rules that ship with the repository.
 *
 * `promtool check rules` (run in CI) proves the PromQL parses. This proves the rules are the ones
 * the issue asks for, that each can be routed and acted on (severity, service, runbook), and that
 * every Factstore metric a rule depends on is one the application actually emits — an alert on a
 * misspelt metric name never fires and never fails.
 */
class AlertRulesTest {

    private val repoRoot = File("..").canonicalFile
    private val yaml = Yaml()

    @Suppress("UNCHECKED_CAST")
    private fun rules(): List<Map<String, Any>> {
        val doc = yaml.load<Map<String, Any>>(File(repoRoot, "monitoring/alerts/factstore.yml").readText())
        return (doc["groups"] as List<Map<String, Any>>).flatMap { it["rules"] as List<Map<String, Any>> }
    }

    /** Alerting rules only; recording rules (#192) feed them and carry no routing labels. */
    private fun alerts(): List<Map<String, Any>> = rules().filter { it["alert"] != null }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.labels() = this["labels"] as Map<String, String>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.annotations() = this["annotations"] as Map<String, String>

    @Test
    fun `prometheus loads the alert rules`() {
        val prometheus = yaml.load<Map<String, Any>>(File(repoRoot, "monitoring/prometheus.yml").readText())

        assertThat(prometheus["rule_files"] as List<*>).contains("alerts/*.yml")
    }

    @Test
    fun `the alerts the issue asks for exist with the agreed severities`() {
        val severities = alerts().associate { it["alert"] as String to it.labels()["severity"] }

        assertThat(severities).containsEntry("FactstoreHigh5xxRatio", "page")
        assertThat(severities).containsEntry("FactstoreIntegrityFailure", "page")
        assertThat(severities).containsEntry("FactstoreAuthFailureSpike", "warn")
        assertThat(severities).containsEntry("FactstoreDbPoolSaturated", "warn")
        assertThat(severities).containsEntry("FactstoreProjectionFailure", "warn")
        assertThat(severities).containsEntry("FactstoreNotificationDeadLetter", "warn")
        // #192: the availability objectives and read freshness in every region.
        assertThat(severities).containsEntry("FactstoreReadSloFastBurn", "page")
        assertThat(severities).containsEntry("FactstoreWriteSloFastBurn", "page")
        assertThat(severities).containsEntry("FactstoreReadSloSlowBurn", "warn")
        assertThat(severities).containsEntry("FactstoreWriteSloSlowBurn", "warn")
        assertThat(severities).containsEntry("FactstoreProjectionStopped", "page")
        assertThat(severities).containsEntry("FactstoreProjectionStale", "warn")
    }

    @Test
    fun `every rule carries severity, service and a runbook that exists`() {
        for (rule in alerts()) {
            val name = rule["alert"]
            assertThat(rule.labels()["severity"]).describedAs("$name severity").isIn("page", "warn")
            assertThat(rule.labels()["service"]).describedAs("$name service").isNotBlank()
            val runbook = rule.annotations()["runbook_url"]
            assertThat(runbook).describedAs("$name runbook_url").isNotBlank()
            val relative = runbook!!.substringAfter("/blob/main/")
            assertThat(File(repoRoot, relative)).describedAs("$name runbook file").exists()
        }
    }

    @Test
    fun `every factstore metric a rule uses is one the application emits`() {
        val observability = "backend/src/main/kotlin/com/factstore/application/observability"
        // Prometheus name -> where it is registered and its Micrometer name. A counter gains
        // _total; a gauge with base unit seconds gains _seconds.
        val emittedBy = mapOf(
            "factstore_errors_total" to ("$observability/FailureMetrics.kt" to "factstore.errors"),
            "factstore_integrity_failures_total" to ("$observability/FailureMetrics.kt" to "factstore.integrity.failures"),
            "factstore_event_publish_failures_total" to ("$observability/FailureMetrics.kt" to "factstore.event.publish.failures"),
            "factstore_projection_failures_total" to ("$observability/FailureMetrics.kt" to "factstore.projection.failures"),
            "factstore_notification_dead_letters_total" to ("$observability/FailureMetrics.kt" to "factstore.notification.dead.letters"),
            "factstore_auth_failures_total" to ("$observability/FailureMetrics.kt" to "factstore.auth.failures"),
            "factstore_projection_delay_seconds" to ("$observability/ProjectionLag.kt" to "factstore.projection.delay"),
            "factstore_projection_last_applied_seconds" to ("$observability/ProjectionLag.kt" to "factstore.projection.last.applied")
        )
        emittedBy.forEach { (metric, source) ->
            assertThat(File(repoRoot, source.first).readText()).describedAs(metric).contains("\"${source.second}\"")
        }
        val emitted = emittedBy.keys

        val used = rules().flatMap { Regex("factstore_[a-z_]+").findAll(it["expr"] as String).map { m -> m.value }.toList() }
        assertThat(used).isNotEmpty
        assertThat(emitted).containsAll(used)
    }

    @Test
    fun `alertmanager routes page and warn without committing a receiver secret`() {
        val text = File(repoRoot, "monitoring/alertmanager.yml").readText()
        val config = yaml.load<Map<String, Any>>(text)

        @Suppress("UNCHECKED_CAST")
        val routes = (config["route"] as Map<String, Any>)["routes"] as List<Map<String, Any>>
        val matchers = routes.flatMap { (it["matchers"] as List<*>).map(Any?::toString) }
        assertThat(matchers).contains("severity=\"page\"", "severity=\"warn\"")
        assertThat(text).doesNotContainPattern("https?://(?!localhost)[^\\s\"]+")
    }
}
