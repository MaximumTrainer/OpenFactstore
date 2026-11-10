package com.factstore.adapter.outbound.persistence

import com.factstore.core.port.outbound.ChainHead
import com.factstore.core.port.outbound.IEventChainHead
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository

@Entity
@Table(name = "event_chain_head")
class EventChainHeadRow(
    @Id
    val id: Int = 1,
    @Column(name = "last_sequence")
    var lastSequence: Long? = null,
    @Column(name = "last_hash", nullable = false, length = 64)
    var lastHash: String = ""
)

@Repository
interface EventChainHeadJpa : JpaRepository<EventChainHeadRow, Int> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from EventChainHeadRow h where h.id = 1")
    fun lockHead(): EventChainHeadRow
}

@Component
class EventChainHeadAdapter(private val jpa: EventChainHeadJpa) : IEventChainHead {

    /**
     * The head row is created by migration V64. A schema built another way (Hibernate DDL in
     * tests) starts without it, so it is created here, at the genesis hash, if missing.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent::class)
    @org.springframework.transaction.annotation.Transactional
    fun ensureHead() {
        if (!jpa.existsById(1)) jpa.save(EventChainHeadRow(1, null, com.factstore.application.signing.EventCanonicalForm.GENESIS_HASH))
    }

    override fun lockForAppend(): ChainHead = jpa.lockHead().let { ChainHead(it.lastSequence, it.lastHash) }

    override fun advance(sequenceNumber: Long, hash: String) {
        val head = jpa.lockHead()
        head.lastSequence = sequenceNumber
        head.lastHash = hash
        jpa.save(head)
    }

    override fun read(): ChainHead = jpa.findById(1).get().let { ChainHead(it.lastSequence, it.lastHash) }
}
