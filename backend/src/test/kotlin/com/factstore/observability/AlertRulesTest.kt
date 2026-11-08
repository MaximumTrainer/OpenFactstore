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
        val severities = rules().associate { it["alert"] as String to it.labels()["severity"] }

        assertThat(severities).containsEntry("FactstoreHigh5xxRatio", "page")
        assertThat(severities).containsEntry("FactstoreIntegrityFailure", "page")
        assertThat(severities).containsEntry("FactstoreAuthFailureSpike", "warn")
        assertThat(severities).containsEntry("FactstoreDbPoolSaturated", "warn")
        assertThat(severities).containsEntry("FactstoreProjectionFailure", "warn")
        assertThat(severities).containsEntry("FactstoreNotificationDeadLetter", "warn")
    }

    @Test
    fun `every rule carries severity, service and a runbook that exists`() {
        for (rule in rules()) {
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
        val emitted = setOf(
            "factstore_errors_total",
            "factstore_integrity_failures_total",
            "factstore_event_publish_failures_total",
            "factstore_projection_failures_total",
            "factstore_notification_dead_letters_total",
            "factstore_auth_failures_total"
        )
        val sources = File(repoRoot, "backend/src/main/kotlin/com/factstore/application/observability/FailureMetrics.kt").readText()
        // Micrometer's dotted name becomes the Prometheus name with _total appended.
        emitted.forEach { metric ->
            val micrometerName = metric.removeSuffix("_total").replace('_', '.')
            assertThat(sources).describedAs(metric).contains("\"$micrometerName\"")
        }

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
