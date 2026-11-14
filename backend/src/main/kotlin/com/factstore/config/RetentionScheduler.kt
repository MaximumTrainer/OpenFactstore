package com.factstore.config

import com.factstore.core.port.inbound.IRetentionService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Runs the retention job on a schedule (#251). Off unless `factstore.retention.enabled=true`;
 * with `factstore.retention.dry-run=true` it only reports, in the log, what it would do.
 */
@Component
@ConditionalOnProperty(name = ["factstore.retention.enabled"], havingValue = "true")
class RetentionScheduler(
    private val retention: IRetentionService,
    @Value("\${factstore.retention.dry-run:false}") private val dryRun: Boolean
) {
    private val log = LoggerFactory.getLogger(RetentionScheduler::class.java)

    @Scheduled(cron = "\${factstore.retention.cron:0 30 3 * * *}", zone = "UTC")
    fun runScheduled() {
        val report = retention.run(dryRun)
        report.batches.forEach {
            log.info(
                "Retention{} {} {} for {}: eligible={} applied={} skippedForLegalHold={}",
                if (dryRun) " (dry run)" else "", it.action, it.dataClass, it.tenant, it.eligible, it.applied, it.skippedForLegalHold
            )
        }
    }
}
