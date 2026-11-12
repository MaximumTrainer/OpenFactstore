package com.factstore.migration

import com.factstore.application.command.EventAppender
import com.factstore.application.ledger.LedgerCheckpointService
import com.factstore.core.domain.event.DomainEvent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * #186 — WORM storage, enforced by the database rather than by application code.
 *
 * Runs against real PostgreSQL with the Flyway migrations applied (`./gradlew migrationTest`).
 * Once a `domain_events` row is written it cannot be updated, deleted or truncated, by Factstore
 * or by anyone else with SQL access. The only exception is the one write that seals a row with its
 * signature (#184), and only inside the transaction that inserted it, before anyone else can see
 * the row. Ledger checkpoints (#188) are immutable too.
 */
@SpringBootTest
@ActiveProfiles("migration-test")
class AppendOnlyStorageTest {

    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var tx: TransactionTemplate
    @Autowired lateinit var appender: EventAppender
    @Autowired lateinit var checkpoints: LedgerCheckpointService

    private fun appendEvent(): Long {
        val id = UUID.randomUUID()
        tx.executeWithoutResult { appender.append(DomainEvent.FlowCreated(aggregateId = id, name = "worm-$id", description = "d")) }
        return jdbc.queryForObject("SELECT sequence_number FROM domain_events WHERE aggregate_id = ?", Long::class.java, id)!!
    }

    private fun refused(sql: String, vararg args: Any) {
        assertThatThrownBy { tx.executeWithoutResult { jdbc.update(sql, *args) } }
            .isInstanceOf(DataAccessException::class.java)
            .satisfies({ assertThat((it as DataAccessException).mostSpecificCause.message).contains("append-only") })
    }

    @Test
    fun `#186 an event appended by the application is stored and sealed`() {
        val seq = appendEvent()
        assertThat(jdbc.queryForObject("SELECT signature_status FROM domain_events WHERE sequence_number = ?", String::class.java, seq))
            .isEqualTo("SIGNED")
    }

    @Test
    fun `#186 a stored event cannot be updated`() {
        val seq = appendEvent()
        refused("UPDATE domain_events SET payload = '{}' WHERE sequence_number = ?", seq)
        refused("UPDATE domain_events SET occurred_at = occurred_at + INTERVAL '1 second' WHERE sequence_number = ?", seq)
    }

    @Test
    fun `#186 a sealed event cannot be re-signed`() {
        val seq = appendEvent()
        refused("UPDATE domain_events SET signature = 'forged', record_hash = ? WHERE sequence_number = ?", "0".repeat(64), seq)
    }

    @Test
    fun `#186 a stored event cannot be deleted`() {
        val seq = appendEvent()
        refused("DELETE FROM domain_events WHERE sequence_number = ?", seq)
    }

    @Test
    fun `#186 the event log cannot be truncated`() {
        appendEvent()
        refused("TRUNCATE domain_events CASCADE")
    }

    @Test
    fun `#186 an unsigned row cannot be sealed after the transaction that wrote it`() {
        val id = UUID.randomUUID()
        tx.executeWithoutResult {
            jdbc.update(
                "INSERT INTO domain_events (event_id, aggregate_id, aggregate_type, event_type, payload) VALUES (?, ?, 'Flow', 'FlowCreated', '{}')",
                UUID.randomUUID(), id
            )
        }
        refused("UPDATE domain_events SET signature_status = 'SIGNED', signature = 'late' WHERE aggregate_id = ?", id)
    }

    @Test
    fun `#186 a ledger checkpoint cannot be changed or removed`() {
        appendEvent()
        val index = tx.execute { checkpoints.checkpoint() }!!.checkpointIndex
        refused("UPDATE ledger_checkpoints SET root_hash = ? WHERE checkpoint_index = ?", "0".repeat(64), index)
        refused("DELETE FROM ledger_checkpoints WHERE checkpoint_index = ?", index)
    }
}
